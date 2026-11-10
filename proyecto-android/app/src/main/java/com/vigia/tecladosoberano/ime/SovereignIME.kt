package com.vigia.tecladosoberano.ime

import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import com.vigia.tecladosoberano.R
import com.vigia.tecladosoberano.clipboard.ClipboardStore
import com.vigia.tecladosoberano.clipboard.ClipboardWatcher
import com.vigia.tecladosoberano.settings.Prefs
import com.vigia.tecladosoberano.ui.ClipboardPanel
import com.vigia.tecladosoberano.ui.EmojiPanel
import com.vigia.tecladosoberano.ui.Key
import com.vigia.tecladosoberano.ui.KeyboardLayouts
import com.vigia.tecladosoberano.ui.KeyboardTheme
import com.vigia.tecladosoberano.ui.KeyboardView
import com.vigia.tecladosoberano.vault.PuenteContrato
import com.vigia.tecladosoberano.vault.VaultBridge

/**
 * Servicio del teclado. Orquesta ciclo de vida, tema, modo contraseña, el manejador de teclas,
 * los paneles (emojis y portapapeles) y el botón 🔑 de La Bóveda.
 *
 * Nota de privacidad: el texto tecleado solo se envía al campo activo mediante InputConnection.
 * No se registra, no se persiste y no sale del proceso.
 */
class SovereignIME : InputMethodService(), KeyboardView.Listener {

    private val state = KeyboardState()
    private lateinit var prefs: Prefs
    private lateinit var actions: KeyActionHandler
    private lateinit var vault: VaultBridge
    private lateinit var clipStore: ClipboardStore
    private lateinit var clipWatcher: ClipboardWatcher

    private var rootView: View? = null
    private var keyboardView: KeyboardView? = null
    private var suggestionsContainer: View? = null
    private var clipboardButton: View? = null
    private var vaultButton: View? = null
    private var panelContainer: FrameLayout? = null
    private var emojiPanel: EmojiPanel? = null
    private var clipboardPanel: ClipboardPanel? = null

    /** Altura que ocupan las teclas: los paneles se abren con la misma para que el teclado no «salte». */
    private var lastKeyboardHeight = 0

    private var currentField = InputFieldInfo(
        isPassword = false,
        isNumeric = false,
        suggestionsAllowed = true
    )

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        actions = KeyActionHandler({ currentInputConnection }, state) { refreshKeyboard() }
        actions.onOpenEmoji = { showPanel(Panel.EMOJI) }

        vault = VaultBridge(this)
        vault.alCambiar = { updateVaultIndicator() }
        vault.alFallo = { codigo, motivo -> showVaultFailure(codigo, motivo) }

        // Portapapeles: se vigila mientras el teclado está activo y se pausa en campos de contraseña.
        clipStore = ClipboardStore(this)
        clipWatcher = ClipboardWatcher(this, clipStore, { state.isPassword }) { refreshClipboardPanel() }
        clipWatcher.start()
    }

    override fun onDestroy() {
        clipWatcher.stop()
        vault.olvidar()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_root, null)
        rootView = root
        keyboardView = root.findViewById<KeyboardView>(R.id.keyboard_view).also { it.listener = this }
        suggestionsContainer = root.findViewById(R.id.suggestions_container)

        vaultButton = root.findViewById<View>(R.id.btn_vault).also { boton ->
            // Un toque: escribe la credencial pendiente o abre La Bóveda. Pulsación larga: pide una credencial nueva.
            boton.setOnClickListener { onVaultPressed(forceNew = false) }
            boton.setOnLongClickListener {
                onVaultPressed(forceNew = true)
                true
            }
        }
        clipboardButton = root.findViewById<View>(R.id.btn_clipboard).also { boton ->
            boton.setOnClickListener {
                showPanel(if (state.panel == Panel.CLIPBOARD) Panel.NONE else Panel.CLIPBOARD)
            }
        }

        buildPanels(root.findViewById(R.id.panel_container))

        applyTheme()
        applyFieldMode()
        refreshKeyboard()
        updateVaultIndicator()
        return root
    }

    /** Evita el modo de pantalla completa en horizontal: el campo de texto sigue visible. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Las alturas cambian al girar: se cierra el panel y se recalculan al abrir el siguiente.
        lastKeyboardHeight = 0
        if (state.panel != Panel.NONE) showPanel(Panel.NONE)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentField = InputFieldInfo.from(info)
        state.isPassword = currentField.isPassword
        actions.editorInfo = info

        // restarting == true: mismo campo (p. ej. tras girar la pantalla) -> se conserva el estado.
        if (!restarting) {
            state.shift = ShiftState.OFF
            state.mode = if (currentField.isNumeric) KeyboardMode.NUMERIC else KeyboardMode.LETTERS
            showPanel(Panel.NONE)
        }

        applyTheme()
        applyFieldMode()
        actions.updateAutoCaps()
        refreshKeyboard()

        // Recoge lo copiado mientras el teclado no miraba (no en contraseñas).
        if (!currentField.isPassword) clipWatcher.capture(true)

        // Si se volvió de La Bóveda con una credencial, aquí se escribe (mismo campo) o se descarta (otra app).
        vault.alIniciarCampo(info, currentField, currentInputConnection)
        updateVaultIndicator()
    }

    override fun onFinishInput() {
        actions.editorInfo = null
        state.isPassword = false
        super.onFinishInput()
    }

    override fun onKey(key: Key) {
        actions.onKey(key)
    }

    // ------------------------------------------------------------------ paneles

    private fun buildPanels(container: FrameLayout) {
        panelContainer = container

        val emoji = EmojiPanel(this)
        emoji.onEmoji = { e -> insertEmoji(e) }
        emoji.onDelete = { actions.backspace() }
        emoji.onClose = { showPanel(Panel.NONE) }

        val clip = ClipboardPanel(this)
        clip.onInsert = { texto ->
            currentInputConnection?.commitText(texto, 1)
            showPanel(Panel.NONE)
        }
        clip.onDelete = { i ->
            clipWatcher.descartarActual()
            clipStore.remove(i)
            refreshClipboardPanel()
        }
        clip.onClear = {
            clipWatcher.descartarActual()
            clipStore.clear()
            refreshClipboardPanel()
        }
        clip.onClose = { showPanel(Panel.NONE) }

        val params = { FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT) }
        container.removeAllViews()
        container.addView(emoji, params())
        container.addView(clip, params())
        emoji.visibility = View.GONE
        clip.visibility = View.GONE
        emojiPanel = emoji
        clipboardPanel = clip
    }

    private fun showPanel(requested: Panel) {
        // En campos de contraseña no hay historial de portapapeles.
        val panel = if (requested == Panel.CLIPBOARD && state.isPassword) Panel.NONE else requested
        state.panel = panel

        val teclas = keyboardView ?: return
        val contenedor = panelContainer ?: return
        val emoji = emojiPanel ?: return
        val clip = clipboardPanel ?: return

        if (panel == Panel.NONE) {
            contenedor.visibility = View.GONE
            teclas.visibility = View.VISIBLE
            return
        }

        if (teclas.height > 0) lastKeyboardHeight = teclas.height
        if (lastKeyboardHeight <= 0) lastKeyboardHeight = (4 * 54 * resources.displayMetrics.density).toInt()
        val lp = contenedor.layoutParams
        lp.height = lastKeyboardHeight
        contenedor.layoutParams = lp

        teclas.visibility = View.GONE
        contenedor.visibility = View.VISIBLE
        if (panel == Panel.EMOJI) {
            clip.visibility = View.GONE
            emoji.visibility = View.VISIBLE
            emoji.setRecents(prefs.recentEmojis)
            emoji.open()
        } else {
            emoji.visibility = View.GONE
            clip.visibility = View.VISIBLE
            clipWatcher.capture(true)
            clip.setItems(clipStore.all())
        }
    }

    private fun insertEmoji(emoji: String) {
        currentInputConnection?.commitText(emoji, 1)
        // Los recientes no se actualizan en contraseñas.
        if (!state.isPassword) {
            prefs.addRecentEmoji(emoji)
            emojiPanel?.setRecents(prefs.recentEmojis)
        }
    }

    private fun refreshClipboardPanel() {
        if (state.panel == Panel.CLIPBOARD) clipboardPanel?.setItems(clipStore.all())
    }

    // ------------------------------------------------------------------ La Bóveda

    private fun onVaultPressed(forceNew: Boolean) {
        val info = currentInputEditorInfo ?: return
        if (!forceNew && vault.escribirPendiente(info, currentField, currentInputConnection)) return

        when (vault.iniciar(info, currentField)) {
            VaultBridge.Inicio.OK -> Unit
            VaultBridge.Inicio.NO_INSTALADA -> toast(R.string.vault_no_instalada)
            VaultBridge.Inicio.FIRMA_DISTINTA -> toast(R.string.vault_firma_distinta)
            VaultBridge.Inicio.SIN_PERMISO -> toast(R.string.vault_sin_permiso)
            VaultBridge.Inicio.BOVEDA_SIN_PUENTE -> toast(R.string.vault_sin_puente)
            VaultBridge.Inicio.EN_CURSO -> toast(R.string.vault_en_curso)
            VaultBridge.Inicio.ERROR -> toast(R.string.vault_error)
        }
    }

    private fun showVaultFailure(codigo: Int, motivo: String?) {
        val base = when (codigo) {
            VaultBridge.FALLO_NO_ABRIO -> getString(R.string.vault_no_abrio)
            PuenteContrato.COD_NO_INICIALIZADA -> getString(R.string.vault_no_inicializada)
            else -> getString(R.string.vault_error)
        }
        // El motivo (código corto, nunca datos) ayuda a diagnosticar si algo falla.
        val texto = if (motivo != null) "$base [$motivo]" else base
        aviso(texto)
    }

    /** El botón 🔑 se tiñe con el color de acento mientras hay una credencial lista para escribir. */
    private fun updateVaultIndicator() {
        val button = vaultButton ?: return
        if (vault.hayPendiente) {
            button.setBackgroundColor(ColorUtils.setAlphaComponent(KeyboardTheme.from(prefs).accent, 90))
        } else {
            button.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun toast(resId: Int) = aviso(getString(resId))

    private val quitarAviso = Runnable {
        avisoView?.visibility = View.GONE
        suggestionsContainer?.visibility = View.VISIBLE
    }
    private var avisoView: android.widget.TextView? = null

    /**
     * Aviso dentro del propio teclado (sobre la barra de sugerencias). Los toasts no sirven:
     * algunos sistemas (MIUI) los suprimen para los teclados.
     */
    private fun aviso(texto: String) {
        val barra = suggestionsContainer ?: return
        val padre = barra.parent as? android.view.ViewGroup ?: return
        val v = avisoView ?: android.widget.TextView(this).also { t ->
            t.setTextColor(Color.WHITE)
            t.textSize = 13f
            t.maxLines = 2
            t.gravity = android.view.Gravity.CENTER_VERTICAL
            t.layoutParams = barra.layoutParams
            val idx = padre.indexOfChild(barra)
            padre.addView(t, idx + 1)
            avisoView = t
        }
        v.text = texto
        v.visibility = View.VISIBLE
        barra.visibility = View.GONE
        v.removeCallbacks(quitarAviso)
        v.postDelayed(quitarAviso, 5000)
    }

    // ------------------------------------------------------------------ interfaz

    private fun applyTheme() {
        val theme = KeyboardTheme.from(prefs)
        rootView?.setBackgroundColor(theme.background)
        keyboardView?.keyboardTheme = theme
        emojiPanel?.applyTheme(theme)
        clipboardPanel?.applyTheme(theme)
    }

    /** Ajusta la interfaz según el tipo de campo (modo contraseña incluido). */
    private fun applyFieldMode() {
        val field = currentField

        // En contraseña: sin sugerencias ni portapapeles. La Bóveda sigue disponible.
        suggestionsContainer?.visibility =
            if (field.suggestionsAllowed) View.VISIBLE else View.INVISIBLE
        clipboardButton?.visibility = if (field.isPassword) View.INVISIBLE else View.VISIBLE
        vaultButton?.visibility = View.VISIBLE
        if (field.isPassword && state.panel == Panel.CLIPBOARD) showPanel(Panel.NONE)

        // En contraseña se impiden capturas de pantalla de la ventana del teclado.
        window?.window?.let { w ->
            if (field.isPassword) {
                w.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    private fun refreshKeyboard() {
        keyboardView?.apply {
            setRows(KeyboardLayouts.forMode(state.mode))
            shiftState = if (state.mode == KeyboardMode.LETTERS) state.shift else ShiftState.OFF
        }
    }
}

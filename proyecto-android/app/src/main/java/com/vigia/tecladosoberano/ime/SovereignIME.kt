package com.vigia.tecladosoberano.ime

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import com.vigia.tecladosoberano.R
import com.vigia.tecladosoberano.settings.Prefs
import com.vigia.tecladosoberano.ui.Key
import com.vigia.tecladosoberano.ui.KeyboardLayouts
import com.vigia.tecladosoberano.ui.KeyboardTheme
import com.vigia.tecladosoberano.ui.KeyboardView
import com.vigia.tecladosoberano.vault.PuenteContrato
import com.vigia.tecladosoberano.vault.VaultBridge

/**
 * Servicio del teclado. Orquesta ciclo de vida, tema, modo contraseña, el manejador de teclas
 * y el botón 🔑 de La Bóveda.
 *
 * Nota de privacidad: el texto tecleado solo se envía al campo activo mediante InputConnection.
 * No se registra, no se persiste y no sale del proceso.
 */
class SovereignIME : InputMethodService(), KeyboardView.Listener {

    private val state = KeyboardState()
    private lateinit var prefs: Prefs
    private lateinit var actions: KeyActionHandler
    private lateinit var vault: VaultBridge

    private var rootView: View? = null
    private var keyboardView: KeyboardView? = null
    private var suggestionsContainer: View? = null
    private var clipboardButton: View? = null
    private var vaultButton: View? = null

    private var currentField = InputFieldInfo(
        isPassword = false,
        isNumeric = false,
        suggestionsAllowed = true
    )

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        actions = KeyActionHandler({ currentInputConnection }, state) { refreshKeyboard() }

        vault = VaultBridge(this)
        vault.alCambiar = { updateVaultIndicator() }
        vault.alFallo = { codigo -> showVaultFailure(codigo) }
        // La credencial llega cuando La Bóveda aún tiene el foco: NO se escribe aquí. Se escribe en
        // onStartInputView, cuando el campo de la app de destino vuelve a estar activo.
    }

    override fun onDestroy() {
        vault.olvidar()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_root, null)
        rootView = root
        keyboardView = root.findViewById<KeyboardView>(R.id.keyboard_view).also { it.listener = this }
        suggestionsContainer = root.findViewById(R.id.suggestions_container)
        clipboardButton = root.findViewById(R.id.btn_clipboard)
        vaultButton = root.findViewById<View>(R.id.btn_vault).also { boton ->
            // Un toque: escribe la credencial pendiente o abre La Bóveda. Pulsación larga: pide una credencial nueva.
            boton.setOnClickListener { onVaultPressed(forceNew = false) }
            boton.setOnLongClickListener {
                onVaultPressed(forceNew = true)
                true
            }
        }

        // Fase siguiente: clipboardButton -> ClipboardPanel.
        applyTheme()
        applyFieldMode()
        refreshKeyboard()
        updateVaultIndicator()
        return root
    }

    /** Evita el modo de pantalla completa en horizontal: el campo de texto sigue visible. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentField = InputFieldInfo.from(info)
        state.isPassword = currentField.isPassword
        actions.editorInfo = info

        // restarting == true: mismo campo (p. ej. tras girar la pantalla) -> se conserva el estado.
        if (!restarting) {
            state.shift = ShiftState.OFF
            state.mode = if (currentField.isNumeric) KeyboardMode.NUMERIC else KeyboardMode.LETTERS
        }

        applyTheme()
        applyFieldMode()
        actions.updateAutoCaps()
        refreshKeyboard()

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

    // ------------------------------------------------------------------ La Bóveda

    private fun onVaultPressed(forceNew: Boolean) {
        val info = currentInputEditorInfo ?: return
        if (!forceNew && vault.escribirPendiente(info, currentField, currentInputConnection)) return

        when (vault.iniciar(info, currentField)) {
            VaultBridge.Inicio.OK -> Unit
            VaultBridge.Inicio.NO_INSTALADA -> toast(R.string.vault_no_instalada)
            VaultBridge.Inicio.FIRMA_DISTINTA -> toast(R.string.vault_firma_distinta)
            VaultBridge.Inicio.EN_CURSO -> toast(R.string.vault_en_curso)
            VaultBridge.Inicio.ERROR -> toast(R.string.vault_error)
        }
    }

    private fun showVaultFailure(codigo: Int) {
        toast(if (codigo == PuenteContrato.COD_NO_INICIALIZADA) R.string.vault_no_inicializada else R.string.vault_error)
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

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------ interfaz

    private fun applyTheme() {
        val theme = KeyboardTheme.from(prefs)
        rootView?.setBackgroundColor(theme.background)
        keyboardView?.keyboardTheme = theme
    }

    /** Ajusta la interfaz según el tipo de campo (modo contraseña incluido). */
    private fun applyFieldMode() {
        val field = currentField

        // En contraseña: sin sugerencias ni portapapeles. La Bóveda sigue disponible.
        suggestionsContainer?.visibility =
            if (field.suggestionsAllowed) View.VISIBLE else View.INVISIBLE
        clipboardButton?.visibility = if (field.isPassword) View.INVISIBLE else View.VISIBLE
        vaultButton?.visibility = View.VISIBLE

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

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
import com.vigia.tecladosoberano.suggest.Dictionary
import com.vigia.tecladosoberano.suggest.BigramStore
import com.vigia.tecladosoberano.suggest.Predictor
import com.vigia.tecladosoberano.suggest.ShortcutStore
import com.vigia.tecladosoberano.suggest.Sug
import com.vigia.tecladosoberano.suggest.WordStore
import com.vigia.tecladosoberano.ui.EmojiSearch
import com.vigia.tecladosoberano.ui.ClipboardPanel
import com.vigia.tecladosoberano.ui.EmojiPanel
import com.vigia.tecladosoberano.ui.Key
import com.vigia.tecladosoberano.ui.KeyType
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
    private var numericButton: android.widget.TextView? = null
    private val sugViews = arrayOfNulls<android.widget.TextView>(3)
    private var panelContainer: FrameLayout? = null
    private var emojiPanel: EmojiPanel? = null
    private var clipboardPanel: ClipboardPanel? = null

    // Sugerencias en español (todo local)
    private lateinit var dictionary: Dictionary
    private lateinit var wordStore: WordStore
    private lateinit var bigramStore: BigramStore
    private lateinit var shortcutStore: ShortcutStore
    private lateinit var predictor: Predictor
    private var currentPrefix = ""
    private var sugList: List<Sug> = emptyList()

    /** Palabras casi iguales a una del diccionario: solo se aprenden si se repiten (así no se aprenden erratas). */
    private val dudosas = HashMap<String, Int>()

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, clave ->
        val c = clave ?: return@OnSharedPreferenceChangeListener
        if (c.startsWith("m") || c == "accent" || c == "background" || c.startsWith("bar_") ||
            c == "learn_words" || c == "learn_common" || c == "next_word" || c == "dict_version"
        ) {
            if (c == "dict_version") recargarDiccionario()
            rootView?.post {
                applyTheme()
                applyMetrics()
                applyPredictorOptions()
                refreshKeyboard()
            }
        }
    }

    // Buscador de emojis: se escribe con las teclas del propio teclado, la consulta no sale de memoria
    private var searching = false
    private val searchQuery = StringBuilder()
    private var searchBar: android.widget.LinearLayout? = null
    private var searchQueryView: android.widget.TextView? = null
    private var searchResults: android.widget.LinearLayout? = null
    private val glyphPaint = android.graphics.Paint()

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

        dictionary = Dictionary(this)
        wordStore = WordStore(this)
        bigramStore = BigramStore(this)
        shortcutStore = ShortcutStore(this)
        predictor = Predictor(dictionary, wordStore, shortcutStore, bigramStore)
        applyPredictorOptions()
        prefs.registerListener(prefsListener)

        vault = VaultBridge(this)
        vault.alCambiar = { updateVaultIndicator() }
        vault.alFallo = { codigo, motivo -> showVaultFailure(codigo, motivo) }

        // Portapapeles: se vigila mientras el teclado está activo y se pausa en campos de contraseña.
        clipStore = ClipboardStore(this)
        clipWatcher = ClipboardWatcher(this, clipStore, { state.isPassword }) { refreshClipboardPanel() }
        clipWatcher.start()
    }

    override fun onDestroy() {
        prefs.unregisterListener(prefsListener)
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

        numericButton = root.findViewById<android.widget.TextView>(R.id.btn_numeric).also { boton ->
            boton.setOnClickListener {
                if (searching) return@setOnClickListener
                showPanel(Panel.NONE)
                state.mode = if (state.mode == KeyboardMode.NUMERIC) KeyboardMode.LETTERS else KeyboardMode.NUMERIC
                state.shift = ShiftState.OFF
                actions.updateAutoCaps()
                refreshKeyboard()
                refreshSuggestions()
            }
        }

        val ids = intArrayOf(R.id.suggestion_1, R.id.suggestion_2, R.id.suggestion_3)
        for (i in 0..2) {
            sugViews[i] = root.findViewById<android.widget.TextView>(ids[i]).also { v ->
                v.setOnClickListener { (v.tag as? Sug)?.let { acceptSuggestion(it) } }
            }
        }

        buildPanels(root.findViewById(R.id.panel_container))
        buildSearchBar(root)

        applyTheme()
        applyMetrics()
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
        applyMetrics()
        if (searching) stopSearch(false)
        if (state.panel != Panel.NONE) showPanel(Panel.NONE)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentField = InputFieldInfo.from(info)
        state.isPassword = currentField.isPassword
        actions.editorInfo = info

        // restarting == true: mismo campo (p. ej. tras girar la pantalla) -> se conserva el estado.
        if (!restarting) {
            if (searching) stopSearch(false)
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
        refreshSuggestions()
    }

    override fun onFinishInput() {
        if (searching) stopSearch(false)
        currentPrefix = ""
        sugList = emptyList()
        actions.editorInfo = null
        state.isPassword = false
        super.onFinishInput()
    }

    override fun onKey(key: Key) {
        if (searching) {
            onSearchKey(key)
            return
        }
        // Al cerrar una palabra (espacio, signo o intro) se aprende si es nueva.
        val cierra = key.type == KeyType.SPACE || key.type == KeyType.ENTER ||
            (key.type == KeyType.CHAR && key.output.isNotEmpty() && !Character.isLetter(key.output[0]))
        if (cierra) learnWordBeforeCursor()
        actions.onKey(key)
    }

    /** Cualquier cambio de texto o de cursor (teclas, pegar, mover el cursor) refresca las sugerencias. */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        refreshSuggestions()
    }

    // ------------------------------------------------------------------ sugerencias

    private fun suggestionsActive(): Boolean =
        currentField.suggestionsAllowed && !state.isPassword && !searching && state.mode != KeyboardMode.NUMERIC

    private fun trailingLetters(texto: CharSequence): String {
        var i = texto.length
        while (i > 0 && Character.isLetter(texto[i - 1])) i--
        return texto.substring(i)
    }

    private fun refreshSuggestions() {
        if (!suggestionsActive()) {
            currentPrefix = ""
            sugList = emptyList()
            keyboardView?.nextLetter = null
            showSuggestions()
            return
        }
        val ic = currentInputConnection
        val antes = ic?.getTextBeforeCursor(80, 0)
        val seleccion = ic?.getSelectedText(0)
        if (antes == null || !seleccion.isNullOrEmpty()) {
            currentPrefix = ""
            sugList = emptyList()
            keyboardView?.nextLetter = null
        } else {
            currentPrefix = trailingLetters(antes)
            val previa = palabraPrevia(antes, currentPrefix)
            sugList = predictor.suggest(currentPrefix, previa, 3)
            keyboardView?.nextLetter =
                if (state.mode == KeyboardMode.LETTERS) predictor.siguientesLetras(currentPrefix) else null
        }
        showSuggestions()
    }

    /** La palabra anterior a [actual] si van separadas por un solo espacio; si no, null. */
    private fun palabraPrevia(antes: CharSequence, actual: String): String? {
        val resto = antes.substring(0, antes.length - actual.length)
        if (resto.isEmpty() || resto.last() != ' ') return null
        val sinEspacio = resto.dropLast(1)
        if (sinEspacio.isEmpty() || !Character.isLetter(sinEspacio.last())) return null
        return trailingLetters(sinEspacio).lowercase()
    }

    /** La mejor sugerencia va en el centro; la 2.ª a la izquierda y la 3.ª a la derecha. */
    private fun showSuggestions() {
        val orden = intArrayOf(1, 0, 2)
        for (slot in 0..2) {
            val v = sugViews[orden[slot]] ?: continue
            val s = sugList.getOrNull(slot)
            v.text = s?.mostrar ?: ""
            v.tag = s
            v.isClickable = s != null
        }
    }

    private fun acceptSuggestion(s: Sug) {
        val ic = currentInputConnection ?: return
        val antes = ic.getTextBeforeCursor(80, 0)
        val previa = if (antes != null) palabraPrevia(antes, currentPrefix) else null
        ic.beginBatchEdit()
        if (currentPrefix.isNotEmpty()) ic.deleteSurroundingText(currentPrefix.length, 0)
        ic.commitText(s.insertar + " ", 1)
        ic.endBatchEdit()
        // Un atajo no se aprende como palabra.
        if (s.mostrar == s.insertar) registrar(s.insertar, previa)
        actions.updateAutoCaps()
        refreshKeyboard()
        refreshSuggestions()
    }

    private fun canLearn(): Boolean =
        prefs.learnWords && currentField.learnAllowed && !state.isPassword && !searching

    /** Carga el diccionario nuevo fuera del hilo principal y lo cambia cuando está listo. */
    private fun recargarDiccionario() {
        Thread {
            val nuevo = Dictionary(this)
            rootView?.post {
                dictionary = nuevo
                predictor = Predictor(nuevo, wordStore, shortcutStore, bigramStore)
                applyPredictorOptions()
                refreshSuggestions()
            } ?: run { dictionary = nuevo }
        }.start()
    }

    private fun applyPredictorOptions() {
        predictor.usarFrecuentes = prefs.learnCommon
        predictor.usarPares = prefs.nextWord
    }

    /** Al cerrar una palabra (espacio, signo o intro): se anota si procede. */
    private fun learnWordBeforeCursor() {
        if (!canLearn()) return
        val antes = currentInputConnection?.getTextBeforeCursor(80, 0) ?: return
        val palabra = trailingLetters(antes)
        if (palabra.length < 3) return
        registrar(palabra, palabraPrevia(antes, palabra))
    }

    /**
     * Anota [palabra] (y, si está activado, el par «previa -> palabra»).
     *  - Fuera del diccionario: se aprende; si se parece a una del diccionario (posible errata) hace falta repetirla 3 veces.
     *  - En el diccionario: solo cuenta si la persona activó «recordar palabras frecuentes».
     */
    private fun registrar(palabra: String, previa: String?) {
        if (!canLearn()) return
        val w = palabra.lowercase()
        if (w.length < 3) return
        val enDiccionario = dictionary.contiene(w)
        if (enDiccionario) {
            if (prefs.learnCommon) wordStore.learn(w)
            if (prefs.nextWord && previa != null && dictionary.contiene(previa)) bigramStore.registrar(previa, w)
        } else {
            if (predictor.pareceErrata(w)) {
                val n = (dudosas[w] ?: 0) + 1
                dudosas[w] = n
                if (n >= 3) {
                    dudosas.remove(w)
                    wordStore.learn(w)
                }
            } else {
                wordStore.learn(w)
            }
        }
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
            t.setTextColor(if (prefs.barText != 0) prefs.barText else Color.WHITE)
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

    /** Aplica las medidas editables (alturas, anchos, sensibilidad) y el modo de una mano. */
    private fun applyMetrics() {
        val kv = keyboardView ?: return
        val horizontal = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val m = prefs.metrics(horizontal)
        kv.metrics = m
        val lp = kv.layoutParams as? android.widget.LinearLayout.LayoutParams ?: return
        if (m.oneHand != 0 && !horizontal) {
            lp.width = (resources.displayMetrics.widthPixels * 0.8f).toInt()
            lp.gravity = if (m.oneHand == 1) android.view.Gravity.START else android.view.Gravity.END
        } else {
            lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            lp.gravity = android.view.Gravity.NO_GRAVITY
        }
        kv.layoutParams = lp
        lastKeyboardHeight = 0
    }

    private fun applyTheme() {
        val theme = KeyboardTheme.from(prefs)
        rootView?.setBackgroundColor(theme.background)
        // Colores propios de la barra de sugerencias y del buscador.
        val textoBarra = if (prefs.barText != 0) prefs.barText else theme.text
        val fondoBarra = if (prefs.barBg != 0) prefs.barBg else Color.TRANSPARENT
        rootView?.findViewById<View>(R.id.suggestion_bar)?.setBackgroundColor(fondoBarra)
        searchBar?.setBackgroundColor(if (prefs.barBg != 0) prefs.barBg else theme.background)
        for (v in sugViews) v?.setTextColor(textoBarra)
        numericButton?.setTextColor(textoBarra)
        avisoView?.setTextColor(textoBarra)
        searchQueryView?.setTextColor(textoBarra)
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
        refreshSuggestions()
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
        numericButton?.setBackgroundColor(
            if (state.mode == KeyboardMode.NUMERIC) ColorUtils.setAlphaComponent(KeyboardTheme.from(prefs).accent, 90)
            else Color.TRANSPARENT
        )
    }

    // ------------------------------------------------------------------ buscador de emojis

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildSearchBar(root: View) {
        val barraNormal = root.findViewById<View>(R.id.suggestion_bar)
        val padre = barraNormal.parent as android.view.ViewGroup
        val barra = android.widget.LinearLayout(this)
        barra.orientation = android.widget.LinearLayout.HORIZONTAL
        barra.gravity = android.view.Gravity.CENTER_VERTICAL
        barra.visibility = View.GONE

        val cerrar = android.widget.TextView(this)
        cerrar.text = "✕"
        cerrar.setTextColor(Color.WHITE)
        cerrar.textSize = 18f
        cerrar.gravity = android.view.Gravity.CENTER
        cerrar.setOnClickListener { stopSearch(true) }
        barra.addView(cerrar, android.widget.LinearLayout.LayoutParams(dp(48), android.widget.LinearLayout.LayoutParams.MATCH_PARENT))

        val consulta = android.widget.TextView(this)
        consulta.setTextColor(if (prefs.barText != 0) prefs.barText else Color.WHITE)
        consulta.textSize = 15f
        consulta.gravity = android.view.Gravity.CENTER_VERTICAL
        consulta.setSingleLine(true)
        consulta.ellipsize = android.text.TextUtils.TruncateAt.START
        barra.addView(consulta, android.widget.LinearLayout.LayoutParams(dp(120), android.widget.LinearLayout.LayoutParams.MATCH_PARENT))
        searchQueryView = consulta

        val scroll = android.widget.HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        val resultados = android.widget.LinearLayout(this)
        resultados.orientation = android.widget.LinearLayout.HORIZONTAL
        resultados.gravity = android.view.Gravity.CENTER_VERTICAL
        scroll.addView(resultados)
        barra.addView(scroll, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        searchResults = resultados

        padre.addView(barra, padre.indexOfChild(barraNormal) + 1, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
        searchBar = barra
    }

    private fun startSearch() {
        searching = true
        searchQuery.setLength(0)
        showPanel(Panel.NONE)
        state.mode = KeyboardMode.LETTERS
        state.shift = ShiftState.OFF
        rootView?.findViewById<View>(R.id.suggestion_bar)?.visibility = View.GONE
        searchBar?.visibility = View.VISIBLE
        refreshKeyboard()
        updateSearch()
    }

    private fun stopSearch(volverAlPanel: Boolean) {
        searching = false
        searchQuery.setLength(0)
        searchBar?.visibility = View.GONE
        rootView?.findViewById<View>(R.id.suggestion_bar)?.visibility = View.VISIBLE
        refreshKeyboard()
        if (volverAlPanel) showPanel(Panel.EMOJI)
        refreshSuggestions()
    }

    private fun onSearchKey(key: Key) {
        when (key.type) {
            KeyType.CHAR -> searchQuery.append(key.output.lowercase())
            KeyType.SPACE -> if (searchQuery.isNotEmpty() && searchQuery.last() != ' ') searchQuery.append(' ')
            KeyType.DELETE -> if (searchQuery.isNotEmpty()) searchQuery.setLength(searchQuery.length - 1)
            KeyType.ENTER, KeyType.EMOJI -> {
                stopSearch(true)
                return
            }
            KeyType.SHIFT -> Unit
            else -> {
                // Cambios de página (?123, ABC…) funcionan igual que siempre.
                actions.onKey(key)
                return
            }
        }
        updateSearch()
    }

    private fun updateSearch() {
        searchQueryView?.text = "🔍 " + searchQuery + "▏"
        val contenedor = searchResults ?: return
        contenedor.removeAllViews()
        val lista = if (searchQuery.isBlank()) prefs.recentEmojis else EmojiSearch.buscar(searchQuery.toString())
        for (e in lista) {
            if (!glyphPaint.hasGlyph(e)) continue
            val t = android.widget.TextView(this)
            t.text = e
            t.textSize = 24f
            t.gravity = android.view.Gravity.CENTER
            t.setOnClickListener { insertEmoji(e) }
            contenedor.addView(t, android.widget.LinearLayout.LayoutParams(dp(44), android.widget.LinearLayout.LayoutParams.MATCH_PARENT))
        }
        if (contenedor.childCount == 0 && searchQuery.isNotBlank()) {
            val t = android.widget.TextView(this)
            t.text = "Sin resultados"
            t.setTextColor(0xFF8B93A0.toInt())
            t.gravity = android.view.Gravity.CENTER_VERTICAL
            contenedor.addView(t)
        }
    }
}

package com.vigia.tecladosoberano.ime

import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.vigia.tecladosoberano.ui.Key
import com.vigia.tecladosoberano.ui.KeyType

/**
 * Traduce pulsaciones de teclas en operaciones sobre el campo de texto activo
 * y mantiene el estado de Shift / Caps Lock / modo de teclado.
 */
class KeyActionHandler(
    private val inputConnection: () -> InputConnection?,
    private val state: KeyboardState,
    private val onStateChanged: () -> Unit
) {

    var editorInfo: EditorInfo? = null

    fun onKey(key: Key) {
        val ic = inputConnection()
        when (key.type) {
            KeyType.CHAR -> typeText(ic, key.outputFor(effectiveShift()))
            KeyType.SPACE -> typeText(ic, " ")
            KeyType.DELETE -> delete(ic)
            KeyType.ENTER -> enter(ic)
            KeyType.SHIFT -> toggleShift()
            KeyType.TO_LETTERS -> setMode(KeyboardMode.LETTERS)
            KeyType.TO_SYMBOLS -> setMode(KeyboardMode.SYMBOLS)
            KeyType.TO_SYMBOLS2 -> setMode(KeyboardMode.SYMBOLS2)
            KeyType.TO_NUMERIC -> setMode(KeyboardMode.NUMERIC)
        }
    }

    /** Auto-mayúscula al inicio de frase. No se consulta el texto en campos de contraseña. */
    fun updateAutoCaps() {
        if (state.isPassword || state.shift == ShiftState.CAPS || state.mode != KeyboardMode.LETTERS) return
        val info = editorInfo ?: return
        val caps = inputConnection()?.getCursorCapsMode(info.inputType) ?: 0
        state.shift = if (caps != 0) ShiftState.ONCE else ShiftState.OFF
    }

    private fun effectiveShift(): ShiftState =
        if (state.mode == KeyboardMode.LETTERS) state.shift else ShiftState.OFF

    private fun typeText(ic: InputConnection?, text: String) {
        ic?.commitText(text, 1)
        if (state.shift == ShiftState.ONCE) state.shift = ShiftState.OFF
        updateAutoCaps()
        onStateChanged()
    }

    private fun delete(ic: InputConnection?) {
        if (ic == null) return
        val selected = ic.getSelectedText(0)
        if (selected.isNullOrEmpty()) {
            // Por puntos de código: borra un emoji completo de una sola vez.
            ic.deleteSurroundingTextInCodePoints(1, 0)
        } else {
            ic.commitText("", 1)
        }
        updateAutoCaps()
        onStateChanged()
    }

    private fun enter(ic: InputConnection?) {
        if (ic == null) return
        val info = editorInfo
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noEnterAction = info != null &&
            (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val hasAction = action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED

        // Sin acción propia (o campo que pide un salto de línea real): se envía la tecla Enter.
        if (hasAction && !noEnterAction) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
        updateAutoCaps()
        onStateChanged()
    }

    private fun toggleShift() {
        val now = SystemClock.uptimeMillis()
        state.shift = when (state.shift) {
            ShiftState.OFF -> ShiftState.ONCE
            // Doble toque rápido sobre Shift activa el bloqueo de mayúsculas.
            ShiftState.ONCE ->
                if (now - state.lastShiftTap < DOUBLE_TAP_MS) ShiftState.CAPS else ShiftState.OFF
            ShiftState.CAPS -> ShiftState.OFF
        }
        state.lastShiftTap = now
        onStateChanged()
    }

    private fun setMode(mode: KeyboardMode) {
        state.mode = mode
        if (mode != KeyboardMode.LETTERS) state.shift = ShiftState.OFF
        updateAutoCaps()
        onStateChanged()
    }

    private companion object {
        const val DOUBLE_TAP_MS = 350L
    }
}

package com.vigia.tecladosoberano.ime

enum class ShiftState { OFF, ONCE, CAPS }

enum class KeyboardMode { LETTERS, SYMBOLS, SYMBOLS2, NUMERIC }

/**
 * Estado del teclado. Vive en el servicio (no en la vista), por lo que sobrevive
 * a rotaciones y a la recreación de la vista de entrada.
 */
class KeyboardState {
    var shift: ShiftState = ShiftState.OFF
    var mode: KeyboardMode = KeyboardMode.LETTERS
    var isPassword: Boolean = false
    var lastShiftTap: Long = 0L
}

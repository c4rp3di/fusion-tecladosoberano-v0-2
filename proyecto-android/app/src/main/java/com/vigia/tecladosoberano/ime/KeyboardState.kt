package com.vigia.tecladosoberano.ime

enum class ShiftState { OFF, ONCE, CAPS }

enum class KeyboardMode { LETTERS, SYMBOLS, SYMBOLS2, NUMERIC }

/** Qué ocupa el sitio de las teclas: el teclado o uno de los paneles. */
enum class Panel { NONE, EMOJI, CLIPBOARD }

/**
 * Estado del teclado. Vive en el servicio (no en la vista), por lo que sobrevive
 * a rotaciones y a la recreación de la vista de entrada.
 */
class KeyboardState {
    var shift: ShiftState = ShiftState.OFF
    var mode: KeyboardMode = KeyboardMode.LETTERS
    var panel: Panel = Panel.NONE
    var isPassword: Boolean = false
    var lastShiftTap: Long = 0L
}

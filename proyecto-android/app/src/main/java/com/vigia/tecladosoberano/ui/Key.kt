package com.vigia.tecladosoberano.ui

import com.vigia.tecladosoberano.ime.ShiftState

enum class KeyType {
    CHAR, SPACE, SHIFT, DELETE, ENTER,
    TO_LETTERS, TO_SYMBOLS, TO_SYMBOLS2, TO_NUMERIC
}

/**
 * Tecla del teclado. Es una clase normal (no data class) para comparar por identidad
 * y así resaltar exactamente la tecla pulsada.
 */
class Key(
    val label: String,
    val type: KeyType = KeyType.CHAR,
    val weight: Float = 1f,
    val output: String = label
) {
    val isFunction: Boolean get() = type != KeyType.CHAR && type != KeyType.SPACE
    val repeatable: Boolean get() = type == KeyType.DELETE

    fun labelFor(shift: ShiftState): String =
        if (type == KeyType.CHAR && shift != ShiftState.OFF) label.uppercase() else label

    fun outputFor(shift: ShiftState): String =
        if (type == KeyType.CHAR && shift != ShiftState.OFF) output.uppercase() else output
}

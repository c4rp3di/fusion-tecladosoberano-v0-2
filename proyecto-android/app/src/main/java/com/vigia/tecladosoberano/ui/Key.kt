package com.vigia.tecladosoberano.ui

import com.vigia.tecladosoberano.ime.ShiftState

enum class KeyType {
    CHAR, SPACE, SHIFT, DELETE, ENTER,
    TO_LETTERS, TO_SYMBOLS, TO_SYMBOLS2, TO_NUMERIC,
    EMOJI
}

/**
 * Tecla del teclado. Es una clase normal (no data class) para comparar por identidad
 * y así resaltar exactamente la tecla pulsada.
 *
 * @param alts alternativas de la pulsación larga, de más a menos usada (la primera queda justo sobre la tecla).
 * @param hint pista pequeña en la esquina de la tecla (p. ej. el número que da la pulsación larga).
 */
class Key(
    val label: String,
    val type: KeyType = KeyType.CHAR,
    val weight: Float = 1f,
    val output: String = label,
    val alts: List<String> = emptyList(),
    val hint: String? = null
) {
    val isFunction: Boolean get() = type != KeyType.CHAR && type != KeyType.SPACE
    val repeatable: Boolean get() = type == KeyType.DELETE

    fun labelFor(shift: ShiftState): String =
        if (type == KeyType.CHAR && shift != ShiftState.OFF) label.uppercase() else label

    fun outputFor(shift: ShiftState): String =
        if (type == KeyType.CHAR && shift != ShiftState.OFF) output.uppercase() else output
}

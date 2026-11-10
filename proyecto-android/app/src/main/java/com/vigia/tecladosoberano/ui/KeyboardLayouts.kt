package com.vigia.tecladosoberano.ui

import com.vigia.tecladosoberano.ime.KeyboardMode
import com.vigia.tecladosoberano.ui.KeyType.*

/**
 * Definición de los layouts. Los pesos de cada fila se normalizan al ancho total,
 * así todas las filas ocupan el 100 % del ancho sin huecos.
 */
object KeyboardLayouts {

    private fun chars(s: String): List<Key> = s.map { Key(it.toString()) }

    private fun bottomRow(modeKey: Key): List<Key> = listOf(
        modeKey,
        Key(","),
        Key("", SPACE, 5f, " "),
        Key("."),
        Key("⏎", ENTER, 1.5f)
    )

    /** QWERTY español (con Ñ). */
    val letters: List<List<Key>> = listOf(
        chars("qwertyuiop"),
        chars("asdfghjklñ"),
        listOf(Key("⇧", SHIFT, 1.5f)) + chars("zxcvbnm") + listOf(Key("⌫", DELETE, 1.5f)),
        bottomRow(Key("?123", TO_SYMBOLS, 1.5f))
    )

    /** Símbolos, página 1: números y signos habituales (incluye ¿ ¡ del español). */
    val symbols: List<List<Key>> = listOf(
        chars("1234567890"),
        chars("@#€_&-+()/"),
        listOf(Key("=\\<", TO_SYMBOLS2, 1.5f)) + chars("¿¡\"':;!") + listOf(Key("⌫", DELETE, 1.5f)),
        bottomRow(Key("ABC", TO_LETTERS, 1.5f))
    )

    /** Símbolos, página 2. */
    val symbols2: List<List<Key>> = listOf(
        chars("~`|•√π÷×¶∆"),
        chars("£¥¢^°={}\\%"),
        listOf(Key("?123", TO_SYMBOLS, 1.5f)) + chars("©®™[]<>") + listOf(Key("⌫", DELETE, 1.5f)),
        listOf(
            Key("ABC", TO_LETTERS, 1.5f),
            Key("123", TO_NUMERIC),
            Key("", SPACE, 4f, " "),
            Key("."),
            Key("⏎", ENTER, 1.5f)
        )
    )

    /** Teclado numérico (campos de número, teléfono y fecha). */
    val numeric: List<List<Key>> = listOf(
        listOf(Key("1"), Key("2"), Key("3"), Key("⌫", DELETE)),
        listOf(Key("4"), Key("5"), Key("6"), Key(",")),
        listOf(Key("7"), Key("8"), Key("9"), Key(".")),
        listOf(Key("ABC", TO_LETTERS), Key("0"), Key("", SPACE, 1f, " "), Key("⏎", ENTER))
    )

    fun forMode(mode: KeyboardMode): List<List<Key>> = when (mode) {
        KeyboardMode.LETTERS -> letters
        KeyboardMode.SYMBOLS -> symbols
        KeyboardMode.SYMBOLS2 -> symbols2
        KeyboardMode.NUMERIC -> numeric
    }
}

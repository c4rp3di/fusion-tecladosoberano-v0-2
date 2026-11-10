package com.vigia.tecladosoberano.ui

import com.vigia.tecladosoberano.ime.KeyboardMode
import com.vigia.tecladosoberano.ui.KeyType.*

/**
 * Definición de los layouts. Los pesos de cada fila se normalizan al ancho total,
 * así todas las filas ocupan el 100 % del ancho sin huecos.
 *
 * Pulsación larga:
 *  - Letras de la fila superior (qwertyuiop): el número 1-0 (y la tilde en e, u, i, o).
 *  - Vocales y c: sus variantes con tilde, diéresis y cedilla (á à ä â ã … ç).
 *  - El punto: ? ¿ ! ¡ : ; … - '
 */
object KeyboardLayouts {

    // Importante: estas constantes van antes que los layouts (orden de inicialización del object).
    private const val DIGITS = "1234567890"

    private val tildes: Map<Char, List<String>> = mapOf(
        'a' to listOf("á", "à", "ä", "â", "ã"),
        'e' to listOf("é", "è", "ë", "ê"),
        'i' to listOf("í", "ì", "ï", "î"),
        'o' to listOf("ó", "ò", "ö", "ô", "õ"),
        'u' to listOf("ú", "ü", "ù", "û"),
        'c' to listOf("ç")
    )

    private fun chars(s: String): List<Key> = s.map { Key(it.toString()) }

    /** Fila de letras. Con [withDigits], cada tecla da su número al mantenerla pulsada y lo muestra como pista. */
    private fun letterRow(s: String, withDigits: Boolean): List<Key> = s.mapIndexed { i, ch ->
        val alts = ArrayList<String>()
        if (withDigits) alts.add(DIGITS[i].toString())
        tildes[ch]?.let { alts.addAll(it) }
        Key(ch.toString(), alts = alts, hint = if (withDigits) DIGITS[i].toString() else null)
    }

    /** El punto, con los signos de puntuación típicos del español en la pulsación larga. */
    private fun dotKey(): Key = Key(
        ".",
        alts = listOf("?", "¿", "!", "¡", ":", ";", "…", "-", "'"),
        hint = "?"
    )

    private fun bottomRow(modeKey: Key): List<Key> = listOf(
        modeKey,
        Key("😀", EMOJI),
        Key(","),
        Key("", SPACE, 4f, " "),
        dotKey(),
        Key("⏎", ENTER, 1.5f)
    )

    /** QWERTY español (con Ñ). */
    val letters: List<List<Key>> = listOf(
        letterRow("qwertyuiop", withDigits = true),
        letterRow("asdfghjklñ", withDigits = false),
        listOf(Key("⇧", SHIFT, 1.5f)) + letterRow("zxcvbnm", withDigits = false) + listOf(Key("⌫", DELETE, 1.5f)),
        bottomRow(Key("?123", TO_SYMBOLS, 1.5f))
    )

    /** Símbolos, página 1: números y signos habituales (incluye ¿ ? ¡ ! del español). */
    val symbols: List<List<Key>> = listOf(
        chars("1234567890"),
        chars("@#€_&-+()/"),
        listOf(Key("=\\<", TO_SYMBOLS2, 1f)) + chars("¿?¡!\"':;") + listOf(Key("⌫", DELETE, 1f)),
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
            Key("😀", EMOJI),
            Key("", SPACE, 3f, " "),
            dotKey(),
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

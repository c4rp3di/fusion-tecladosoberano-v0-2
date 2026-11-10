package com.vigia.tecladosoberano.ui

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.vigia.tecladosoberano.settings.Prefs

/**
 * Paleta del teclado. Todos los tonos se derivan del fondo y del acento elegidos,
 * así cualquier combinación mantiene el contraste y la estética minimalista.
 */
data class KeyboardTheme(
    val background: Int,
    val key: Int,
    val keyFunction: Int,
    val shadow: Int,
    val text: Int,
    val accent: Int,
    val onAccent: Int
) {
    companion object {
        val DEFAULT: KeyboardTheme = from(Prefs.DEFAULT_ACCENT, Prefs.DEFAULT_BACKGROUND)

        fun from(prefs: Prefs): KeyboardTheme = from(prefs.accent, prefs.background)

        fun from(accent: Int, background: Int): KeyboardTheme = KeyboardTheme(
            background = background,
            key = ColorUtils.blendARGB(background, Color.WHITE, 0.09f),
            keyFunction = ColorUtils.blendARGB(background, Color.WHITE, 0.17f),
            shadow = ColorUtils.blendARGB(background, Color.BLACK, 0.55f),
            text = 0xFFE6E8EB.toInt(),
            accent = accent,
            onAccent = 0xFF0B0E12.toInt()
        )
    }
}

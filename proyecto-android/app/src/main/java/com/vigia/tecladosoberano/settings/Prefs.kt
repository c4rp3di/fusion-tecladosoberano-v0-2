package com.vigia.tecladosoberano.settings

import android.content.Context

/**
 * Solo preferencias visuales (colores). Nunca se guarda texto tecleado aquí.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("ks_prefs", Context.MODE_PRIVATE)

    var accent: Int
        get() = sp.getInt(KEY_ACCENT, DEFAULT_ACCENT)
        set(value) = sp.edit().putInt(KEY_ACCENT, value).apply()

    var background: Int
        get() = sp.getInt(KEY_BACKGROUND, DEFAULT_BACKGROUND)
        set(value) = sp.edit().putInt(KEY_BACKGROUND, value).apply()

    companion object {
        private const val KEY_ACCENT = "accent"
        private const val KEY_BACKGROUND = "background"

        val DEFAULT_ACCENT: Int = 0xFF22D3A6.toInt()
        val DEFAULT_BACKGROUND: Int = 0xFF0E1116.toInt()
    }
}

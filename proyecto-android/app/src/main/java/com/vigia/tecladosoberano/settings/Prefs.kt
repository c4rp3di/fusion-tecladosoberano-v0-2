package com.vigia.tecladosoberano.settings

import android.content.Context

/**
 * Preferencias visuales (colores) y emojis usados recientemente.
 * Nunca se guarda aquí texto tecleado: solo emojis elegidos en el panel, y no en campos de contraseña.
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

    /** Del más reciente al más antiguo. */
    val recentEmojis: List<String>
        get() = (sp.getString(KEY_RECENT, "") ?: "").split(' ').filter { it.isNotEmpty() }

    fun addRecentEmoji(emoji: String) {
        if (emoji.isBlank() || emoji.contains(' ')) return
        val nueva = ArrayList<String>(MAX_RECENT)
        nueva.add(emoji)
        for (e in recentEmojis) if (e != emoji && nueva.size < MAX_RECENT) nueva.add(e)
        sp.edit().putString(KEY_RECENT, nueva.joinToString(" ")).apply()
    }

    companion object {
        private const val KEY_ACCENT = "accent"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_RECENT = "recent_emojis"
        private const val MAX_RECENT = 32

        val DEFAULT_ACCENT: Int = 0xFF22D3A6.toInt()
        val DEFAULT_BACKGROUND: Int = 0xFF0E1116.toInt()
    }
}

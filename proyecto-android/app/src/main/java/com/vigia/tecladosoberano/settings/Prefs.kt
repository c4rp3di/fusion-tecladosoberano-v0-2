package com.vigia.tecladosoberano.settings

import android.content.Context

/**
 * Preferencias visuales (colores) y emojis usados recientemente.
 * Nunca se guarda aquí texto tecleado: solo emojis elegidos en el panel, y no en campos de contraseña.
 */
/** Medidas y sensibilidad del teclado (se editan en la app). Un perfil para vertical y otro para horizontal. */
class KbMetrics(
    var height: Float = 1f,
    var rows: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
    var space: Float = 1f,
    var func: Float = 1f,
    var gapDp: Float = 2.5f,
    var text: Float = 1f,
    var magnet: Float = 0.6f,
    var onRelease: Boolean = false,
    var oneHand: Int = 0, // 0 normal, 1 izquierda, 2 derecha
    /** Marco: espacio vacío alrededor del teclado, en dp (izquierda, arriba, derecha, abajo). */
    var padL: Float = 0f,
    var padT: Float = 0f,
    var padR: Float = 0f,
    var padB: Float = 0f
) {
    fun copy(): KbMetrics = KbMetrics(height, rows.copyOf(), space, func, gapDp, text, magnet, onRelease, oneHand, padL, padT, padR, padB)
}

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("ks_prefs", Context.MODE_PRIVATE)

    var accent: Int
        get() = sp.getInt(KEY_ACCENT, DEFAULT_ACCENT)
        set(value) = sp.edit().putInt(KEY_ACCENT, value).apply()

    var background: Int
        get() = sp.getInt(KEY_BACKGROUND, DEFAULT_BACKGROUND)
        set(value) = sp.edit().putInt(KEY_BACKGROUND, value).apply()

    private fun mk(landscape: Boolean, name: String) = (if (landscape) "ml_" else "mp_") + name

    fun metrics(landscape: Boolean): KbMetrics {
        val m = KbMetrics()
        m.height = sp.getFloat(mk(landscape, "height"), 1f)
        for (i in 0..3) m.rows[i] = sp.getFloat(mk(landscape, "row$i"), 1f)
        m.space = sp.getFloat(mk(landscape, "space"), 1f)
        m.func = sp.getFloat(mk(landscape, "func"), 1f)
        m.gapDp = sp.getFloat(mk(landscape, "gap"), 2.5f)
        m.text = sp.getFloat(mk(landscape, "text"), 1f)
        m.magnet = sp.getFloat(mk(landscape, "magnet"), 0.6f)
        m.onRelease = sp.getBoolean(mk(landscape, "release"), false)
        m.oneHand = sp.getInt(mk(landscape, "onehand"), 0)
        m.padL = sp.getFloat(mk(landscape, "padl"), 0f)
        m.padT = sp.getFloat(mk(landscape, "padt"), 0f)
        m.padR = sp.getFloat(mk(landscape, "padr"), 0f)
        m.padB = sp.getFloat(mk(landscape, "padb"), 0f)
        return m
    }

    fun saveMetrics(landscape: Boolean, m: KbMetrics) {
        val e = sp.edit()
        e.putFloat(mk(landscape, "height"), m.height)
        for (i in 0..3) e.putFloat(mk(landscape, "row$i"), m.rows[i])
        e.putFloat(mk(landscape, "space"), m.space)
        e.putFloat(mk(landscape, "func"), m.func)
        e.putFloat(mk(landscape, "gap"), m.gapDp)
        e.putFloat(mk(landscape, "text"), m.text)
        e.putFloat(mk(landscape, "magnet"), m.magnet)
        e.putBoolean(mk(landscape, "release"), m.onRelease)
        e.putInt(mk(landscape, "onehand"), m.oneHand)
        e.putFloat(mk(landscape, "padl"), m.padL)
        e.putFloat(mk(landscape, "padt"), m.padT)
        e.putFloat(mk(landscape, "padr"), m.padR)
        e.putFloat(mk(landscape, "padb"), m.padB)
        e.apply()
    }

    fun registerListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(l)

    /** Aprender palabras nuevas (fuera del diccionario). Nunca en campos de contraseña. */
    var learnWords: Boolean
        get() = sp.getBoolean(KEY_LEARN, true)
        set(value) = sp.edit().putBoolean(KEY_LEARN, value).apply()

    /** Sube cada vez que se importa o restaura el diccionario: el teclado lo recarga. */
    fun bumpDictionary() = sp.edit().putLong("dict_version", System.currentTimeMillis()).apply()

    /** Opcional: recordar también qué palabras del diccionario usas más, para sugerirlas antes. */
    var learnCommon: Boolean
        get() = sp.getBoolean("learn_common", false)
        set(value) = sp.edit().putBoolean("learn_common", value).apply()

    /** Opcional: predecir la siguiente palabra aprendiendo pares de palabras. */
    var nextWord: Boolean
        get() = sp.getBoolean("next_word", false)
        set(value) = sp.edit().putBoolean("next_word", value).apply()

    /** Colores de la barra de sugerencias (0 = los del tema). */
    var barText: Int
        get() = sp.getInt("bar_text", 0)
        set(value) = sp.edit().putInt("bar_text", value).apply()

    var barBg: Int
        get() = sp.getInt("bar_bg", 0)
        set(value) = sp.edit().putInt("bar_bg", value).apply()

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
        private const val KEY_LEARN = "learn_words"
        private const val KEY_RECENT = "recent_emojis"
        private const val MAX_RECENT = 32

        val DEFAULT_ACCENT: Int = 0xFF22D3A6.toInt()
        val DEFAULT_BACKGROUND: Int = 0xFF0E1116.toInt()
    }
}

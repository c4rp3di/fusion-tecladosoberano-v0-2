package com.vigia.tecladosoberano.suggest

import android.content.Context

/**
 * Palabras aprendidas con su recuento. Por defecto solo palabras que NO están en el diccionario
 * (nombres, jerga). Si la persona activa «recordar mis palabras frecuentes», también cuenta las del diccionario.
 * Nunca guarda frases, contexto ni orden. Cifrado con Keystore; sin copias de seguridad.
 */
class WordStore(context: Context) : CountStore(context, "words.dat", "ks_words_key", 3000) {

    fun uso(palabra: String): Int = get(palabra.lowercase())

    /** Palabras guardadas que empiezan por [prefijoNorm] (ya normalizado), con su recuento. */
    fun conPrefijo(prefijoNorm: String): List<Pair<String, Int>> {
        val r = ArrayList<Pair<String, Int>>()
        for ((w, n) in entradas()) if (Dictionary.normalizar(w).startsWith(prefijoNorm)) r.add(w to n)
        return r
    }

    fun learn(palabra: String) {
        val w = palabra.lowercase()
        if (w.length < 3 || w.length > 24) return
        add(w)
    }

    fun forget(palabra: String) = remove(palabra.lowercase())
}

package com.vigia.tecladosoberano.suggest

import android.content.Context

/**
 * Diccionario español local (assets/es_words.txt, una palabra por línea, de más a menos frecuente).
 * Se lee del propio APK: no hay descargas ni red.
 */
class Dictionary(context: Context) {

    val words: Array<String>
    val norms: Array<String>

    init {
        val lista = ArrayList<String>(3000)
        try {
            context.applicationContext.assets.open(FILE).bufferedReader(Charsets.UTF_8).useLines { seq ->
                seq.map { it.trim() }.filter { it.isNotEmpty() }.forEach { lista.add(it) }
            }
        } catch (e: Exception) {
            // Sin diccionario: el teclado funciona igual, solo sin sugerencias de la lista base.
        }
        words = lista.toTypedArray()
        norms = Array(words.size) { normalizar(words[it]) }
    }

    /** ¿Está la palabra (sin importar mayúsculas ni tildes)? */
    fun contiene(palabra: String): Boolean {
        val n = normalizar(palabra)
        for (x in norms) if (x == n) return true
        return false
    }

    companion object {
        private const val FILE = "es_words.txt"

        /** Minúsculas y sin tildes (la ñ se conserva): «Está» -> «esta», «año» -> «año». */
        fun normalizar(s: String): String {
            val sb = StringBuilder(s.length)
            for (c in s.lowercase()) {
                sb.append(
                    when (c) {
                        'á', 'à', 'ä', 'â', 'ã' -> 'a'
                        'é', 'è', 'ë', 'ê' -> 'e'
                        'í', 'ì', 'ï', 'î' -> 'i'
                        'ó', 'ò', 'ö', 'ô', 'õ' -> 'o'
                        'ú', 'ù', 'ü', 'û' -> 'u'
                        else -> c
                    }
                )
            }
            return sb.toString()
        }
    }
}

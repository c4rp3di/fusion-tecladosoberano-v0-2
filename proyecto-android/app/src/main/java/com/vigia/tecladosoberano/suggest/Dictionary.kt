package com.vigia.tecladosoberano.suggest

import android.content.Context

/**
 * Diccionario español local (assets/es_words.txt, una palabra por línea, de más a menos frecuente).
 * Se lee del propio APK: no hay descargas ni red. Va indexado por la forma sin tildes, así que las
 * búsquedas por prefijo son binarias y valen también para listas grandes.
 */
class Dictionary(context: Context) {

    val words: Array<String>
    val norms: Array<String>

    /** Índices de palabras ordenados por su forma normalizada. */
    private val orden: IntArray
    private val ordenNorms: Array<String>
    private val conocidas = HashSet<String>()

    init {
        val lista = ArrayList<String>(3000)
        val vistas = HashSet<String>()
        fun leer(texto: java.io.BufferedReader) {
            texto.useLines { seq ->
                for (linea in seq) {
                    val w = linea.trim()
                    if (w.isNotEmpty() && vistas.add(w.lowercase())) lista.add(w)
                }
            }
        }
        // 1) La lista importada por la persona (si existe), 2) la integrada detrás (sin repetir).
        try {
            val propia = java.io.File(context.applicationContext.filesDir, USER_FILE)
            if (propia.exists()) leer(propia.bufferedReader(Charsets.UTF_8))
        } catch (e: Exception) {
            // Archivo dañado: se ignora y queda la integrada.
        }
        try {
            leer(context.applicationContext.assets.open(FILE).bufferedReader(Charsets.UTF_8))
        } catch (e: Exception) {
            // Sin diccionario integrado: el teclado funciona igual.
        }
        words = lista.toTypedArray()
        norms = Array(words.size) { normalizar(words[it]) }
        val idx = (0 until words.size).sortedWith(compareBy<Int>({ norms[it] }, { it }))
        orden = idx.toIntArray()
        ordenNorms = Array(orden.size) { norms[orden[it]] }
        for (n in norms) conocidas.add(n)
    }

    /** ¿Está la palabra (sin importar mayúsculas ni tildes)? */
    fun contiene(palabra: String): Boolean = conocidas.contains(normalizar(palabra))

    /**
     * Rango [desde, hasta) dentro del orden alfabético con todas las palabras que empiezan por [prefijoNorm].
     * Se recorre con [indiceEn].
     */
    fun rangoPrefijo(prefijoNorm: String): IntRange {
        var lo = 0
        var hi = ordenNorms.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (ordenNorms[mid] < prefijoNorm) lo = mid + 1 else hi = mid
        }
        val desde = lo
        var fin = desde
        while (fin < ordenNorms.size && ordenNorms[fin].startsWith(prefijoNorm)) fin++
        return desde until fin
    }

    /** Índice de la palabra (0 = más frecuente) en la posición [pos] del orden alfabético. */
    fun indiceEn(pos: Int): Int = orden[pos]

    companion object {
        private const val FILE = "es_words.txt"
        const val USER_FILE = "user_words.txt"

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

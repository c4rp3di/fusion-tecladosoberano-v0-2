package com.vigia.tecladosoberano.suggest

/**
 * Sugiere palabras por prefijo, sin importar tildes («esta» sugiere «está»).
 * Orden: aprendidas usadas 2+ veces, luego el diccionario por frecuencia, luego aprendidas de 1 uso.
 */
class Predictor(private val dictionary: Dictionary, private val store: WordStore) {

    fun suggest(prefijo: String, max: Int = 3): List<String> {
        if (prefijo.isEmpty()) return emptyList()
        val p = Dictionary.normalizar(prefijo)
        val salida = ArrayList<String>(max)

        val aprendidas = store.conPrefijo(p).sortedByDescending { it.second }
        for ((w, n) in aprendidas) if (n >= 2 && salida.size < max) anadir(salida, w, prefijo)

        val norms = dictionary.norms
        val words = dictionary.words
        var i = 0
        while (i < norms.size && salida.size < max) {
            if (norms[i].startsWith(p)) anadir(salida, words[i], prefijo)
            i++
        }

        for ((w, n) in aprendidas) if (n < 2 && salida.size < max) anadir(salida, w, prefijo)
        return salida
    }

    private fun anadir(lista: MutableList<String>, palabra: String, prefijo: String) {
        val w = adaptarMayusculas(prefijo, palabra)
        // No se sugiere exactamente lo ya escrito.
        if (w == prefijo) return
        if (!lista.contains(w)) lista.add(w)
    }

    private fun adaptarMayusculas(prefijo: String, palabra: String): String {
        if (prefijo.length > 1 && prefijo.all { it.isUpperCase() }) return palabra.uppercase()
        if (prefijo[0].isUpperCase()) return palabra.replaceFirstChar { it.uppercase() }
        return palabra
    }
}

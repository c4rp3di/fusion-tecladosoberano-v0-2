package com.vigia.tecladosoberano.suggest

import android.content.Context

/**
 * Pares «palabra -> palabra siguiente» para predecir la siguiente palabra. Opcional (apagado por defecto).
 * Solo guarda pares de palabras del diccionario, con tope, cifrado, y nunca en campos de contraseña, correo o URL.
 */
class BigramStore(context: Context) : CountStore(context, "pairs.dat", "ks_pairs_key", 4000) {

    fun siguientes(previa: String): List<Pair<String, Int>> {
        val pre = previa.lowercase() + "|"
        val r = ArrayList<Pair<String, Int>>()
        for ((k, n) in entradas()) if (k.startsWith(pre)) r.add(k.substring(pre.length) to n)
        return r
    }

    fun registrar(previa: String, siguiente: String) {
        add(previa.lowercase() + "|" + siguiente.lowercase())
    }
}

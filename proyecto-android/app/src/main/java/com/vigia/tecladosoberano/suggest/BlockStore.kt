package com.vigia.tecladosoberano.suggest

import android.content.Context

/**
 * Palabras que la persona ha quitado de las sugerencias (pulsación larga). Nunca se vuelven a sugerir
 * hasta que se restauren desde la app. Cifrado con Keystore.
 */
class BlockStore(context: Context) : CountStore(context, "blocked.dat", "ks_blocked_key", 2000) {

    fun bloqueada(palabra: String): Boolean = get(palabra.lowercase()) > 0

    fun bloquear(palabra: String) {
        val w = palabra.lowercase()
        if (get(w) == 0) add(w)
    }

    fun cantidad(): Int = size()
}

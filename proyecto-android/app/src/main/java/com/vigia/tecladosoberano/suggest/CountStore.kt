package com.vigia.tecladosoberano.suggest

import android.content.Context
import org.json.JSONObject

/**
 * Tabla clave -> número de usos, cifrada en disco. Base de las palabras aprendidas y de los pares de palabras.
 * Con tope: al llenarse se descartan las menos usadas.
 */
open class CountStore(context: Context, nombre: String, alias: String, private val max: Int) {

    private val archivo = SecureFile(context, nombre, alias)
    protected val counts = HashMap<String, Int>()

    init {
        val claro = archivo.leer()
        if (claro != null) {
            try {
                val json = JSONObject(String(claro, Charsets.UTF_8))
                val it = json.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    counts[k] = json.optInt(k, 1)
                }
            } catch (e: Exception) {
                counts.clear()
            }
            claro.fill(0)
        }
    }

    fun size(): Int = counts.size

    fun get(clave: String): Int = counts[clave] ?: 0

    fun add(clave: String) {
        counts[clave] = (counts[clave] ?: 0) + 1
        if (counts.size > max) {
            val sobran = counts.entries.sortedBy { it.value }.take(counts.size - max + max / 10).map { it.key }
            for (k in sobran) counts.remove(k)
        }
        guardar()
    }

    fun remove(clave: String) {
        if (counts.remove(clave) != null) guardar()
    }

    fun clear() {
        counts.clear()
        archivo.escribir(null)
    }

    protected fun entradas(): Set<Map.Entry<String, Int>> = counts.entries

    private fun guardar() {
        if (counts.isEmpty()) {
            archivo.escribir(null)
            return
        }
        val json = JSONObject()
        for ((k, v) in counts) json.put(k, v)
        val claro = json.toString().toByteArray(Charsets.UTF_8)
        archivo.escribir(claro)
        claro.fill(0)
    }
}

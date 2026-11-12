package com.vigia.tecladosoberano.suggest

import android.content.Context
import org.json.JSONObject

/**
 * Atajos definidos por la persona: «dir» -> su dirección, «mail» -> su correo…
 * Solo existe lo que ella da de alta. Cifrado con Keystore. No se usan en campos de contraseña.
 */
class ShortcutStore(context: Context) {

    private val archivo = SecureFile(context, "shortcuts.dat", "ks_shortcuts_key")
    private val mapa = LinkedHashMap<String, String>()

    init {
        val claro = archivo.leer()
        if (claro != null) {
            try {
                val json = JSONObject(String(claro, Charsets.UTF_8))
                val it = json.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    mapa[k] = json.getString(k)
                }
            } catch (e: Exception) {
                mapa.clear()
            }
            claro.fill(0)
        }
    }

    fun todos(): List<Pair<String, String>> = mapa.entries.map { it.key to it.value }

    fun get(disparador: String): String? = mapa[disparador.trim().lowercase()]

    fun poner(disparador: String, texto: String) {
        val d = disparador.trim().lowercase()
        if (d.isEmpty() || d.length > 20 || texto.isEmpty() || texto.length > 500) return
        mapa[d] = texto
        guardar()
    }

    fun quitar(disparador: String) {
        if (mapa.remove(disparador) != null) guardar()
    }

    private fun guardar() {
        if (mapa.isEmpty()) {
            archivo.escribir(null)
            return
        }
        val json = JSONObject()
        for ((k, v) in mapa) json.put(k, v)
        val claro = json.toString().toByteArray(Charsets.UTF_8)
        archivo.escribir(claro)
        claro.fill(0)
    }
}

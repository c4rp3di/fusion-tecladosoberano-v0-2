package com.vigia.tecladosoberano.suggest

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.PriorityQueue

/**
 * Importa un .txt como diccionario. Admite una palabra por línea (se supone ordenada de más a menos frecuente)
 * o «palabra frecuencia» separadas por espacio, tabulador o coma (se ordena por frecuencia).
 * Se limpia (minúsculas, solo letras, sin duplicados) y se guarda en el almacenamiento privado de la app.
 * Todo ocurre en el móvil: el archivo no sale de él.
 */
object DictionaryImporter {

    const val MAX_PALABRAS = 150_000
    private val LETRAS = Regex("[a-záéíóúüñàèìòùâêîôûäëïöç]{2,24}")

    class Resultado(val palabras: Int, val lineas: Int, val recortado: Boolean)

    /** Debe llamarse fuera del hilo principal. Lanza excepción si el archivo no se puede leer. */
    fun importar(context: Context, uri: Uri): Resultado {
        val app = context.applicationContext
        class E(val palabra: String, val peso: Long)
        val enOrden = ArrayList<String>()
        val vistas = HashSet<String>()
        // Montículo mínimo: si hay frecuencias, se conservan las MAX_PALABRAS más frecuentes.
        val monticulo = PriorityQueue<E>(1024, compareBy<E> { it.peso })
        var lineas = 0
        var conFrecuencia = false
        var recortado = false

        app.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).useLines { seq ->
            for (linea in seq) {
                lineas++
                val partes = linea.trim().split(' ', '\t', ',', ';')
                val palabra = partes[0].trim().lowercase()
                if (!LETRAS.matches(palabra)) continue
                val peso = partes.getOrNull(1)?.trim()?.toLongOrNull()
                if (peso != null) {
                    conFrecuencia = true
                    monticulo.add(E(palabra, peso))
                    if (monticulo.size > MAX_PALABRAS) {
                        monticulo.poll()
                        recortado = true
                    }
                } else if (enOrden.size >= MAX_PALABRAS) {
                    recortado = true
                } else if (vistas.add(palabra)) {
                    enOrden.add(palabra)
                }
            }
        }

        val finales: List<String> =
            if (conFrecuencia) LinkedHashSet(monticulo.sortedByDescending { it.peso }.map { it.palabra }).toList() else enOrden
        if (finales.isEmpty()) throw IllegalArgumentException("sin palabras válidas")

        val destino = File(app.filesDir, Dictionary.USER_FILE)
        val tmp = File(app.filesDir, Dictionary.USER_FILE + ".tmp")
        tmp.bufferedWriter(Charsets.UTF_8).use { w ->
            for (p in finales) {
                w.write(p)
                w.write("\n")
            }
        }
        if (!tmp.renameTo(destino)) {
            destino.delete()
            tmp.renameTo(destino)
        }
        return Resultado(finales.size, lineas, recortado)
    }

    fun restaurar(context: Context) {
        File(context.applicationContext.filesDir, Dictionary.USER_FILE).delete()
    }

    fun hayImportado(context: Context): Boolean =
        File(context.applicationContext.filesDir, Dictionary.USER_FILE).exists()
}

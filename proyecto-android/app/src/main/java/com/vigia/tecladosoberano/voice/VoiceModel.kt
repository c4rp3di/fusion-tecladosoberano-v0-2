package com.vigia.tecladosoberano.voice

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Modelo de voz de Vosk guardado en el almacenamiento privado del teclado.
 * No viene en el APK ni se descarga desde la app (no tiene permiso de internet): el usuario copia el enlace,
 * lo baja con el navegador y lo importa aquí desde el selector de archivos.
 */
object VoiceModel {

    const val URL_PEQUENO = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
    const val URL_GRANDE = "https://alphacephei.com/vosk/models/vosk-model-es-0.42.zip"

    private const val CARPETA = "voz_modelo"
    private const val MAX_BYTES = 4L * 1024 * 1024 * 1024

    fun carpeta(context: Context): File = File(context.applicationContext.filesDir, CARPETA)

    /** Un modelo de Vosk válido contiene am/final.mdl y conf/. */
    private fun esModelo(dir: File) = File(dir, "am/final.mdl").isFile && File(dir, "conf").isDirectory

    fun instalado(context: Context): Boolean = esModelo(carpeta(context))

    fun tamanoMb(context: Context): Long =
        carpeta(context).walkTopDown().filter { it.isFile }.sumOf { it.length() } / (1024 * 1024)

    fun borrar(context: Context) {
        carpeta(context).deleteRecursively()
    }

    /**
     * Descomprime el .zip elegido y deja el modelo listo. Debe llamarse fuera del hilo principal.
     * Protegido contra rutas maliciosas dentro del zip y contra zips gigantes.
     * Devuelve el tamaño en MB. Lanza excepción con un motivo legible si el archivo no vale.
     */
    fun importar(context: Context, uri: Uri, progreso: (Long) -> Unit = {}): Long {
        val app = context.applicationContext
        val tmp = File(app.filesDir, "$CARPETA.tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        val raizTmp = tmp.canonicalPath + File.separator
        var total = 0L
        try {
            val entrada = app.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("No se puede abrir el archivo")
            ZipInputStream(entrada.buffered()).use { zip ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val e = zip.nextEntry ?: break
                    val destino = File(tmp, e.name)
                    if (!destino.canonicalPath.startsWith(raizTmp)) throw IllegalArgumentException("Zip no válido")
                    if (e.isDirectory) {
                        destino.mkdirs()
                        continue
                    }
                    destino.parentFile?.mkdirs()
                    destino.outputStream().use { out ->
                        while (true) {
                            val n = zip.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            total += n
                            if (total > MAX_BYTES) throw IllegalArgumentException("El archivo es demasiado grande")
                        }
                    }
                    progreso(total / (1024 * 1024))
                }
            }
            // El zip de Vosk trae una carpeta raíz (vosk-model-small-es-0.42/...): se busca la que tiene el modelo.
            val raiz = if (esModelo(tmp)) tmp
            else tmp.walkTopDown().maxDepth(3).firstOrNull { it.isDirectory && esModelo(it) }
                ?: throw IllegalArgumentException("Ese zip no es un modelo de Vosk")
            val destino = carpeta(app)
            destino.deleteRecursively()
            if (!raiz.renameTo(destino)) throw IllegalStateException("No se pudo guardar el modelo")
            return tamanoMb(app)
        } finally {
            tmp.deleteRecursively()
        }
    }
}

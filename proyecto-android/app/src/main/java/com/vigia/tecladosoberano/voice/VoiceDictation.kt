package com.vigia.tecladosoberano.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputConnection
import com.vigia.tecladosoberano.settings.Prefs
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Dictado en vivo con Vosk, todo en el móvil. Lo que se oye aparece como texto provisional
 * (setComposingText) y se fija al terminar cada frase (commitText). Nada se guarda ni se aprende.
 */
class VoiceDictation(
    private val context: Context,
    private val ic: () -> InputConnection?,
    private val prefs: Prefs,
    private val onEstado: (Estado, String?) -> Unit
) {
    enum class Estado { INACTIVO, CARGANDO, ESCUCHANDO }

    var estado = Estado.INACTIVO
        private set

    val activo: Boolean get() = estado != Estado.INACTIVO

    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var service: SpeechService? = null

    /** Generación: invalida cargas lentas si el usuario cancela mientras el modelo se carga. */
    private var generacion = 0

    /** Texto que había antes del cursor al empezar la frase en curso (para espacios y mayúsculas). */
    private var base: String? = null

    private val liberarModelo = Runnable { liberar() }

    fun start() {
        if (estado != Estado.INACTIVO) return
        main.removeCallbacks(liberarModelo)
        val miGen = ++generacion
        cambiar(Estado.CARGANDO, null)
        Thread {
            var fallo: String? = null
            try {
                if (model == null) {
                    LibVosk.setLogLevel(LogLevel.WARNINGS)
                    model = Model(VoiceModel.carpeta(context).absolutePath)
                }
            } catch (t: Throwable) {
                model = null
                fallo = "No se pudo cargar el modelo de voz"
            }
            main.post {
                if (miGen != generacion) return@post
                if (fallo != null || model == null) {
                    cambiar(Estado.INACTIVO, fallo ?: "No se pudo cargar el modelo de voz")
                    return@post
                }
                try {
                    val m = model ?: throw IllegalStateException("sin modelo")
                    val r = Recognizer(m, 16000.0f)
                    val s = SpeechService(r, 16000.0f)
                    recognizer = r
                    service = s
                    base = null
                    if (!s.startListening(oyente)) throw IllegalStateException("micrófono ocupado")
                    cambiar(Estado.ESCUCHANDO, null)
                } catch (t: Throwable) {
                    cerrarRecursos(false)
                    cambiar(Estado.INACTIVO, "No se pudo abrir el micrófono")
                }
            }
        }.start()
    }

    /** Termina y escribe lo último que se haya oído. */
    fun stop() {
        if (estado == Estado.INACTIVO) return
        generacion++
        if (estado == Estado.ESCUCHANDO) cerrarRecursos(true)
        cambiar(Estado.INACTIVO, null)
    }

    /** Termina sin escribir más (cambio de campo, ocultar teclado). Lo ya dictado se queda. */
    fun cancelar() {
        if (estado == Estado.INACTIVO) return
        generacion++
        cerrarRecursos(false)
        ic()?.finishComposingText()
        base = null
        cambiar(Estado.INACTIVO, null)
    }

    /** Al ocultar el teclado: se corta y, pasado un rato sin usar, se libera la memoria del modelo. */
    fun alOcultar() {
        cancelar()
        main.removeCallbacks(liberarModelo)
        main.postDelayed(liberarModelo, 90_000L)
    }

    fun alMostrar() {
        main.removeCallbacks(liberarModelo)
    }

    /** Libera el modelo (p. ej. tras borrarlo desde ajustes o al cerrar el servicio). */
    fun liberar() {
        cancelar()
        main.removeCallbacks(liberarModelo)
        try { model?.close() } catch (_: Throwable) {}
        model = null
    }

    private fun cerrarRecursos(conFinal: Boolean) {
        val s = service
        service = null
        try {
            if (conFinal) s?.stop() else s?.cancel()
        } catch (_: Throwable) {}
        try { s?.shutdown() } catch (_: Throwable) {}
        try { recognizer?.close() } catch (_: Throwable) {}
        recognizer = null
    }

    private fun cambiar(nuevo: Estado, mensaje: String?) {
        estado = nuevo
        onEstado(nuevo, mensaje)
    }

    private val oyente = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            if (estado != Estado.ESCUCHANDO) return
            escribir(extraer(hypothesis, "partial"), false)
        }

        override fun onResult(hypothesis: String?) {
            escribir(extraer(hypothesis, "text"), true)
        }

        override fun onFinalResult(hypothesis: String?) {
            escribir(extraer(hypothesis, "text"), true)
        }

        override fun onError(exception: Exception?) {
            generacion++
            cerrarRecursos(false)
            cambiar(Estado.INACTIVO, "Error de voz")
        }

        override fun onTimeout() {
            stop()
        }
    }

    private fun extraer(json: String?, campo: String): String =
        try { JSONObject(json ?: "{}").optString(campo, "") } catch (_: Throwable) { "" }

    private fun escribir(oido: String, fin: Boolean) {
        val c = ic() ?: return
        if (oido.isBlank()) {
            if (fin) base = null
            return
        }
        val antes = base ?: (c.getTextBeforeCursor(3, 0)?.toString() ?: "").also { base = it }
        val texto = VoiceText.componer(oido, antes, prefs.voiceCommands, prefs.voiceCaps)
        if (texto.isEmpty()) return
        if (fin) {
            c.commitText(texto, 1)
            base = null
        } else {
            c.setComposingText(texto, 1)
        }
    }
}

package com.vigia.tecladosoberano.vault

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.vigia.tecladosoberano.ime.InputFieldInfo
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.KeyPair
import java.security.SecureRandom
import java.util.Arrays

/**
 * Lado teclado del puente con La Bóveda (botón 🔑).
 *
 * Flujo:
 *  1. [iniciar]: comprueba que La Bóveda está instalada, con el puente y firmada con la MISMA llave, genera un par
 *     efímero y lanza PuenteTecladoActivity con un Intent explícito (protegido por permiso de firma).
 *  2. La Bóveda avisa de que se abrió (COD_ABIERTA), autentica a la persona, esta elige la credencial y La Bóveda
 *     contesta, cifrada, por el ResultReceiver.
 *  3. Al llegar la respuesta la credencial queda en memoria ([hayPendiente]) unos segundos. Se escribe con
 *     commitText cuando el campo de la app de destino vuelve a tener el foco (o al pulsar 🔑 de nuevo).
 *
 * Nada pasa por el portapapeles, el disco ni los logs. Los datos solo viven en CharArray y se ponen a cero.
 */
class VaultBridge(private val context: Context) {

    enum class Inicio { OK, NO_INSTALADA, FIRMA_DISTINTA, SIN_PERMISO, BOVEDA_SIN_PUENTE, EN_CURSO, ERROR }

    private class Peticion(
        val id: ByteArray,
        val par: KeyPair,
        val publica: ByteArray,
        val destino: String,
        val campoId: Int,
        val campoNombre: String?,
        val campoEraClave: Boolean
    )

    private class Pendiente(
        val usuario: CharArray,
        val clave: CharArray,
        val destino: String,
        val campoId: Int,
        val campoNombre: String?,
        val campoEraClave: Boolean
    ) {
        var usuarioEscrito = false
    }

    private val principal = Handler(Looper.getMainLooper())
    private var peticion: Peticion? = null
    private var pendiente: Pendiente? = null

    /** La Bóveda confirmó que mostró su pantalla. */
    private var abierta = false
    private var inicioMs = 0L

    private val caducarPeticion = Runnable {
        peticion = null
        alCambiar?.invoke()
    }
    private val caducarPendiente = Runnable { olvidar() }

    /** Si pasan unos segundos sin que La Bóveda confirme que se abrió, Android ha bloqueado el lanzamiento. */
    private val vigilarApertura = Runnable {
        if (peticion != null && !abierta) alFallo?.invoke(FALLO_NO_ABRIO, null)
    }

    /** Cambió el estado (hay o no credencial pendiente): sirve para repintar el botón 🔑. */
    var alCambiar: (() -> Unit)? = null

    /**
     * Algo falló: (código COD_* de La Bóveda o [FALLO_NO_ABRIO], motivo corto para diagnóstico).
     * Una cancelación voluntaria no se notifica.
     */
    var alFallo: ((Int, String?) -> Unit)? = null

    val hayPendiente: Boolean get() = pendiente != null

    // ------------------------------------------------------------------ petición

    fun iniciar(info: EditorInfo, campo: InputFieldInfo): Inicio {
        if (peticion != null) return Inicio.EN_CURSO
        val destino = info.packageName
        if (destino.isNullOrEmpty() || destino == PuenteContrato.PAQUETE_BOVEDA) return Inicio.ERROR

        val pm = context.packageManager
        try {
            pm.getPackageInfo(PuenteContrato.PAQUETE_BOVEDA, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return Inicio.NO_INSTALADA
        }
        // Antes de enviar nada: la app que contesta debe estar firmada con nuestra misma llave.
        if (pm.checkSignatures(context.packageName, PuenteContrato.PAQUETE_BOVEDA) != PackageManager.SIGNATURE_MATCH) {
            return Inicio.FIRMA_DISTINTA
        }
        if (context.checkSelfPermission(PuenteContrato.PERMISO) != PackageManager.PERMISSION_GRANTED) {
            return Inicio.SIN_PERMISO
        }
        val componente = ComponentName(PuenteContrato.PAQUETE_BOVEDA, PuenteContrato.ACTIVIDAD_BOVEDA)
        if (Intent().setComponent(componente).resolveActivityInfo(pm, 0) == null) {
            return Inicio.BOVEDA_SIN_PUENTE
        }

        olvidarPendiente()

        val id = ByteArray(16)
        SecureRandom().nextBytes(id)
        val par = PuenteCanal.nuevoParEfimero()
        val publica = par.public.encoded
        val p = Peticion(id, par, publica, destino, info.fieldId, info.fieldName, campo.isPassword)

        val local = object : ResultReceiver(principal) {
            override fun onReceiveResult(codigo: Int, datos: Bundle?) {
                alRecibir(p, codigo, datos)
            }
        }
        // Una subclase anónima NO puede viajar en el Intent: La Bóveda intentaría cargar
        // su clase (VaultBridge$iniciar$receptor$1), no la encontraría y perdería el receptor.
        // Se envía una copia genérica de ResultReceiver que apunta al mismo Binder.
        val receptor = genericoParaEnviar(local)

        val intent = Intent()
            .setComponent(componente)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(PuenteContrato.EXTRA_VERSION, PuenteContrato.VERSION)
            .putExtra(PuenteContrato.EXTRA_ID, id)
            .putExtra(PuenteContrato.EXTRA_CLAVE_PUBLICA, publica)
            .putExtra(PuenteContrato.EXTRA_DESTINO, destino)
            .putExtra(PuenteContrato.EXTRA_RECEPTOR, receptor)

        peticion = p
        abierta = false
        try {
            context.startActivity(intent)
        } catch (e: SecurityException) {
            peticion = null
            return Inicio.SIN_PERMISO
        } catch (e: ActivityNotFoundException) {
            peticion = null
            return Inicio.BOVEDA_SIN_PUENTE
        } catch (e: Exception) {
            peticion = null
            return Inicio.ERROR
        }
        inicioMs = SystemClock.elapsedRealtime()
        principal.postDelayed(caducarPeticion, VIDA_PETICION_MS)
        principal.postDelayed(vigilarApertura, ESPERA_APERTURA_MS)
        alCambiar?.invoke()
        return Inicio.OK
    }

    private fun cancelarPeticion() {
        principal.removeCallbacks(caducarPeticion)
        principal.removeCallbacks(vigilarApertura)
        peticion = null
    }

    // ------------------------------------------------------------------ respuesta

    private fun alRecibir(p: Peticion, codigo: Int, datos: Bundle?) {
        // Solo vale la respuesta de la petición vigente.
        if (peticion !== p) return

        // Aviso de vida: La Bóveda mostró su pantalla. La petición sigue abierta.
        if (codigo == PuenteContrato.COD_ABIERTA) {
            abierta = true
            principal.removeCallbacks(vigilarApertura)
            return
        }
        cancelarPeticion()

        if (codigo != PuenteContrato.COD_OK || datos == null) {
            val motivo = datos?.getString(PuenteContrato.RES_MOTIVO)
            val rapido = SystemClock.elapsedRealtime() - inicioMs < CIERRE_RAPIDO_MS
            // Una cancelación de la persona no se avisa; un cierre instantáneo con motivo, sí (algo falló).
            if (codigo != PuenteContrato.COD_CANCELADO || (motivo != null && rapido)) {
                alFallo?.invoke(codigo, motivo)
            }
            alCambiar?.invoke()
            return
        }
        val publica = datos.getByteArray(PuenteContrato.RES_PUBLICA_BOVEDA)
        val iv = datos.getByteArray(PuenteContrato.RES_IV)
        val cifrado = datos.getByteArray(PuenteContrato.RES_CIFRADO)
        if (publica == null || iv == null || cifrado == null) {
            alFallo?.invoke(PuenteContrato.COD_ERROR, "respuesta_incompleta")
            return
        }

        var claro: ByteArray? = null
        try {
            claro = PuenteCanal.descifrar(p.par.private, p.publica, p.id, p.destino, PuenteCanal.Sobre(publica, iv, cifrado))
            val (u, c) = PuenteCanal.desempaquetar(claro)
            val usuario = aChars(u)
            val clave = aChars(c)
            u.fill(0)
            c.fill(0)
            olvidarPendiente()
            pendiente = Pendiente(usuario, clave, p.destino, p.campoId, p.campoNombre, p.campoEraClave)
            principal.postDelayed(caducarPendiente, VIDA_PENDIENTE_MS)
        } catch (e: Exception) {
            alFallo?.invoke(PuenteContrato.COD_ERROR, "descifrado_" + e.javaClass.simpleName)
            return
        } finally {
            claro?.fill(0)
        }
        // La credencial se escribe cuando el campo de la app de destino recupera el foco (alIniciarCampo).
        alCambiar?.invoke()
    }

    private fun aChars(bytes: ByteArray): CharArray {
        val cb = Charsets.UTF_8.decode(ByteBuffer.wrap(bytes))
        val salida = CharArray(cb.remaining())
        cb.get(salida)
        if (cb.hasArray()) Arrays.fill(cb.array(), '\u0000')
        return salida
    }

    // ------------------------------------------------------------------ escritura

    /**
     * Un campo de texto empezó a mostrarse. Si la credencial es para esa app y es el mismo campo (y del mismo tipo)
     * desde el que se pidió, se escribe sola. Si el usuario fue a otra app, se olvida.
     */
    fun alIniciarCampo(info: EditorInfo, campo: InputFieldInfo, ic: InputConnection?) {
        val p = pendiente ?: return
        val paquete = info.packageName
        // La propia Bóveda pasa por delante mientras la persona elige: no cuenta como «otra app».
        if (paquete == PuenteContrato.PAQUETE_BOVEDA) return
        if (paquete != p.destino) {
            olvidar()
            return
        }
        if (ic != null && info.fieldId == p.campoId && info.fieldName == p.campoNombre && campo.isPassword == p.campoEraClave) {
            escribir(ic, campo)
        }
    }

    /** Pulsación de 🔑 con una credencial pendiente: la escribe en el campo actual sin volver a La Bóveda. */
    fun escribirPendiente(info: EditorInfo, campo: InputFieldInfo, ic: InputConnection?): Boolean {
        val p = pendiente ?: return false
        if (ic == null || info.packageName != p.destino) return false
        return escribir(ic, campo)
    }

    /**
     * Campo de contraseña -> escribe la contraseña y olvida todo.
     * Otro campo de texto -> escribe el usuario y conserva la contraseña para el siguiente paso.
     * Siempre con commitText: no se toca el portapapeles.
     */
    private fun escribir(ic: InputConnection, campo: InputFieldInfo): Boolean {
        val p = pendiente ?: return false
        if (campo.isPassword) {
            if (p.clave.isEmpty()) return false
            ic.commitText(CharBuffer.wrap(p.clave), 1)
            olvidarPendiente()
        } else {
            if (p.usuarioEscrito || p.usuario.isEmpty()) return false
            ic.commitText(CharBuffer.wrap(p.usuario), 1)
            p.usuarioEscrito = true
            Arrays.fill(p.usuario, '\u0000')
            if (p.clave.isEmpty()) olvidarPendiente()
        }
        alCambiar?.invoke()
        return true
    }

    // ------------------------------------------------------------------ limpieza

    private fun olvidarPendiente() {
        principal.removeCallbacks(caducarPendiente)
        pendiente?.let {
            Arrays.fill(it.usuario, '\u0000')
            Arrays.fill(it.clave, '\u0000')
        }
        pendiente = null
    }

    /** Descarta la petición en curso y cualquier credencial retenida (cierre del servicio, cambio de app, caducidad). */
    fun olvidar() {
        cancelarPeticion()
        olvidarPendiente()
        alCambiar?.invoke()
    }

    companion object {
        /** Código propio (no viene de La Bóveda): no confirmó que se abriera a tiempo. */
        const val FALLO_NO_ABRIO = -1

        /** Plazo para autenticarse y elegir en La Bóveda. */
        private const val VIDA_PETICION_MS = 90_000L

        /** Cuánto espera la credencial en memoria a que la persona vuelva al campo. */
        private const val VIDA_PENDIENTE_MS = 90_000L

        /** Tiempo máximo para que La Bóveda confirme que se abrió (arranque en frío incluido). */
        private const val ESPERA_APERTURA_MS = 3_000L

        /** Un cierre antes de este tiempo no lo hizo la persona: se avisa con su motivo. */
        private const val CIERRE_RAPIDO_MS = 2_500L
    }

    private fun genericoParaEnviar(r: ResultReceiver): ResultReceiver {
        val parcel = android.os.Parcel.obtain()
        try {
            r.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return ResultReceiver.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}

package com.vigia.tecladosoberano.clipboard

import android.content.ClipboardManager
import android.content.Context

/**
 * Vigila el portapapeles del sistema y guarda en [ClipboardStore] lo que se copia.
 *
 * Android solo deja leer el portapapeles al teclado activo (o a la app en primer plano): por eso funciona aquí.
 * Reglas de privacidad:
 *  - En campos de contraseña ([isPaused] = true) no se lee ni se guarda nada.
 *  - Se ignora lo que el sistema marca como sensible (p. ej. una contraseña copiada desde La Bóveda).
 *  - Solo se guardan textos; nada de imágenes ni archivos.
 */
class ClipboardWatcher(
    context: Context,
    private val store: ClipboardStore,
    private val isPaused: () -> Boolean,
    private val onChanged: () -> Unit
) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(ClipboardManager::class.java)
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { capture(false) }

    /** Texto del portapapeles que la persona borró del historial: no se vuelve a añadir al reabrir el teclado. */
    private var descartado: String? = null

    fun start() {
        try {
            manager.addPrimaryClipChangedListener(listener)
        } catch (e: Exception) {
        }
    }

    fun stop() {
        try {
            manager.removePrimaryClipChangedListener(listener)
        } catch (e: Exception) {
        }
    }

    /**
     * Lee el portapapeles actual. [alAbrir] = true cuando no ha habido un aviso de cambio (se abre el teclado o el panel):
     * recoge lo copiado mientras el teclado no miraba, salvo lo que la persona ya descartó.
     */
    fun capture(alAbrir: Boolean) {
        if (isPaused()) return
        val texto = leerTexto() ?: return
        if (alAbrir && texto == descartado) return
        if (!alAbrir) descartado = null
        if (store.add(texto)) onChanged()
    }

    /** Llamar al borrar uno o todos los elementos: lo que hay ahora en el portapapeles no se vuelve a recoger solo. */
    fun descartarActual() {
        descartado = leerTexto()
    }

    private fun leerTexto(): String? {
        return try {
            val clip = manager.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            val desc = clip.description
            if (desc.extras?.getBoolean(EXTRA_SENSIBLE, false) == true) return null
            if (!desc.hasMimeType("text/*")) return null
            clip.getItemAt(0).coerceToText(appContext)?.toString()
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        /** Igual que ClipDescription.EXTRA_IS_SENSITIVE (Android 13+); como texto para no exigir esa versión. */
        const val EXTRA_SENSIBLE = "android.content.extra.IS_SENSITIVE"
    }
}

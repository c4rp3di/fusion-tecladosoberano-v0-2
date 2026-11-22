package com.vigia.tecladosoberano.settings

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.vigia.tecladosoberano.voice.VoiceModel

/**
 * Ajustes del dictado por voz. El teclado no tiene internet: aquí se copia el enlace del modelo,
 * se descarga con el navegador y se importa el .zip desde el almacenamiento del móvil.
 */
class VoiceActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var estadoModelo: TextView
    private lateinit var estadoMicro: TextView
    private lateinit var progreso: TextView
    private lateinit var btnPermiso: Button
    private lateinit var btnImportar: Button
    private lateinit var btnBorrar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = dp(20)
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        c.agregar(texto("Voz (dictado sin conexión)", 24f, true))
        c.agregar(texto(
            "Dicta y el teclado escribe al momento. Todo ocurre en tu móvil: el audio no se guarda ni sale de él, " +
                "porque el teclado no tiene permiso de internet. El motor ya va dentro; falta el modelo de idioma, que " +
                "lo traes tú.", 13f, false, dim = true), 8)

        c.agregar(titulo("1. Conseguir el modelo"))
        c.agregar(texto("Copia el enlace, pégalo en el navegador y descarga el .zip. Sin descomprimirlo.", 13f, false, dim = true), 4)
        c.agregar(boton("Copiar enlace · modelo pequeño (≈ 40 MB, recomendado)") { copiar(VoiceModel.URL_PEQUENO, it) }, 8)
        c.agregar(boton("Copiar enlace · modelo grande (≈ 1,4 GB, más preciso, mucha memoria)") { copiar(VoiceModel.URL_GRANDE, it) }, 4)

        c.agregar(titulo("2. Importarlo"))
        btnImportar = boton("Importar modelo (.zip)") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            startActivityForResult(i, REQ_MODELO)
        }
        c.agregar(btnImportar, 4)
        progreso = texto("", 13f, false, dim = true)
        c.agregar(progreso, 4)
        estadoModelo = texto("", 14f, false)
        c.agregar(estadoModelo, 8)
        btnBorrar = boton("Borrar el modelo") {
            AlertDialog.Builder(this)
                .setMessage("¿Borrar el modelo de voz? Desaparecerá el icono 🎤 hasta que importes otro.")
                .setPositiveButton("Borrar") { _, _ ->
                    VoiceModel.borrar(this)
                    prefs.avisarCambioVoz()
                    refrescar()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        c.agregar(btnBorrar, 4)

        c.agregar(titulo("3. Micrófono"))
        estadoMicro = texto("", 14f, false)
        c.agregar(estadoMicro, 4)
        btnPermiso = boton("Conceder permiso del micrófono") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_PERMISO)
        }
        c.agregar(btnPermiso, 4)

        c.agregar(titulo("Opciones"))
        c.agregar(interruptor("Entender «coma», «punto», «nueva línea»…", prefs.voiceCommands) { prefs.voiceCommands = it }, 4)
        c.agregar(interruptor("Mayúscula al empezar frase", prefs.voiceCaps) { prefs.voiceCaps = it }, 4)
        c.agregar(texto(
            "Comandos: coma · punto · punto y coma · dos puntos · punto y aparte · nueva línea · nuevo párrafo · " +
                "interrogación · exclamación · abrir/cerrar interrogación · abrir/cerrar exclamación · " +
                "abrir/cerrar paréntesis · puntos suspensivos.\n" +
                "Lo dictado no se aprende, y en campos de contraseña el micrófono no se activa. " +
                "El icono 🎤 se coloca desde el editor del teclado (barra superior).", 12f, false, dim = true), 8)

        c.agregar(titulo("Prueba"))
        c.agregar(EditText(this).apply {
            hint = "Pulsa 🎤 en tu teclado y habla"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF8B93A0.toInt())
        })

        val sv = ScrollView(this)
        sv.setBackgroundColor(getColor(com.vigia.tecladosoberano.R.color.ks_background))
        sv.addView(c)
        setContentView(sv)

        refrescar()
        if (intent.getBooleanExtra(EXTRA_PEDIR_PERMISO, false) && !tienePermiso()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_PERMISO)
        }
    }

    private fun tienePermiso() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun refrescar() {
        val hay = VoiceModel.instalado(this)
        estadoModelo.text = if (hay) "✔ Modelo instalado (${VoiceModel.tamanoMb(this)} MB)" else "Todavía no hay modelo."
        btnBorrar.isEnabled = hay
        estadoMicro.text = if (tienePermiso()) "✔ Permiso concedido" else "Sin permiso todavía."
        btnPermiso.isEnabled = !tienePermiso()
    }

    private fun copiar(url: String, b: Button) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Modelo de voz", url))
        val antes = b.text
        b.text = "✔ Enlace copiado: pégalo en el navegador"
        b.postDelayed({ b.text = antes }, 2500)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_MODELO || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        btnImportar.isEnabled = false
        progreso.text = "Importando… no cierres esta pantalla."
        Thread {
            val resultado = try {
                VoiceModel.importar(this, uri) { mb ->
                    runOnUiThread { if (!isFinishing) progreso.text = "Importando… $mb MB" }
                }
                null
            } catch (t: Throwable) {
                t.message ?: "No se pudo importar"
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                btnImportar.isEnabled = true
                progreso.text = if (resultado == null) "✔ Listo. Ya puedes dictar con 🎤." else "✖ $resultado"
                if (resultado == null) prefs.avisarCambioVoz()
                refrescar()
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refrescar()
    }

    // ------------------------------------------------------------------ ayudas de interfaz

    private fun texto(t: String, size: Float, bold: Boolean, dim: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(if (dim) 0xFF8B93A0.toInt() else 0xFFE6E8EB.toInt())
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun titulo(t: String) = texto(t, 16f, true).also {
        it.layoutParams = margen(26)
    }

    private fun boton(t: String, onClick: (Button) -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { onClick(this) }
    }

    private fun interruptor(t: String, valor: Boolean, cambio: (Boolean) -> Unit) = Switch(this).apply {
        text = t
        setTextColor(0xFFE6E8EB.toInt())
        isChecked = valor
        setOnCheckedChangeListener { _, on -> cambio(on) }
    }

    /** Añade una vista con margen superior (no se llama addView para no chocar con el método del sistema). */
    private fun LinearLayout.agregar(v: android.view.View, arriba: Int = 0) {
        if (arriba > 0) {
            v.layoutParams = margen(arriba)
        } else if (v.layoutParams == null) {
            v.layoutParams = margen(0)
        }
        addView(v)
    }

    private fun margen(arriba: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(arriba) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PEDIR_PERMISO = "pedir_permiso"
        private const val REQ_MODELO = 51
        private const val REQ_PERMISO = 52
    }
}

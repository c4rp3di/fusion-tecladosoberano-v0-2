package com.vigia.tecladosoberano.settings

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.vigia.tecladosoberano.R
import com.vigia.tecladosoberano.ui.KeyboardLayouts
import com.vigia.tecladosoberano.ui.KeyboardTheme
import com.vigia.tecladosoberano.ui.KeyboardView
import java.util.Locale

/**
 * Editor del teclado: vista previa en vivo arriba y deslizadores debajo.
 * Los cambios se guardan al momento y el teclado real los aplica sin reiniciar.
 * Hay un perfil para vertical y otro para horizontal.
 */
class KeyboardEditorActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var preview: KeyboardView
    private var landscape = false
    private lateinit var m: KbMetrics
    private val panel by lazy { LinearLayout(this).apply { orientation = LinearLayout.VERTICAL } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        landscape = false
        m = prefs.metrics(landscape)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.ks_background))
        }

        preview = KeyboardView(this).apply {
            keyboardTheme = KeyboardTheme.from(prefs)
            setRows(KeyboardLayouts.letters)
            metrics = m
            forceLandscape = landscape
        }
        raiz.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(this)
        scroll.addView(panel)
        raiz.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(raiz)
        construirControles()
    }

    private fun construirControles() {
        panel.removeAllViews()
        val pad = dp(16)
        panel.setPadding(pad, dp(10), pad, pad)

        panel.addView(texto("Editor del teclado", 20f, true))
        panel.addView(texto("Lo que cambies se ve arriba y se aplica al teclado real al instante.", 13f, false, dim = true))

        // Perfil
        val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fila.addView(boton(if (landscape) "Vertical" else "▶ Vertical") { cambiarPerfil(false) }, peso())
        fila.addView(boton(if (landscape) "▶ Horizontal" else "Horizontal") { cambiarPerfil(true) }, peso())
        panel.addView(fila, margen(10))

        panel.addView(titulo("Tamaño"))
        panel.addView(slider("Altura general", 0.7f, 1.5f, m.height, { "%.0f %%".format(Locale.US, it * 100) }) { m.height = it })
        for (i in 0..3) {
            val nombre = listOf("Fila 1 (q…p)", "Fila 2 (a…ñ)", "Fila 3 (z…m)", "Fila 4 (espacio)")[i]
            panel.addView(slider("Altura de $nombre", 0.6f, 1.6f, m.rows[i], { "%.0f %%".format(Locale.US, it * 100) }) { m.rows[i] = it })
        }

        panel.addView(titulo("Teclas especiales"))
        panel.addView(slider("Ancho de la barra espaciadora", 0.6f, 2.2f, m.space, { "%.0f %%".format(Locale.US, it * 100) }) { m.space = it })
        panel.addView(slider("Ancho de Shift, borrar e Intro", 0.7f, 1.8f, m.func, { "%.0f %%".format(Locale.US, it * 100) }) { m.func = it })

        panel.addView(titulo("Aspecto"))
        panel.addView(slider("Separación entre teclas", 0f, 6f, m.gapDp, { "%.1f dp".format(Locale.US, it) }) { m.gapDp = it })
        panel.addView(slider("Tamaño de las letras", 0.8f, 1.4f, m.text, { "%.0f %%".format(Locale.US, it * 100) }) { m.text = it })

        panel.addView(titulo("Precisión al escribir"))
        panel.addView(texto("El «imán» ayuda a acertar entre letras vecinas: favorece la letra más probable según lo que ya has escrito. 0 % = solo cuenta dónde tocas.", 12f, false, dim = true))
        panel.addView(slider("Imán hacia la letra probable", 0f, 1f, m.magnet, { "%.0f %%".format(Locale.US, it * 100) }) { m.magnet = it })
        val sw = Switch(this).apply {
            text = "Decidir la tecla al soltar el dedo (en vez de al tocar)"
            setTextColor(0xFFE6E8EB.toInt())
            isChecked = m.onRelease
            setOnCheckedChangeListener { _, on -> m.onRelease = on; guardar() }
        }
        panel.addView(sw, margen(8))

        panel.addView(titulo("Modo una mano (solo vertical)"))
        val manos = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val nombres = listOf("Normal", "Izquierda", "Derecha")
        nombres.forEachIndexed { i, n ->
            manos.addView(boton(if (m.oneHand == i) "▶ $n" else n) {
                m.oneHand = i
                guardar()
                construirControles()
            }, peso())
        }
        panel.addView(manos, margen(4))

        panel.addView(boton("Restablecer este perfil") {
            m = KbMetrics()
            guardar()
            construirControles()
        }, margen(20))

        panel.addView(titulo("Prueba"))
        panel.addView(EditText(this).apply {
            hint = "Escribe aquí con tu teclado real"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF8B93A0.toInt())
        })
    }

    private fun cambiarPerfil(horizontal: Boolean) {
        if (horizontal == landscape) return
        landscape = horizontal
        m = prefs.metrics(landscape)
        preview.forceLandscape = landscape
        preview.metrics = m
        construirControles()
    }

    private fun guardar() {
        prefs.saveMetrics(landscape, m)
        preview.metrics = m
    }

    private fun slider(
        nombre: String, min: Float, max: Float, valor: Float,
        formato: (Float) -> String, alCambiar: (Float) -> Unit
    ): LinearLayout {
        val caja = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val etiqueta = texto("$nombre: ${formato(valor)}", 14f, false)
        caja.addView(etiqueta)
        val barra = SeekBar(this)
        barra.max = 100
        barra.progress = (((valor - min) / (max - min)) * 100f).toInt().coerceIn(0, 100)
        barra.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, deUsuario: Boolean) {
                val v = min + (max - min) * p / 100f
                etiqueta.text = "$nombre: ${formato(v)}"
                if (deUsuario) {
                    alCambiar(v)
                    guardar()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        caja.addView(barra)
        caja.layoutParams = margen(6)
        return caja
    }

    private fun titulo(t: String) = texto(t, 16f, true).also { it.layoutParams = margen(22) }

    private fun texto(t: String, size: Float, bold: Boolean, dim: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(if (dim) 0xFF8B93A0.toInt() else 0xFFE6E8EB.toInt())
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun boton(t: String, alPulsar: () -> Unit) = Button(this).apply {
        text = t
        setOnClickListener { alPulsar() }
    }

    private fun peso() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun margen(arriba: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(arriba) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

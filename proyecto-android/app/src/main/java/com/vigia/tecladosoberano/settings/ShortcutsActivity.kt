package com.vigia.tecladosoberano.settings

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.vigia.tecladosoberano.R
import com.vigia.tecladosoberano.suggest.ShortcutStore

/**
 * Atajos de texto: escribes el disparador (p. ej. «dir») y el teclado lo ofrece en la barra de sugerencias
 * con el texto completo. Se guardan cifrados en el móvil. No se usan en campos de contraseña.
 */
class ShortcutsActivity : Activity() {

    private lateinit var store: ShortcutStore
    private val panel by lazy { LinearLayout(this).apply { orientation = LinearLayout.VERTICAL } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ShortcutStore(this)
        val pad = dp(20)
        panel.setPadding(pad, pad, pad, pad)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.ks_background))
            addView(panel)
        })
        pintar()
    }

    private fun pintar() {
        panel.removeAllViews()
        panel.addView(texto("Atajos", 24f, true))
        panel.addView(texto("Escribe el disparador y el teclado te ofrecerá el texto completo en la barra de sugerencias. Se guardan cifrados en este móvil y no se usan en campos de contraseña.", 13f, false, true))

        val disparador = EditText(this).apply {
            hint = "Disparador (p. ej. dir)"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF8B93A0.toInt())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val contenido = EditText(this).apply {
            hint = "Texto completo"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF8B93A0.toInt())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        panel.addView(disparador, margen(16))
        panel.addView(contenido, margen(4))
        panel.addView(Button(this).apply {
            text = "Añadir atajo"
            setOnClickListener {
                val d = disparador.text.toString().trim()
                val t = contenido.text.toString()
                if (d.isNotEmpty() && t.isNotEmpty()) {
                    store.poner(d, t)
                    pintar()
                }
            }
        }, margen(8))

        panel.addView(texto("Tus atajos", 16f, true).also { it.layoutParams = margen(24) })
        val lista = store.todos()
        if (lista.isEmpty()) panel.addView(texto("Todavía no hay ninguno.", 14f, false, true))
        for ((d, t) in lista) {
            val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fila.addView(texto("$d  →  ${t.replace('\n', ' ')}", 15f, false), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            fila.addView(Button(this).apply {
                text = "✕"
                setOnClickListener {
                    store.quitar(d)
                    pintar()
                }
            })
            panel.addView(fila, margen(4))
        }
    }

    private fun texto(t: String, size: Float, bold: Boolean, dim: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(if (dim) 0xFF8B93A0.toInt() else 0xFFE6E8EB.toInt())
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun margen(arriba: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(arriba) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

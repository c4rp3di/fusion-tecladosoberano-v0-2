package com.vigia.tecladosoberano.settings

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.vigia.tecladosoberano.R
import com.vigia.tecladosoberano.suggest.BigramStore
import com.vigia.tecladosoberano.suggest.BlockStore
import com.vigia.tecladosoberano.suggest.CountStore
import com.vigia.tecladosoberano.suggest.WordStore

/**
 * Qué ha aprendido el teclado, para revisarlo y borrar solo lo que no debería estar.
 * Tres listas: palabras aprendidas, pares de palabras (siguiente palabra) y sugerencias quitadas.
 * Todo está cifrado en el móvil; aquí solo se lee para mostrártelo.
 */
class LearnedWordsActivity : Activity() {

    private lateinit var prefs: Prefs
    private val panel by lazy { LinearLayout(this).apply { orientation = LinearLayout.VERTICAL } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
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
        panel.addView(texto("Palabras aprendidas", 24f, true))
        panel.addView(texto("Marca las que quieras borrar. Lo que no marques se queda tal cual.", 13f, false, true))

        val palabras = WordStore(this)
        seccion(
            "Palabras aprendidas", palabras,
            palabras.todas().map { it.first to "${it.first}   ×${it.second}" },
            "Borrar marcadas", "Todavía no ha aprendido ninguna palabra."
        )

        val pares = BigramStore(this)
        seccion(
            "Pares de palabras (siguiente palabra)", pares,
            pares.todas().map { it.first to it.first.replace("|", "  →  ") + "   ×${it.second}" },
            "Borrar marcados",
            if (prefs.nextWord) "Todavía no hay ninguno." else "Desactivado: no guarda pares."
        )

        val quitadas = BlockStore(this)
        seccion(
            "Sugerencias quitadas", quitadas,
            quitadas.todas().map { it.first to it.first },
            "Restaurar marcadas", "No has quitado ninguna sugerencia."
        )
    }

    private fun seccion(
        titulo: String, almacen: CountStore, filas: List<Pair<String, String>>,
        textoAccion: String, vacio: String
    ) {
        panel.addView(texto("$titulo (${filas.size})", 17f, true).also { it.layoutParams = margen(28) })
        if (filas.isEmpty()) {
            panel.addView(texto(vacio, 14f, false, true))
            return
        }
        val casillas = ArrayList<Pair<CheckBox, String>>()
        for ((clave, etiqueta) in filas) {
            val c = CheckBox(this).apply {
                text = etiqueta
                setTextColor(0xFFE6E8EB.toInt())
            }
            casillas.add(c to clave)
            panel.addView(c)
        }
        val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fila.addView(Button(this).apply {
            text = "Marcar todas"
            setOnClickListener {
                val marcar = casillas.any { !it.first.isChecked }
                casillas.forEach { it.first.isChecked = marcar }
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fila.addView(Button(this).apply {
            text = textoAccion
            setOnClickListener {
                val elegidas = casillas.filter { it.first.isChecked }.map { it.second }
                if (elegidas.isEmpty()) {
                    text = "Marca alguna primero"
                    return@setOnClickListener
                }
                AlertDialog.Builder(this@LearnedWordsActivity)
                    .setMessage("¿$textoAccion (${elegidas.size})?")
                    .setPositiveButton("Sí") { _, _ ->
                        elegidas.forEach { almacen.remove(it) }
                        // El teclado relee sus datos al momento.
                        prefs.bumpDictionary()
                        pintar()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(fila, margen(6))
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

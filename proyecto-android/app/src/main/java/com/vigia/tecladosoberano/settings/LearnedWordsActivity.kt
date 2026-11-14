package com.vigia.tecladosoberano.settings

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.vigia.tecladosoberano.R
import com.vigia.tecladosoberano.suggest.BigramStore
import com.vigia.tecladosoberano.suggest.BlockStore
import com.vigia.tecladosoberano.suggest.CountStore
import com.vigia.tecladosoberano.suggest.Dictionary
import com.vigia.tecladosoberano.suggest.WordStore

/**
 * Qué ha aprendido el teclado, para revisarlo y borrar solo lo que no debería estar.
 * Las listas vienen plegadas; al abrir una se ordena por orden alfabético y aparece a la derecha un índice
 * de letras: se arrastra el dedo por él para saltar a la letra que buscas.
 * Todo está cifrado en el móvil; aquí solo se lee para mostrártelo.
 */
class LearnedWordsActivity : Activity() {

    private lateinit var prefs: Prefs
    private val panel by lazy { LinearLayout(this).apply { orientation = LinearLayout.VERTICAL } }
    private lateinit var scroll: ScrollView
    private lateinit var indice: LinearLayout
    private lateinit var burbuja: TextView

    /** Sección abierta: 0 = palabras, 1 = pares, -1 = ninguna (todas plegadas). */
    private var abierta = -1

    /** Primera fila de cada letra de la lista abierta, para el índice lateral. */
    private val filaPorLetra = LinkedHashMap<String, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = dp(20)
        panel.setPadding(pad, pad, pad, pad)

        scroll = ScrollView(this)
        scroll.addView(panel)

        indice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(0x33000000)
        }
        burbuja = TextView(this).apply {
            textSize = 40f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC22303F.toInt())
            visibility = View.GONE
        }
        indice.setOnTouchListener { v, ev -> tocarIndice(v, ev) }

        val raiz = FrameLayout(this)
        raiz.setBackgroundColor(getColor(R.color.ks_background))
        raiz.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val lpIndice = FrameLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END)
        lpIndice.setMargins(0, dp(70), 0, dp(30))
        raiz.addView(indice, lpIndice)
        raiz.addView(burbuja, FrameLayout.LayoutParams(dp(80), dp(80), Gravity.CENTER))
        setContentView(raiz)
        pintar()
    }

    private fun pintar() {
        panel.removeAllViews()
        filaPorLetra.clear()
        panel.addView(texto("Palabras aprendidas", 24f, true))
        panel.addView(texto("Toca una lista para abrirla y marca lo que quieras borrar. Lo que no marques se queda tal cual.", 13f, false, true))

        val palabras = WordStore(this)
        seccion(
            0, "Palabras aprendidas", palabras,
            palabras.todas().map { it.first to "${it.first}   ×${it.second}" },
            "Todavía no ha aprendido ninguna palabra."
        )

        val pares = BigramStore(this)
        seccion(
            1, "Pares de palabras (siguiente palabra)", pares,
            pares.todas().map { it.first to it.first.replace("|", "  →  ") + "   ×${it.second}" },
            if (prefs.nextWord) "Todavía no hay ninguno." else "Desactivado: no guarda pares."
        )

        // Palabras del diccionario que se quitaron con pulsación larga (las aprendidas se borran directamente).
        val quitadas = BlockStore(this)
        if (quitadas.cantidad() > 0) {
            panel.addView(Button(this).apply {
                text = "Restaurar palabras del diccionario que quitaste (${quitadas.cantidad()})"
                setOnClickListener {
                    AlertDialog.Builder(this@LearnedWordsActivity)
                        .setMessage("¿Volver a sugerir las ${quitadas.cantidad()} palabras que quitaste?")
                        .setPositiveButton("Sí") { _, _ ->
                            quitadas.clear()
                            prefs.bumpDictionary()
                            pintar()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }, margen(28))
        }

        actualizarIndice()
    }

    private fun seccion(
        id: Int, titulo: String, almacen: CountStore, filas: List<Pair<String, String>>, vacio: String
    ) {
        val abierta = this.abierta == id
        val cabecera = texto((if (abierta) "▾  " else "▸  ") + "$titulo (${filas.size})", 17f, true)
        cabecera.setPadding(0, dp(14), 0, dp(14))
        cabecera.setOnClickListener {
            this.abierta = if (abierta) -1 else id
            pintar()
            scroll.scrollTo(0, 0)
        }
        panel.addView(cabecera, margen(14))
        if (!abierta) return
        if (filas.isEmpty()) {
            panel.addView(texto(vacio, 14f, false, true))
            return
        }

        val casillas = ArrayList<Pair<CheckBox, String>>()
        val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fila.addView(Button(this).apply {
            text = "Marcar todas"
            setOnClickListener {
                val marcar = casillas.any { !it.first.isChecked }
                casillas.forEach { it.first.isChecked = marcar }
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fila.addView(Button(this).apply {
            text = "Borrar marcadas"
            setOnClickListener {
                val elegidas = casillas.filter { it.first.isChecked }.map { it.second }
                if (elegidas.isEmpty()) {
                    text = "Marca alguna primero"
                    return@setOnClickListener
                }
                AlertDialog.Builder(this@LearnedWordsActivity)
                    .setMessage("¿Borrar ${elegidas.size} marcadas?")
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
        panel.addView(fila, margen(4))

        // Orden alfabético (sin tildes) para poder saltar por letras.
        val ordenadas = filas.sortedBy { Dictionary.normalizar(it.first) }
        for ((clave, etiqueta) in ordenadas) {
            val c = CheckBox(this).apply {
                text = etiqueta
                setTextColor(0xFFE6E8EB.toInt())
            }
            casillas.add(c to clave)
            panel.addView(c)
            val inicial = inicialDe(clave)
            if (!filaPorLetra.containsKey(inicial)) filaPorLetra[inicial] = c
        }
        panel.setPadding(dp(20), dp(20), dp(40), dp(20))
    }

    private fun inicialDe(clave: String): String {
        val c = Dictionary.normalizar(clave).firstOrNull() ?: return "#"
        return if (c in 'a'..'z' || c == 'ñ') c.uppercaseChar().toString() else "#"
    }

    // ------------------------------------------------------------------ índice lateral

    private fun actualizarIndice() {
        indice.removeAllViews()
        if (abierta < 0 || filaPorLetra.isEmpty()) {
            indice.visibility = View.GONE
            panel.setPadding(dp(20), dp(20), dp(20), dp(20))
            return
        }
        for (letra in filaPorLetra.keys) {
            indice.addView(TextView(this).apply {
                text = letra
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(0xFFB8C0CC.toInt())
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        indice.visibility = View.VISIBLE
    }

    private fun tocarIndice(v: View, ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val letras = filaPorLetra.keys.toList()
                if (letras.isEmpty() || v.height == 0) return true
                val i = (ev.y / v.height * letras.size).toInt().coerceIn(0, letras.size - 1)
                val fila = filaPorLetra[letras[i]] ?: return true
                scroll.scrollTo(0, fila.top)
                burbuja.text = letras[i]
                burbuja.visibility = View.VISIBLE
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> burbuja.visibility = View.GONE
        }
        return true
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

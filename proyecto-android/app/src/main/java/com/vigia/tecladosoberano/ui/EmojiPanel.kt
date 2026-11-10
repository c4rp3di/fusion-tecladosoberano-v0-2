package com.vigia.tecladosoberano.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.annotation.SuppressLint
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.vigia.tecladosoberano.R

/**
 * Panel de emojis y caracteres especiales por categorías.
 * Pestaña 0 = recientes; las demás vienen de [EmojiData]. Barra inferior: ABC · categorías · ⌫.
 * Solo se muestran los emojis que el teléfono sabe dibujar (Paint.hasGlyph).
 */
class EmojiPanel(context: Context) : LinearLayout(context) {

    var onEmoji: ((String) -> Unit)? = null
    var onDelete: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onSearch: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private var theme = KeyboardTheme.DEFAULT
    private var recents: List<String> = emptyList()
    private var selected = 1

    private val grid = LinearLayout(context)
    private val scroll = ScrollView(context)
    private val btnBack = barButton(context.getString(R.string.panel_back))
    private val btnDelete = barButton("⌫")
    private val searchRow = TextView(context)
    private val tabs = ArrayList<TextView>()
    private val glyphPaint = Paint()
    private val cache = HashMap<Int, List<String>>()

    private val handler = Handler(Looper.getMainLooper())
    private val repeatDelete = object : Runnable {
        override fun run() {
            onDelete?.invoke()
            handler.postDelayed(this, 60L)
        }
    }

    init {
        orientation = VERTICAL

        searchRow.text = context.getString(R.string.emoji_buscar)
        searchRow.textSize = 14f
        searchRow.gravity = Gravity.CENTER_VERTICAL
        searchRow.setPadding(dp(14), 0, dp(14), 0)
        searchRow.isClickable = true
        searchRow.setOnClickListener { onSearch?.invoke() }
        val lpBusca = LayoutParams(LayoutParams.MATCH_PARENT, dp(36))
        lpBusca.setMargins(dp(6), dp(4), dp(6), dp(2))
        addView(searchRow, lpBusca)

        grid.orientation = VERTICAL
        scroll.addView(grid)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(context)
        bar.orientation = HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(4), dp(2), dp(4), dp(4))

        btnBack.setOnClickListener { onClose?.invoke() }
        bar.addView(btnBack, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.3f))

        val iconos = listOf("🕘") + EmojiData.categories.map { it.icon }
        iconos.forEachIndexed { i, icono ->
            val t = TextView(context)
            t.text = icono
            t.gravity = Gravity.CENTER
            t.contentDescription = if (i == 0) context.getString(R.string.emoji_recientes) else EmojiData.categories[i - 1].name
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            t.setOnClickListener { select(i) }
            tabs.add(t)
            bar.addView(t, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        bar.addView(btnDelete, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.3f))
        configureDelete()
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dp(46)))

        applyTheme(theme)
    }

    // ------------------------------------------------------------------ API

    fun applyTheme(t: KeyboardTheme) {
        theme = t
        setBackgroundColor(t.background)
        btnBack.setTextColor(t.text)
        btnBack.setPressableBackground(t.keyFunction, pressedColorFor(t, t.keyFunction), dp(8).toFloat())
        searchRow.setTextColor(0xFF8B93A0.toInt())
        searchRow.background = rounded(t.keyFunction)
        btnDelete.setTextColor(t.text)
        btnDelete.background = rounded(t.keyFunction)
        selectTabVisual()
        showCategory(selected)
    }

    fun setRecents(nuevos: List<String>) {
        recents = nuevos
        if (selected == 0) showCategory(0)
    }

    /** Al abrir el panel: recientes si los hay; si no, las caritas. */
    fun open() {
        select(if (recents.isEmpty()) 1 else 0)
    }

    // ------------------------------------------------------------------ categorías

    private fun select(i: Int) {
        selected = i
        selectTabVisual()
        showCategory(i)
    }

    private fun selectTabVisual() {
        tabs.forEachIndexed { i, t ->
            if (i == selected) {
                t.background = rounded(pressedColorFor(theme, theme.key))
            } else {
                t.background = null
            }
        }
    }

    private fun itemsFor(i: Int): List<String> {
        if (i == 0) return recents.filter { glyphPaint.hasGlyph(it) }
        return cache.getOrPut(i) { EmojiData.categories[i - 1].items.filter { glyphPaint.hasGlyph(it) } }
    }

    private fun columnCount(): Int =
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 12 else 8

    private fun showCategory(i: Int) {
        grid.removeAllViews()
        val lista = itemsFor(i)
        if (lista.isEmpty()) {
            val vacio = TextView(context)
            vacio.text = context.getString(R.string.emoji_sin_recientes)
            vacio.setTextColor(0xFF8B93A0.toInt())
            vacio.textSize = 14f
            vacio.gravity = Gravity.CENTER
            vacio.setPadding(dp(24), dp(32), dp(24), dp(32))
            grid.addView(vacio, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            return
        }
        val columnas = columnCount()
        var inicio = 0
        while (inicio < lista.size) {
            val fila = LinearLayout(context)
            fila.orientation = HORIZONTAL
            for (c in 0 until columnas) {
                val idx = inicio + c
                val celda: View = if (idx < lista.size) celda(lista[idx], i == selected && selected == tabEspeciales()) else View(context)
                fila.addView(celda, LayoutParams(0, dp(46), 1f))
            }
            grid.addView(fila, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            inicio += columnas
        }
        scroll.scrollTo(0, 0)
    }

    private fun tabEspeciales(): Int = EmojiData.categories.size

    private fun celda(emoji: String, esTexto: Boolean): TextView {
        val t = TextView(context)
        t.text = emoji
        t.gravity = Gravity.CENTER
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (esTexto) 22f else 25f)
        if (esTexto) t.setTextColor(theme.text)
        t.isClickable = true
        t.setPressableBackground(Color.TRANSPARENT, pressedColorFor(theme, theme.background), dp(8).toFloat())
        t.setOnClickListener { onEmoji?.invoke(emoji) }
        return t
    }

    // ------------------------------------------------------------------ botones

    @SuppressLint("ClickableViewAccessibility")
    private fun configureDelete() {
        btnDelete.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.background = rounded(pressedColorFor(theme, theme.keyFunction))
                    onDelete?.invoke()
                    handler.postDelayed(repeatDelete, 400L)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(repeatDelete)
                    v.background = rounded(theme.keyFunction)
                }
            }
            true
        }
    }

    private fun barButton(texto: String): TextView {
        val b = TextView(context)
        b.text = texto
        b.textSize = 15f
        b.setTypeface(b.typeface, Typeface.BOLD)
        b.gravity = Gravity.CENTER
        b.isClickable = true
        val lp = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.3f)
        lp.setMargins(dp(2), 0, dp(2), 0)
        b.layoutParams = lp
        return b
    }

    private fun rounded(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(8).toFloat()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(repeatDelete)
        super.onDetachedFromWindow()
    }

    private fun dp(v: Int): Int = (v * density).toInt()
}

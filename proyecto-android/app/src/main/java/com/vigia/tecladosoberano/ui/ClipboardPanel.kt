package com.vigia.tecladosoberano.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.vigia.tecladosoberano.R
import com.vigia.tecladosoberano.clipboard.ClipboardStore

/**
 * Panel del historial del portapapeles. Un toque en un elemento lo escribe en el campo; ✕ lo borra del historial.
 * Construido por código: sin RecyclerView ni más dependencias (son como mucho 25 elementos).
 */
class ClipboardPanel(context: Context) : LinearLayout(context) {

    var onInsert: ((String) -> Unit)? = null
    var onDelete: ((Int) -> Unit)? = null
    var onClear: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private var theme = KeyboardTheme.DEFAULT
    private var items: List<ClipboardStore.Item> = emptyList()

    private val btnBack = headerButton(context.getString(R.string.panel_back))
    private val btnClear = headerButton(context.getString(R.string.clip_clear))
    private val title = TextView(context)
    private val list = LinearLayout(context)

    init {
        orientation = VERTICAL

        val header = LinearLayout(context)
        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(6), dp(4), dp(6), dp(4))

        btnBack.setOnClickListener { onClose?.invoke() }
        btnClear.setOnClickListener { onClear?.invoke() }

        title.text = context.getString(R.string.clip_title)
        title.gravity = Gravity.CENTER
        title.textSize = 15f
        title.setTypeface(title.typeface, Typeface.BOLD)

        header.addView(btnBack, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        header.addView(title, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        header.addView(btnClear, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))

        list.orientation = VERTICAL
        val scroll = ScrollView(context)
        scroll.isFillViewport = false
        scroll.addView(list)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        applyTheme(theme)
    }

    fun applyTheme(t: KeyboardTheme) {
        theme = t
        setBackgroundColor(t.background)
        title.setTextColor(t.text)
        for (b in listOf(btnBack, btnClear)) {
            b.setTextColor(t.text)
            b.setPressableBackground(t.keyFunction, pressedColorFor(t, t.keyFunction), dp(8).toFloat())
        }
        setItems(items)
    }

    fun setItems(nuevos: List<ClipboardStore.Item>) {
        items = nuevos
        list.removeAllViews()
        btnClear.visibility = if (items.isEmpty()) View.INVISIBLE else View.VISIBLE

        if (items.isEmpty()) {
            val vacio = TextView(context)
            vacio.text = context.getString(R.string.clip_empty)
            vacio.setTextColor(0xFF8B93A0.toInt())
            vacio.textSize = 14f
            vacio.gravity = Gravity.CENTER
            vacio.setPadding(dp(24), dp(32), dp(24), dp(32))
            list.addView(vacio, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            return
        }
        items.forEachIndexed { i, item -> list.addView(row(i, item)) }
    }

    private fun row(index: Int, item: ClipboardStore.Item): View {
        val fila = LinearLayout(context)
        fila.orientation = HORIZONTAL
        fila.gravity = Gravity.CENTER_VERTICAL

        val texto = TextView(context)
        texto.text = item.text
        texto.setTextColor(theme.text)
        texto.textSize = 15f
        texto.maxLines = 3
        texto.ellipsize = TextUtils.TruncateAt.END
        texto.setPadding(dp(12), dp(10), dp(12), dp(10))
        texto.minHeight = dp(44)
        texto.gravity = Gravity.CENTER_VERTICAL
        texto.isClickable = true
        texto.setPressableBackground(theme.key, pressedColorFor(theme, theme.key), dp(8).toFloat())
        texto.setOnClickListener { onInsert?.invoke(item.text) }

        val borrar = TextView(context)
        borrar.text = "✕"
        borrar.setTextColor(0xFF8B93A0.toInt())
        borrar.textSize = 16f
        borrar.gravity = Gravity.CENTER
        borrar.contentDescription = context.getString(R.string.clip_delete)
        borrar.setBackgroundColor(Color.TRANSPARENT)
        borrar.setOnClickListener { onDelete?.invoke(index) }

        fila.addView(texto, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        fila.addView(borrar, LayoutParams(dp(44), dp(44)))

        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(6), dp(3), dp(2), dp(3))
        fila.layoutParams = lp
        return fila
    }

    private fun headerButton(texto: String): TextView {
        val b = TextView(context)
        b.text = texto
        b.textSize = 14f
        b.setTypeface(b.typeface, Typeface.BOLD)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(14), 0, dp(14), 0)
        b.isClickable = true
        return b
    }

    private fun dp(v: Int): Int = (v * density).toInt()
}

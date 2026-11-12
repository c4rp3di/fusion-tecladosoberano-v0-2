package com.vigia.tecladosoberano.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.vigia.tecladosoberano.ime.ShiftState
import com.vigia.tecladosoberano.settings.KbMetrics

/**
 * Vista de teclado dibujada con Canvas: ligera, sin dependencias y con soporte
 * multitáctil (varios dedos a la vez para escribir rápido).
 *
 * - La tecla se dispara al levantar el dedo, sobre la tecla que haya debajo (corrige deslices).
 * - La zona táctil de cada tecla ocupa toda su celda: no hay huecos muertos entre teclas.
 * - Feedback visual: la tecla pulsada se rellena con el color de acento. Sin vibración.
 * - Pulsación larga en una tecla con alternativas ([Key.alts]): se abre una ventanita sobre la tecla;
 *   se desliza el dedo en horizontal para elegir y se suelta para escribir.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onKey(key: Key)
    }

    var listener: Listener? = null

    var keyboardTheme: KeyboardTheme = KeyboardTheme.DEFAULT
        set(value) {
            field = value
            invalidate()
        }

    var shiftState: ShiftState = ShiftState.OFF
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Medidas editables (alturas, anchos, separación, sensibilidad). */
    var metrics: KbMetrics = KbMetrics()
        set(value) {
            field = value
            requestLayout()
            computeRects()
            invalidate()
        }

    /** Si no es null, fuerza el perfil horizontal/vertical (la vista previa del editor). */
    var forceLandscape: Boolean? = null
        set(value) {
            field = value
            requestLayout()
        }

    /** Probabilidad de la siguiente letra (minúscula) según lo escrito: el «iman» hacia la letra probable. */
    @Volatile
    var nextLetter: Map<Char, Float>? = null

    private var rows: List<List<Key>> = emptyList()
    private var rowTops = FloatArray(0)
    private val keyRects = ArrayList<Pair<Key, RectF>>()
    private val pressedKeys = HashMap<Int, Key>()

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val gap: Float get() = metrics.gapDp * density
    private val corner = 8f * density
    private val relief = 2f * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val bodyRect = RectF()
    private val shadowRect = RectF()
    private val haloRect = RectF()

    private val uiHandler = Handler(Looper.getMainLooper())

    // --- repetición al mantener pulsado (borrar)
    private var repeatKey: Key? = null
    private var repeatFired = false
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val key = repeatKey ?: return
            repeatFired = true
            listener?.onKey(key)
            uiHandler.postDelayed(this, REPEAT_INTERVAL_MS)
        }
    }

    // --- pulsación larga con ventanita de alternativas
    private var pendingKey: Key? = null
    private var pendingPointer = -1
    private var pendingX = 0f
    private val longPressRunnable = Runnable { openPopup() }

    private var popupPointer = -1
    private var popupKey: Key? = null
    private var popupItems: List<String> = emptyList()
    private var popupRects: List<RectF> = emptyList()
    private var popupSelected = -1

    fun setRows(newRows: List<List<Key>>) {
        if (rows === newRows) return
        rows = newRows
        pressedKeys.clear()
        stopRepeat()
        cancelPending()
        closePopup()
        requestLayout()
        computeRects()
        invalidate()
    }

    private fun rowHeightPx(): Float {
        val landscape = forceLandscape ?: (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        return (if (landscape) 44f else 54f) * density * metrics.height
    }

    private fun rowScale(i: Int): Float = if (rows.size == 4) metrics.rows[i].coerceIn(0.5f, 1.8f) else 1f

    private fun keyWeight(key: Key): Float = key.weight * when (key.type) {
        KeyType.SPACE -> metrics.space
        KeyType.SHIFT, KeyType.DELETE, KeyType.ENTER,
        KeyType.TO_LETTERS, KeyType.TO_SYMBOLS, KeyType.TO_SYMBOLS2, KeyType.TO_NUMERIC -> metrics.func
        else -> 1f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        var h = gap
        for (i in 0 until rows.size.coerceAtLeast(1)) h += rowHeightPx() * rowScale(i)
        setMeasuredDimension(w, h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeRects()
    }

    private fun computeRects() {
        keyRects.clear()
        if (rows.isEmpty() || width == 0 || height == 0) return
        val suma = (0 until rows.size).sumOf { rowScale(it).toDouble() }.toFloat()
        rowTops = FloatArray(rows.size + 1)
        for (i in rows.indices) rowTops[i + 1] = rowTops[i] + height * rowScale(i) / suma
        rows.forEachIndexed { r, row ->
            val total = row.sumOf { keyWeight(it).toDouble() }.toFloat()
            var x = 0f
            for (key in row) {
                val w = width * keyWeight(key) / total
                keyRects.add(key to RectF(x, rowTops[r], x + w, rowTops[r + 1]))
                x += w
            }
        }
    }

    /**
     * Tecla bajo el dedo. Con «iman», entre letras vecinas gana la que está más cerca del dedo
     * pero favoreciendo la letra probable según lo que se lleva escrito.
     */
    private fun keyAt(x: Float, y: Float): Key? {
        var directa: Pair<Key, RectF>? = null
        for (par in keyRects) {
            if (par.second.contains(x, y)) {
                directa = par
                break
            }
        }
        val d = directa ?: return null
        val probs = nextLetter
        val iman = metrics.magnet
        if (iman <= 0f || probs == null || d.first.type != KeyType.CHAR) return d.first

        var mejor = d.first
        var mejorPuntos = Float.MAX_VALUE
        for ((key, rect) in keyRects) {
            if (key.type != KeyType.CHAR || key.label.length != 1) continue
            // Solo teclas a menos de ~40 % de su ancho del dedo.
            val dx = Math.max(Math.max(rect.left - x, x - rect.right), 0f)
            val dy = Math.max(Math.max(rect.top - y, y - rect.bottom), 0f)
            if (dx > rect.width() * 0.4f || dy > 0f) continue
            val cx = (x - rect.centerX()) / rect.width()
            val cy = (y - rect.centerY()) / rect.height() * 0.5f
            val dist = Math.sqrt((cx * cx + cy * cy).toDouble()).toFloat()
            val p = probs[key.label[0].lowercaseChar()] ?: 0f
            val puntos = dist / (1f + iman * 4f * p)
            if (puntos < mejorPuntos) {
                mejorPuntos = puntos
                mejor = key
            }
        }
        return mejor
    }

    // ------------------------------------------------------------------ dibujo

    override fun onDraw(canvas: Canvas) {
        val theme = keyboardTheme
        val pressed = pressedKeys.values

        for ((key, cell) in keyRects) {
            val isPressed = pressed.any { it === key }

            bodyRect.set(cell)
            bodyRect.inset(gap, gap)

            val isCaps = key.type == KeyType.SHIFT && shiftState == ShiftState.CAPS
            val isShiftOnce = key.type == KeyType.SHIFT && shiftState == ShiftState.ONCE

            val keyColor = when {
                isPressed || isCaps -> theme.accent
                key.isFunction -> theme.keyFunction
                else -> theme.key
            }
            val textColor = when {
                isPressed || isCaps -> theme.onAccent
                isShiftOnce -> theme.accent
                else -> theme.text
            }

            if (!isPressed) {
                // Relieve: una sombra desplazada hacia abajo bajo la tecla.
                shadowRect.set(bodyRect)
                shadowRect.offset(0f, relief)
                fillPaint.color = theme.shadow
                canvas.drawRoundRect(shadowRect, corner, corner, fillPaint)
                bodyRect.bottom -= relief
            }

            fillPaint.color = keyColor
            canvas.drawRoundRect(bodyRect, corner, corner, fillPaint)

            val label = key.labelFor(shiftState)
            if (label.isNotEmpty()) {
                textPaint.color = textColor
                // Por puntos de código: un emoji (2 chars) cuenta como un símbolo y se dibuja grande.
                val simbolos = label.codePointCount(0, label.length)
                textPaint.textSize = (if (simbolos > 1) 15f else 22f) * scaledDensity * metrics.text
                val baseline = bodyRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(label, bodyRect.centerX(), baseline, textPaint)
            }

            // Pista de la pulsación larga (el número de la fila superior, la ? del punto).
            val hint = key.hint
            if (hint != null) {
                hintPaint.color = (textColor and 0x00FFFFFF) or (0x99 shl 24)
                hintPaint.textSize = 10.5f * scaledDensity
                canvas.drawText(hint, bodyRect.right - 5f * density, bodyRect.top + 13f * density, hintPaint)
            }
        }

        if (popupKey != null && popupRects.isNotEmpty()) drawPopup(canvas, theme)
    }

    private fun drawPopup(canvas: Canvas, theme: KeyboardTheme) {
        val first = popupRects.first()
        val last = popupRects.last()
        bodyRect.set(first.left, first.top, last.right, first.bottom)
        bodyRect.inset(gap, gap)

        // Halo oscuro para despegar la ventanita de las teclas de debajo.
        haloRect.set(bodyRect)
        haloRect.inset(-3f * density, -3f * density)
        fillPaint.color = theme.shadow
        canvas.drawRoundRect(haloRect, corner + 3f * density, corner + 3f * density, fillPaint)
        fillPaint.color = theme.keyFunction
        canvas.drawRoundRect(bodyRect, corner, corner, fillPaint)

        popupRects.forEachIndexed { i, cell ->
            val selected = i == popupSelected
            bodyRect.set(cell)
            bodyRect.inset(gap * 1.5f, gap * 1.5f)
            if (selected) {
                fillPaint.color = theme.accent
                canvas.drawRoundRect(bodyRect, corner, corner, fillPaint)
            }
            val item = popupItems[i]
            val etiqueta = if (shiftState != ShiftState.OFF) item.uppercase() else item
            textPaint.color = if (selected) theme.onAccent else theme.text
            textPaint.textSize = 22f * scaledDensity
            val baseline = bodyRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(etiqueta, bodyRect.centerX(), baseline, textPaint)
        }
    }

    // ------------------------------------------------------------------ táctil

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val key = keyAt(event.getX(i), event.getY(i))
                // Un dedo nuevo anula una pulsación larga pendiente de otro dedo: la primera tecla se escribe normal.
                cancelPending()
                if (key != null) {
                    val id = event.getPointerId(i)
                    pressedKeys[id] = key
                    if (key.repeatable) {
                        startRepeat(key)
                    } else if (key.alts.isNotEmpty() && popupPointer == -1) {
                        schedulePending(key, id, event.getX(i))
                    }
                    invalidate()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                var changed = false
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)

                    // Dedo con la ventanita abierta: solo elige alternativa.
                    if (id == popupPointer) {
                        val sel = nearestCell(event.getX(i))
                        if (sel != popupSelected) {
                            popupSelected = sel
                            changed = true
                        }
                        continue
                    }

                    val old = pressedKeys[id] ?: continue
                    if (id == pendingPointer) pendingX = event.getX(i)
                    // Con «decidir al tocar» la tecla queda fijada por el primer toque.
                    if (!metrics.onRelease) continue
                    val now = keyAt(event.getX(i), event.getY(i))
                    if (now != null && now !== old) {
                        pressedKeys[id] = now
                        if (repeatKey === old) stopRepeat()
                        if (id == pendingPointer) cancelPending()
                        changed = true
                    }
                }
                if (changed) invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = event.actionIndex
                val id = event.getPointerId(i)

                if (id == popupPointer) {
                    // Soltar sobre una alternativa la escribe (como una tecla normal: respeta Shift).
                    val alt = popupItems.getOrNull(popupSelected)
                    pressedKeys.remove(id)
                    closePopup()
                    if (alt != null) listener?.onKey(Key(alt))
                    invalidate()
                    return true
                }
                if (id == pendingPointer) cancelPending()

                val pressed = pressedKeys.remove(id)
                if (pressed != null) {
                    val target = if (metrics.onRelease) keyAt(event.getX(i), event.getY(i)) ?: pressed else pressed
                    val alreadyFiredByRepeat = repeatFired && repeatKey === pressed
                    if (repeatKey === pressed) stopRepeat()
                    if (!alreadyFiredByRepeat) listener?.onKey(target)
                }
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedKeys.clear()
                stopRepeat()
                cancelPending()
                closePopup()
                invalidate()
            }
        }
        return true
    }

    // ------------------------------------------------------------------ pulsación larga

    private fun schedulePending(key: Key, pointerId: Int, x: Float) {
        pendingKey = key
        pendingPointer = pointerId
        pendingX = x
        uiHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
    }

    private fun cancelPending() {
        uiHandler.removeCallbacks(longPressRunnable)
        pendingKey = null
        pendingPointer = -1
    }

    /**
     * Abre la ventanita sobre la tecla (en la fila de arriba; en la primera fila, sobre la propia fila).
     * La alternativa principal queda justo encima de la tecla y las demás se extienden hacia el centro de la pantalla.
     */
    private fun openPopup() {
        val key = pendingKey ?: return
        val id = pendingPointer
        if (pressedKeys[id] !== key || key.alts.isEmpty()) {
            cancelPending()
            return
        }
        val rect = keyRects.firstOrNull { it.first === key }?.second ?: return
        val rowIndex = rows.indexOfFirst { fila -> fila.any { it === key } }
        if (rowIndex < 0 || width == 0) return

        val n = key.alts.size
        val cellW = minOf(maxOf(rect.width(), 40f * density), width.toFloat() / n)
        val total = cellW * n
        val derecha = rect.centerX() > width / 2f
        var x0 = if (derecha) rect.right - total else rect.left
        x0 = x0.coerceIn(0f, maxOf(0f, width - total))

        val fila = if (rowIndex > 0) rowIndex - 1 else rowIndex
        val top = rowTops[fila]
        val bottom = rowTops[fila + 1]

        // En el lado derecho se dibuja al revés para que la alternativa principal siga sobre la tecla.
        popupItems = if (derecha) key.alts.reversed() else key.alts
        popupRects = List(n) { c -> RectF(x0 + c * cellW, top, x0 + (c + 1) * cellW, bottom) }
        popupKey = key
        popupPointer = id
        popupSelected = nearestCell(pendingX)

        uiHandler.removeCallbacks(longPressRunnable)
        pendingKey = null
        pendingPointer = -1
        invalidate()
    }

    private fun nearestCell(x: Float): Int {
        var mejor = -1
        var distancia = Float.MAX_VALUE
        popupRects.forEachIndexed { i, r ->
            val d = Math.abs(r.centerX() - x)
            if (d < distancia) {
                distancia = d
                mejor = i
            }
        }
        return mejor
    }

    private fun closePopup() {
        popupKey = null
        popupPointer = -1
        popupItems = emptyList()
        popupRects = emptyList()
        popupSelected = -1
    }

    // ------------------------------------------------------------------ repetición

    private fun startRepeat(key: Key) {
        stopRepeat()
        repeatKey = key
        repeatFired = false
        uiHandler.postDelayed(repeatRunnable, REPEAT_START_DELAY_MS)
    }

    private fun stopRepeat() {
        uiHandler.removeCallbacks(repeatRunnable)
        repeatKey = null
        repeatFired = false
    }

    override fun onDetachedFromWindow() {
        stopRepeat()
        cancelPending()
        closePopup()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val REPEAT_START_DELAY_MS = 380L
        const val REPEAT_INTERVAL_MS = 45L
        const val LONG_PRESS_MS = 350L
    }
}

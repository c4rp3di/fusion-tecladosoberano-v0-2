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

/**
 * Vista de teclado dibujada con Canvas: ligera, sin dependencias y con soporte
 * multitáctil (varios dedos a la vez para escribir rápido).
 *
 * - La tecla se dispara al levantar el dedo, sobre la tecla que haya debajo (corrige deslices).
 * - La zona táctil de cada tecla ocupa toda su celda: no hay huecos muertos entre teclas.
 * - Feedback visual: la tecla pulsada se rellena con el color de acento. Sin vibración.
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

    private var rows: List<List<Key>> = emptyList()
    private val keyRects = ArrayList<Pair<Key, RectF>>()
    private val pressedKeys = HashMap<Int, Key>()

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val gap = 2.5f * density
    private val corner = 8f * density
    private val relief = 2f * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val bodyRect = RectF()
    private val shadowRect = RectF()

    private val uiHandler = Handler(Looper.getMainLooper())
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

    fun setRows(newRows: List<List<Key>>) {
        if (rows === newRows) return
        rows = newRows
        pressedKeys.clear()
        stopRepeat()
        requestLayout()
        computeRects()
        invalidate()
    }

    private fun rowHeightPx(): Float {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return (if (landscape) 44f else 54f) * density
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (rowHeightPx() * rows.size.coerceAtLeast(1) + gap).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeRects()
    }

    private fun computeRects() {
        keyRects.clear()
        if (rows.isEmpty() || width == 0 || height == 0) return
        val rowH = height.toFloat() / rows.size
        rows.forEachIndexed { r, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            var x = 0f
            for (key in row) {
                val w = width * key.weight / total
                keyRects.add(key to RectF(x, r * rowH, x + w, (r + 1) * rowH))
                x += w
            }
        }
    }

    private fun keyAt(x: Float, y: Float): Key? {
        for ((key, rect) in keyRects) {
            if (rect.contains(x, y)) return key
        }
        return null
    }

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
                textPaint.textSize = (if (label.length > 1) 15f else 22f) * scaledDensity
                val baseline = bodyRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(label, bodyRect.centerX(), baseline, textPaint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val key = keyAt(event.getX(i), event.getY(i))
                if (key != null) {
                    pressedKeys[event.getPointerId(i)] = key
                    if (key.repeatable) startRepeat(key)
                    invalidate()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                var changed = false
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val old = pressedKeys[id] ?: continue
                    val now = keyAt(event.getX(i), event.getY(i))
                    if (now != null && now !== old) {
                        pressedKeys[id] = now
                        if (repeatKey === old) stopRepeat()
                        changed = true
                    }
                }
                if (changed) invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = event.actionIndex
                val pressed = pressedKeys.remove(event.getPointerId(i))
                if (pressed != null) {
                    val target = keyAt(event.getX(i), event.getY(i)) ?: pressed
                    val alreadyFiredByRepeat = repeatFired && repeatKey === pressed
                    if (repeatKey === pressed) stopRepeat()
                    if (!alreadyFiredByRepeat) listener?.onKey(target)
                }
                invalidate()
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedKeys.clear()
                stopRepeat()
                invalidate()
            }
        }
        return true
    }

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
        super.onDetachedFromWindow()
    }

    private companion object {
        const val REPEAT_START_DELAY_MS = 380L
        const val REPEAT_INTERVAL_MS = 45L
    }
}

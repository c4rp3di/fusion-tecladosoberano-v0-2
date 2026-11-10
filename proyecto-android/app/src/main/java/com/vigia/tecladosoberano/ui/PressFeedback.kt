package com.vigia.tecladosoberano.ui

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils

/** Color de una zona pulsada: el color base mezclado con el acento (feedback visual, sin vibración). */
fun pressedColorFor(theme: KeyboardTheme, base: Int): Int = ColorUtils.blendARGB(base, theme.accent, 0.55f)

/** Fondo redondeado que cambia de color mientras se mantiene pulsado. No bloquea el clic normal. */
@SuppressLint("ClickableViewAccessibility")
fun View.setPressableBackground(normal: Int, pressed: Int, radiusPx: Float) {
    fun fondo(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
    }
    background = fondo(normal)
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.background = fondo(pressed)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.background = fondo(normal)
        }
        false
    }
}

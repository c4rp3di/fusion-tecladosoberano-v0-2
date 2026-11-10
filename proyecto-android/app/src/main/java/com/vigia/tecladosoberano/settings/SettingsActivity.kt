package com.vigia.tecladosoberano.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.vigia.tecladosoberano.R

/**
 * Pantalla de ajustes: activar el teclado, elegirlo y personalizar colores.
 * Interfaz construida por código (sin dependencias de UI externas).
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs

    private val accentChoices = intArrayOf(
        0xFF22D3A6.toInt(), // verde menta (por defecto)
        0xFF4FC3F7.toInt(), // azul
        0xFFB388FF.toInt(), // violeta
        0xFFFF8A65.toInt(), // naranja
        0xFFFFD54F.toInt(), // ámbar
        0xFFFF6B8A.toInt()  // rosa
    )

    private val backgroundChoices = intArrayOf(
        0xFF0E1116.toInt(), // carbón (por defecto)
        0xFF000000.toInt(), // negro puro
        0xFF0B1620.toInt(), // azul noche
        0xFF14110F.toInt()  // marrón oscuro
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val pad = dp(20)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        content.addView(label(getString(R.string.app_name), 26f, bold = true))
        content.addView(label(getString(R.string.settings_intro), 14f, dim = true).withTopMargin(8))

        content.addView(button(getString(R.string.settings_enable)) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }.withTopMargin(24))

        content.addView(button(getString(R.string.settings_pick)) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }.withTopMargin(8))

        content.addView(label(getString(R.string.settings_accent), 16f, bold = true).withTopMargin(28))
        content.addView(swatchRow(accentChoices) { prefs.accent = it }.withTopMargin(10))

        content.addView(label(getString(R.string.settings_background), 16f, bold = true).withTopMargin(24))
        content.addView(swatchRow(backgroundChoices) { prefs.background = it }.withTopMargin(10))

        val test = EditText(this).apply {
            hint = getString(R.string.settings_test_hint)
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF8B93A0.toInt())
        }
        content.addView(test.withTopMargin(32))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.ks_background))
            addView(content)
        })
    }

    private fun label(text: String, sizeSp: Float, bold: Boolean = false, dim: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(if (dim) 0xFF8B93A0.toInt() else 0xFFE6E8EB.toInt())
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun swatchRow(colors: IntArray, onPick: (Int) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        colors.forEach { color ->
            val swatch = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(dp(1), 0x55FFFFFF)
                }
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) }
                setOnClickListener { onPick(color) }
            }
            addView(swatch)
        }
    }

    private fun <T : View> T.withTopMargin(dpValue: Int): T {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(dpValue) }
        return this
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

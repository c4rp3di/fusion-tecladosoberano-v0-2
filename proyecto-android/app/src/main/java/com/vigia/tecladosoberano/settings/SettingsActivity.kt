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

        content.addView(button("Editor del teclado (tamaño, teclas, sensibilidad)") {
            startActivity(Intent(this, KeyboardEditorActivity::class.java))
        }.withTopMargin(24))
        val estadoDic = label(estadoDiccionario(), 13f, dim = true)
        content.addView(button("Importar diccionario (.txt)") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/*")
            startActivityForResult(i, REQ_DICCIONARIO)
        }.withTopMargin(8))
        content.addView(estadoDic.withTopMargin(4))
        estadoDiccionarioView = estadoDic
        content.addView(button("Volver al diccionario integrado") {
            com.vigia.tecladosoberano.suggest.DictionaryImporter.restaurar(this)
            prefs.bumpDictionary()
            estadoDic.text = estadoDiccionario()
        }.withTopMargin(4))
        content.addView(button("Atajos de texto") {
            startActivity(Intent(this, ShortcutsActivity::class.java))
        }.withTopMargin(8))

        content.addView(label("Barra de sugerencias: color del texto", 16f, bold = true).withTopMargin(24))
        content.addView(swatchRow(intArrayOf(0, 0xFFFFFFFF.toInt(), 0xFF22D3A6.toInt(), 0xFFFFD54F.toInt(), 0xFF4FC3F7.toInt(), 0xFFFF8A65.toInt())) { prefs.barText = it }.withTopMargin(10))
        content.addView(label("Barra de sugerencias: fondo (el círculo vacío es el del tema)", 16f, bold = true).withTopMargin(18))
        content.addView(swatchRow(intArrayOf(0, 0xFF1A2029.toInt(), 0xFF000000.toInt(), 0xFF0B2A3A.toInt(), 0xFF2A1A33.toInt())) { prefs.barBg = it }.withTopMargin(10))

        content.addView(label("Sugerencias: opciones de privacidad (todo local y cifrado)", 16f, bold = true).withTopMargin(28))
        content.addView(android.widget.Switch(this).apply {
            text = "Recordar mis palabras frecuentes (también las del diccionario)"
            setTextColor(Color.WHITE)
            isChecked = prefs.learnCommon
            setOnCheckedChangeListener { _, on -> prefs.learnCommon = on }
        }.withTopMargin(8))
        content.addView(android.widget.Switch(this).apply {
            text = "Predecir la siguiente palabra (aprende pares de palabras)"
            setTextColor(Color.WHITE)
            isChecked = prefs.nextWord
            setOnCheckedChangeListener { _, on -> prefs.nextWord = on }
        }.withTopMargin(8))

        content.addView(label(getString(R.string.settings_learn), 14f, dim = true).withTopMargin(28))
        content.addView(android.widget.Switch(this).apply {
            isChecked = prefs.learnWords
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, on -> prefs.learnWords = on }
        }.withTopMargin(6))
        content.addView(button(getString(R.string.settings_forget)) {
            com.vigia.tecladosoberano.suggest.WordStore(this).clear()
            com.vigia.tecladosoberano.suggest.BigramStore(this).clear()
            it.text = getString(R.string.settings_forgotten)
        }.withTopMargin(8))

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

    private var estadoDiccionarioView: TextView? = null

    private fun estadoDiccionario(): String =
        if (com.vigia.tecladosoberano.suggest.DictionaryImporter.hayImportado(this))
            "Usando tu diccionario importado (con el integrado detrás)."
        else "Usando el diccionario integrado. Formato del .txt: una palabra por línea, de más a menos frecuente, o «palabra frecuencia». Máx. 150.000 palabras."

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_DICCIONARIO || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        estadoDiccionarioView?.text = "Importando…"
        Thread {
            val mensaje = try {
                val r = com.vigia.tecladosoberano.suggest.DictionaryImporter.importar(this, uri)
                prefs.bumpDictionary()
                "Importadas ${r.palabras} palabras de ${r.lineas} líneas" + (if (r.recortado) " (recortado a las ${com.vigia.tecladosoberano.suggest.DictionaryImporter.MAX_PALABRAS} primeras/más frecuentes)." else ".")
            } catch (e: Exception) {
                "No se pudo importar: el archivo no se pudo leer o no tiene palabras válidas."
            }
            runOnUiThread { estadoDiccionarioView?.text = mensaje }
        }.start()
    }

    private fun label(text: String, sizeSp: Float, bold: Boolean = false, dim: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(if (dim) 0xFF8B93A0.toInt() else 0xFFE6E8EB.toInt())
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun button(text: String, onClick: (Button) -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick(this) }
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

    private companion object {
        const val REQ_DICCIONARIO = 41
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

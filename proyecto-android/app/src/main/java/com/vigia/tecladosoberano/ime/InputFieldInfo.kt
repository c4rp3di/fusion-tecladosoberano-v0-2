package com.vigia.tecladosoberano.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Resumen del campo de texto activo. [isPassword] activa el modo seguro:
 * sin sugerencias, sin portapapeles y sin aprendizaje de palabras.
 */
data class InputFieldInfo(
    val isPassword: Boolean,
    val isNumeric: Boolean,
    val suggestionsAllowed: Boolean,
    /** Falso en campos de correo/URL: no se aprenden palabras de ahí. */
    val learnAllowed: Boolean = true
) {
    companion object {
        fun from(info: EditorInfo?): InputFieldInfo {
            val type = info?.inputType ?: InputType.TYPE_NULL
            val cls = type and InputType.TYPE_MASK_CLASS
            val variation = type and InputType.TYPE_MASK_VARIATION

            val textPassword = cls == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                )
            val numberPassword = cls == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            val isPassword = textPassword || numberPassword

            val isNumeric = cls == InputType.TYPE_CLASS_NUMBER ||
                cls == InputType.TYPE_CLASS_PHONE ||
                cls == InputType.TYPE_CLASS_DATETIME

            val noSuggestions = cls == InputType.TYPE_CLASS_TEXT &&
                (type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0

            return InputFieldInfo(
                isPassword = isPassword,
                isNumeric = isNumeric,
                suggestionsAllowed = !isPassword && !noSuggestions && !isNumeric,
                learnAllowed = !isPassword && !noSuggestions && !isNumeric && !(cls == InputType.TYPE_CLASS_TEXT && (
                    variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                        variation == InputType.TYPE_TEXT_VARIATION_URI ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                    ))
            )
        }
    }
}

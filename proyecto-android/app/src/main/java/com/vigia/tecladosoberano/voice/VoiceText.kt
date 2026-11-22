package com.vigia.tecladosoberano.voice

/**
 * Convierte lo que oye Vosk (palabras en minúscula, sin puntuación) en texto escrito:
 * comandos de voz («coma», «punto», «nueva línea»…), espacios correctos y mayúscula tras punto.
 */
object VoiceText {

    private enum class Tipo { PALABRA, CIERRE, APERTURA, SALTO }

    private class Sig(val texto: String, val tipo: Tipo)

    // Comandos de varias palabras primero (se busca la coincidencia más larga).
    private val comandos: List<Pair<List<String>, Sig>> = listOf(
        listOf("punto", "y", "aparte") to Sig(".\n", Tipo.SALTO),
        listOf("punto", "y", "coma") to Sig(";", Tipo.CIERRE),
        listOf("dos", "puntos") to Sig(":", Tipo.CIERRE),
        listOf("signo", "de", "interrogación") to Sig("?", Tipo.CIERRE),
        listOf("signo", "de", "exclamación") to Sig("!", Tipo.CIERRE),
        listOf("abrir", "interrogación") to Sig("¿", Tipo.APERTURA),
        listOf("cerrar", "interrogación") to Sig("?", Tipo.CIERRE),
        listOf("abrir", "exclamación") to Sig("¡", Tipo.APERTURA),
        listOf("cerrar", "exclamación") to Sig("!", Tipo.CIERRE),
        listOf("abrir", "paréntesis") to Sig("(", Tipo.APERTURA),
        listOf("cerrar", "paréntesis") to Sig(")", Tipo.CIERRE),
        listOf("nueva", "línea") to Sig("\n", Tipo.SALTO),
        listOf("salto", "de", "línea") to Sig("\n", Tipo.SALTO),
        listOf("nuevo", "párrafo") to Sig("\n\n", Tipo.SALTO),
        listOf("puntos", "suspensivos") to Sig("…", Tipo.CIERRE),
        listOf("coma") to Sig(",", Tipo.CIERRE),
        listOf("punto") to Sig(".", Tipo.CIERRE),
        listOf("interrogación") to Sig("?", Tipo.CIERRE),
        listOf("exclamación") to Sig("!", Tipo.CIERRE)
    ).sortedByDescending { it.first.size }

    /**
     * @param oido lo que ha reconocido Vosk.
     * @param antes los últimos caracteres que ya hay antes del cursor (para el espacio inicial y la mayúscula).
     * @param comandosOn interpretar «coma», «punto»…
     * @param mayusculas poner mayúscula al empezar frase.
     */
    fun componer(oido: String, antes: String, comandosOn: Boolean, mayusculas: Boolean): String {
        val palabras = oido.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (palabras.isEmpty()) return ""
        val piezas = ArrayList<Sig>()
        var i = 0
        while (i < palabras.size) {
            var hecho = false
            if (comandosOn) {
                for ((frase, sig) in comandos) {
                    if (i + frase.size <= palabras.size && frase.indices.all { palabras[i + it] == frase[it] }) {
                        piezas.add(sig)
                        i += frase.size
                        hecho = true
                        break
                    }
                }
            }
            if (!hecho) {
                piezas.add(Sig(palabras[i], Tipo.PALABRA))
                i++
            }
        }

        val sb = StringBuilder()
        // Estado de lo que hay justo antes de escribir: ¿espacio?, ¿inicio de frase?
        var ultimo: Char? = antes.lastOrNull()
        var inicioFrase = mayusculas && esInicioDeFrase(antes)
        var sinEspacio = ultimo == null || ultimo.isWhitespace() || ultimo == '¿' || ultimo == '¡' || ultimo == '('

        for (p in piezas) {
            when (p.tipo) {
                Tipo.PALABRA -> {
                    if (!sinEspacio) sb.append(' ')
                    sb.append(if (inicioFrase) p.texto.replaceFirstChar { it.uppercaseChar() } else p.texto)
                    inicioFrase = false
                    sinEspacio = false
                }
                Tipo.CIERRE -> {
                    // Pegado a la palabra anterior, sin espacio delante.
                    sb.append(p.texto)
                    sinEspacio = false
                    if (mayusculas && p.texto in listOf(".", "?", "!")) inicioFrase = true
                }
                Tipo.APERTURA -> {
                    if (!sinEspacio) sb.append(' ')
                    sb.append(p.texto)
                    sinEspacio = true
                }
                Tipo.SALTO -> {
                    sb.append(p.texto)
                    sinEspacio = true
                    if (mayusculas && p.texto.startsWith(".")) inicioFrase = true
                    if (mayusculas && p.texto.all { it == '\n' }) inicioFrase = true
                }
            }
        }
        return sb.toString()
    }

    /** Inicio de frase: campo vacío o lo último es . ? ! o salto de línea (con espacios de por medio). */
    private fun esInicioDeFrase(antes: String): Boolean {
        val t = antes.trimEnd(' ')
        if (t.isEmpty()) return antes.isEmpty() || antes.all { it == ' ' }
        val c = t.last()
        return c == '.' || c == '?' || c == '!' || c == '\n'
    }
}

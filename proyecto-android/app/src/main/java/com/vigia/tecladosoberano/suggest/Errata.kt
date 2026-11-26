package com.vigia.tecladosoberano.suggest

/**
 * Detecta palabras que probablemente son una errata de dedo de otra del diccionario:
 * una letra cambiada por una tecla vecina (x↔c, j↔u…), dos letras cambiadas de sitio,
 * una letra de más o una que falta. Sirve para no aprenderlas y para marcarlas al limpiar.
 */
object Errata {

    private const val LETRAS = "abcdefghijklmnopqrstuvwxyzñ"

    /** Teclas vecinas en un QWERTY español (misma fila o filas contiguas, con el desfase real de las filas). */
    private val VECINAS: Map<Char, String> = run {
        val filas = listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm")
        val desfase = floatArrayOf(0f, 0.25f, 0.75f)
        class T(val c: Char, val x: Float, val y: Int)
        val teclas = ArrayList<T>()
        filas.forEachIndexed { r, fila -> fila.forEachIndexed { i, c -> teclas.add(T(c, i + desfase[r], r)) } }
        teclas.associate { a ->
            a.c to teclas.filter { b ->
                b !== a && kotlin.math.abs(a.y - b.y) <= 1 && kotlin.math.abs(a.x - b.x) <= 1.1f
            }.map { it.c }.joinToString("")
        }
    }

    fun sospechosa(dic: Dictionary, palabra: String): Boolean {
        val n = Dictionary.normalizar(palabra)
        if (n.length < 3 || dic.contiene(n)) return false

        // Una letra cambiada por una tecla vecina.
        for (i in n.indices) {
            val vecinas = VECINAS[n[i]] ?: continue
            for (v in vecinas) {
                if (dic.contiene(n.substring(0, i) + v + n.substring(i + 1))) return true
            }
        }
        // Dos letras seguidas cambiadas de sitio.
        for (i in 0 until n.length - 1) {
            if (n[i] == n[i + 1]) continue
            val c = n.substring(0, i) + n[i + 1] + n[i] + n.substring(i + 2)
            if (dic.contiene(c)) return true
        }
        // Una letra de más.
        for (i in n.indices) {
            val c = n.removeRange(i, i + 1)
            if (c.length >= 3 && dic.contiene(c)) return true
        }
        // Una letra que falta.
        for (i in 0..n.length) {
            for (l in LETRAS) {
                if (dic.contiene(n.substring(0, i) + l + n.substring(i))) return true
            }
        }
        return false
    }
}

package com.vigia.tecladosoberano.suggest

/** Una sugerencia: lo que se ve y lo que se escribe (difieren en los atajos). */
class Sug(val mostrar: String, val insertar: String)

/**
 * Sugiere palabras: atajos del usuario, completar por prefijo (sin importar tildes), corregir erratas
 * y predecir la siguiente palabra. Todo local.
 *
 * Orden al completar: palabras aprendidas con 2+ usos, diccionario por frecuencia (subiendo las más
 * usadas si la persona lo permite y las que siguen a la palabra anterior), aprendidas de 1 uso.
 */
class Predictor(
    private val dictionary: Dictionary,
    private val store: WordStore,
    private val atajos: ShortcutStore,
    private val pares: BigramStore,
    private val bloqueadas: BlockStore
) {
    /** Se activa desde los ajustes. */
    var usarFrecuentes = false
    var usarPares = false

    fun suggest(prefijo: String, previa: String?, max: Int = 3): List<Sug> {
        val salida = ArrayList<Sug>(max)
        if (prefijo.isEmpty()) {
            if (previa != null) for (w in siguientes(previa, max)) if (!bloqueadas.bloqueada(w)) salida.add(Sug(w, w))
            return salida
        }
        val p = Dictionary.normalizar(prefijo)

        // 1) Atajo exacto del usuario
        atajos.get(prefijo)?.let { texto ->
            salida.add(Sug("⚡ " + texto.replace('\n', ' '), texto))
        }

        // 2) Completar por prefijo
        class Cand(val palabra: String, val puntos: Int)
        val cands = ArrayList<Cand>()
        val rango = dictionary.rangoPrefijo(p)
        val bonusPrevia = if (previa != null && usarPares) siguientes(previa, 8) else emptyList()
        for (pos in rango) {
            val idx = dictionary.indiceEn(pos)
            val w = dictionary.words[idx]
            var puntos = idx
            if (usarFrecuentes) puntos -= minOf(store.uso(w), 50) * 40
            val k = bonusPrevia.indexOf(w.lowercase())
            if (k >= 0) puntos -= 600 - k * 40
            cands.add(Cand(w, puntos))
        }
        // Palabras aprendidas que no están en el diccionario
        for ((w, n) in store.conPrefijo(p)) {
            if (dictionary.contiene(w)) continue
            cands.add(Cand(w, if (n >= 2) -200 - n else 5000 - n))
        }
        cands.sortBy { it.puntos }
        for (c in cands) {
            if (salida.size >= max) break
            anadir(salida, c.palabra, prefijo)
        }

        // 3) Correcciones si la palabra no existe y quedan huecos
        if (salida.size < max && prefijo.length >= 3 && !dictionary.contiene(prefijo) && store.uso(prefijo) == 0) {
            for (w in corregir(p, max - salida.size)) anadir(salida, w, prefijo)
        }
        return salida
    }

    /** Probabilidad de la siguiente letra tras [prefijo], para el «imán» del teclado. */
    fun siguientesLetras(prefijo: String): Map<Char, Float>? {
        if (prefijo.isEmpty()) return null
        val p = Dictionary.normalizar(prefijo)
        val rango = dictionary.rangoPrefijo(p)
        if (rango.isEmpty()) return null
        val pesos = HashMap<Char, Float>()
        var total = 0f
        for (pos in rango) {
            val idx = dictionary.indiceEn(pos)
            val n = dictionary.norms[idx]
            if (n.length <= p.length) continue
            val peso = 1f / (50f + idx)
            val c = n[p.length]
            pesos[c] = (pesos[c] ?: 0f) + peso
            total += peso
        }
        if (total <= 0f) return null
        for (k in pesos.keys.toList()) pesos[k] = pesos[k]!! / total
        return pesos
    }

    /**
     * Corrección automática: solo si la palabra está toda en minúsculas, no existe en el diccionario ni entre
     * las aprendidas, y hay una palabra a una sola letra de distancia (la más frecuente). Si no, null.
     */
    fun autocorreccion(palabra: String): String? {
        if (palabra.length < 3 || palabra != palabra.lowercase()) return null
        if (dictionary.contiene(palabra) || store.uso(palabra) > 0) return null
        val n = Dictionary.normalizar(palabra)
        for (c in corregirDist(n, 1, 3)) {
            if (!bloqueadas.bloqueada(c)) return c
        }
        return null
    }

    /** ¿Se parece tanto a una palabra del diccionario (1 letra de diferencia) que probablemente es una errata? */
    fun pareceErrata(palabra: String): Boolean {
        val n = Dictionary.normalizar(palabra)
        if (n.length < 3) return false
        return corregirDist(n, 1, 1).isNotEmpty()
    }

    // ------------------------------------------------------------------ siguiente palabra

    private fun siguientes(previa: String, max: Int): List<String> {
        val pr = previa.lowercase()
        val puntos = LinkedHashMap<String, Int>()
        if (usarPares) {
            for ((w, n) in pares.siguientes(pr)) puntos[w] = (puntos[w] ?: 0) + 100 * n
        }
        val semillas = Semillas.siguientes[pr] ?: emptyList()
        semillas.forEachIndexed { i, w -> puntos[w] = (puntos[w] ?: 0) + 30 - i * 5 }
        return puntos.entries.sortedByDescending { it.value }.take(max).map { it.key }
    }

    // ------------------------------------------------------------------ correcciones

    private fun corregir(pNorm: String, max: Int): List<String> {
        val dist = if (pNorm.length <= 4) 1 else 2
        return corregirDist(pNorm, dist, max)
    }

    private fun corregirDist(pNorm: String, distMax: Int, max: Int): List<String> {
        class C(val w: String, val d: Int, val idx: Int)
        val encontrados = ArrayList<C>()
        // Con la misma letra inicial (lo normal en una errata) para no recorrerlo todo.
        val rango = dictionary.rangoPrefijo(pNorm.substring(0, 1))
        for (pos in rango) {
            val idx = dictionary.indiceEn(pos)
            val n = dictionary.norms[idx]
            if (Math.abs(n.length - pNorm.length) > distMax) continue
            val d = distancia(pNorm, n, distMax)
            if (d in 1..distMax) encontrados.add(C(dictionary.words[idx], d, idx))
        }
        encontrados.sortWith(compareBy({ it.d }, { it.idx }))
        return encontrados.take(max).map { it.w }
    }

    /** Distancia de edición con transposición (Damerau-Levenshtein restringida). Devuelve max+1 si supera [max]. */
    private fun distancia(a: String, b: String, max: Int): Int {
        val n = a.length
        val m = b.length
        if (Math.abs(n - m) > max) return max + 1
        var prev2 = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            cur[0] = i
            var minFila = cur[0]
            for (j in 1..m) {
                val coste = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + coste)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < minFila) minFila = v
            }
            if (minFila > max) return max + 1
            val t = prev2
            prev2 = prev
            prev = cur
            cur = t
        }
        return prev[m]
    }

    // ------------------------------------------------------------------ utilidades

    private fun anadir(lista: MutableList<Sug>, palabra: String, prefijo: String) {
        if (bloqueadas.bloqueada(palabra)) return
        val w = adaptarMayusculas(prefijo, palabra)
        if (w == prefijo) return
        if (lista.none { it.insertar == w }) lista.add(Sug(w, w))
    }

    private fun adaptarMayusculas(prefijo: String, palabra: String): String {
        if (prefijo.length > 1 && prefijo.all { it.isUpperCase() }) return palabra.uppercase()
        if (prefijo[0].isUpperCase()) return palabra.replaceFirstChar { it.uppercase() }
        return palabra
    }
}

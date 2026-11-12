package com.vigia.tecladosoberano.suggest

/** Pares frecuentes del español incluidos de fábrica (no se aprenden de nadie). */
object Semillas {
    val siguientes: Map<String, List<String>> = mapOf(
        "buenos" to listOf("días"),
        "buenas" to listOf("tardes", "noches"),
        "muchas" to listOf("gracias"),
        "muchísimas" to listOf("gracias"),
        "mil" to listOf("gracias"),
        "por" to listOf("favor", "qué", "supuesto"),
        "de" to listOf("nada", "acuerdo", "la"),
        "hasta" to listOf("luego", "mañana", "pronto"),
        "nos" to listOf("vemos", "hablamos"),
        "lo" to listOf("siento", "que", "sé"),
        "me" to listOf("gusta", "encanta", "voy"),
        "no" to listOf("sé", "puedo", "me"),
        "qué" to listOf("tal", "pasa", "hora"),
        "cómo" to listOf("estás", "estáis", "te"),
        "feliz" to listOf("cumpleaños", "navidad", "año"),
        "te" to listOf("quiero", "llamo", "espero"),
        "tengo" to listOf("que", "una", "ganas"),
        "voy" to listOf("a", "para", "de"),
        "está" to listOf("bien", "todo", "en"),
        "todo" to listOf("bien", "el", "lo"),
        "muy" to listOf("bien", "bueno", "bonito"),
        "claro" to listOf("que"),
        "a" to listOf("las", "la", "ver"),
        "en" to listOf("la", "el", "casa"),
        "es" to listOf("que", "muy", "un"),
        "que" to listOf("tal", "no", "me"),
        "con" to listOf("el", "la", "los"),
        "un" to listOf("poco", "momento", "día"),
        "una" to listOf("vez", "hora", "cosa")
    )
}

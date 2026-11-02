package de.foody.domain

/**
 * Anweisungen ans Bild-Sprach-Modell der Fotoerkennung (ADR 0017, Regel 29). Englisch, weil das Modell so trainiert und
 * gemessen ist. Erst fragt die [auswahl] nach einem eigenen Rezept; passt keines, beschreibt das Modell das [GERICHT].
 */
object FotoAnweisung {
    /** Trainingsanweisung des Adapters „food-analysis“; Antwort mit `dish_name`, `nutritional_summary`, `portion_size`. */
    const val GERICHT = "As a food-analyzer AI, analyze the image and return a single JSON object containing nutritional " +
        "information.\n\nRespond with JSON only. No extra text.\n"

    /** Höchstens so viele Rezeptnamen, damit die Anweisung kurz bleibt. */
    const val HOECHSTENS = 200

    private const val AUSWAHL = "Look at this photo of a meal. Which of these dishes is it? Choose exactly one name from " +
        "the list, or \"none\" if none fits. Answer with JSON only: {\"choice\": string, \"portions\": number}\nList: "

    /**
     * Auswahl aus den eigenen Rezepten, die Rezepte aus dem heutigen Plan vorne, jeder Name nur einmal (ohne Rücksicht
     * auf Groß-/Kleinschreibung und Leerzeichen); `null`, wenn es keine Rezepte gibt.
     */
    fun auswahl(kontext: FotoKontext): String? {
        val (plan, uebrige) = kontext.rezepte.partition { it.id in kontext.planRezepte }
        val namen = (plan + uebrige).map { it.name.trim() }.distinctBy { it.lowercase().replace(Regex("""\s+"""), " ") }
        if (namen.isEmpty()) return null
        return AUSWAHL + namen.take(HOECHSTENS).joinToString(", ", "[", "]") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }
    }
}

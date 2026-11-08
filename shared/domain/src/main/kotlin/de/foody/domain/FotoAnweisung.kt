package de.foody.domain

/**
 * Anweisung ans Bild-Sprach-Modell der Fotoerkennung (ADR 0017, Nachtrag 2026-11-08; Regel 29). Englisch, weil das
 * Modell so am verlässlichsten JSON liefert; nur der Gerichtname kommt auf Deutsch.
 */
object FotoAnweisung {
    /**
     * Für Qwen3-VL-4B: Gericht, Zutaten mit Gramm und Nährwerte als JSON. Die Zutatenliste nutzt Foody nicht, mit ihr
     * schätzt das Modell die kcal aber deutlich besser als ohne (Messung 2026-11-08: 91 % statt 73 % plausibel).
     */
    const val GERICHT = "Look at this photo of a meal. Identify the dish and estimate what was served. " +
        "Answer with JSON only, no other text, in exactly this form: " +
        "{\"dish_name\": string, \"ingredients\": [{\"name\": string, \"grams\": number}], " +
        "\"calories_kcal\": number, \"protein_g\": number, \"carbs_g\": number, \"fat_g\": number}. " +
        "Write dish_name in German, as the dish is usually called in Germany."
}

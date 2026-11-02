package de.foody.domain

/** Anweisung ans Bild-Sprach-Modell der Fotoerkennung (ADR 0017, Regel 29). Englisch, weil das Modell so trainiert ist. */
object FotoAnweisung {
    /** Trainingsanweisung des Adapters „food-analysis“; Antwort mit `dish_name`, `nutritional_summary`, `portion_size`. */
    const val GERICHT = "As a food-analyzer AI, analyze the image and return a single JSON object containing nutritional " +
        "information.\n\nRespond with JSON only. No extra text.\n"
}

package de.foody.domain

import kotlin.math.roundToInt

/**
 * Schätzt die Portionenzahl importierter Rezepte – die Exportdateien enthalten keine. Zwei Anhaltspunkte, der
 * größere zählt: das Gewicht der festen Zutaten (Flüssigkeiten wie Brühe oder Bier verkochen zur Soße) und die
 * Energie. Nur Gewicht unterschätzt Rezepte mit trockenen Zutaten (500 g Nudeln roh reichen für 4–5), nur
 * Energie solche mit lückenhaften Nährwerten. Bewusst grob – das Rezept vermerkt die Schätzung zur Korrektur.
 */
object ServingsEstimator {
    enum class Kind(val gramsPerServing: Int, val kcalPerServing: Int, val range: IntRange) {
        MAIN(450, 700, 1..8), SOUP(250, 400, 2..10), DESSERT(150, 350, 2..12), BAKED(100, 400, 4..24)
    }

    private val bakedEndings = listOf(
        "kuchen", "torte", "tarte", "kipferl", "plätzchen", "kekse", "gebäck", "zopf", "brot", "brötchen", "semmeln",
        "teig", "muffins", "schnecken", "stollen", "waffeln", "baguette", "brezen", "brezel", "brezeln",
    )
    private val dessertWords = listOf("tiramisu", "dessert", "mousse", "pudding", "creme", "panna", "trifle")

    fun kindOf(recipeName: String): Kind {
        val words = recipeName.lowercase().split(Regex("""[^\p{L}]+""")).filter { it.isNotEmpty() }
        return when {
            words.any { w -> bakedEndings.any { w.endsWith(it) } } -> Kind.BAKED
            words.any { w -> dessertWords.any { w.startsWith(it) || w.endsWith(it) } } -> Kind.DESSERT
            words.any { it.endsWith("suppe") } -> Kind.SOUP
            else -> Kind.MAIN
        }
    }

    /**
     * Geschätzte Portionen; null, wenn zu wenig bekannt ist (dann bleibt die Vorgabe).
     * [meatPieces]: Fleisch oder Fisch in Stück („4 Rindersteaks“, „2 Entenkeulen“) – bei Hauptgerichten das
     * verlässlichste Signal, denn die Beilagen stehen oft gar nicht im Rezept.
     */
    fun estimate(recipeName: String, solidGrams: Double, kcal: Double, meatPieces: Int? = null): Int? {
        val kind = kindOf(recipeName)
        if (kind == Kind.MAIN && meatPieces != null && meatPieces >= 2) return meatPieces.coerceIn(kind.range)
        if (solidGrams < MIN_KNOWN_GRAMS && kcal < MIN_KNOWN_KCAL) return null
        val servings = maxOf(solidGrams / kind.gramsPerServing, kcal / kind.kcalPerServing)
        return servings.roundToInt().coerceIn(kind.range)
    }

    private const val MIN_KNOWN_GRAMS = 200.0
    private const val MIN_KNOWN_KCAL = 400.0
}

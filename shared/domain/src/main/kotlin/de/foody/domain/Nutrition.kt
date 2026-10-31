package de.foody.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Ergebnis der Nährwertberechnung. [totals] enthält nur Beiträge bekannter Werte;
 * [completeness] gibt je Nährstoff an, welcher Anteil der Zutaten bekannt war (0..1).
 */
data class NutritionResult(
    val totals: Map<Nutrient, BigDecimal>,
    val completeness: Map<Nutrient, Double>,
    val servings: Int,
) {
    fun isComplete(n: Nutrient) = (completeness[n] ?: 0.0) >= 1.0

    fun perServing(n: Nutrient): BigDecimal? {
        if (servings <= 0) return null
        return totals[n]?.divide(BigDecimal(servings), MATH_SCALE, RoundingMode.HALF_UP)
    }

    companion object {
        /** Umrechnung kJ ↔ kcal – die einzige Stelle mit diesem Faktor. */
        val KJ_JE_KCAL = BigDecimal("4.184")

        /** kcal mit einer Nachkommastelle (Zwischenwerte, z. B. Portionen-Schätzung). */
        fun kjToKcal(kj: BigDecimal): BigDecimal = kj.divide(KJ_JE_KCAL, 1, RoundingMode.HALF_UP)

        /**
         * Angezeigte kcal: einmal kaufmännisch auf ganze kcal gerundet. Alle Anzeigen (Rezept, Liste, Planer, Tagebuch)
         * nutzen diese Funktion – über [kjToKcal] erst auf eine Stelle und dann ganz zu runden, ergäbe ±1 (349,46 → 350).
         */
        fun kcal(kj: BigDecimal): Int = kj.divide(KJ_JE_KCAL, 0, RoundingMode.HALF_UP).toInt()

        fun kcalToKj(kcal: BigDecimal): BigDecimal = kcal.multiply(KJ_JE_KCAL)
    }
}

object NutritionCalculator {
    private val HUNDRED = BigDecimal(100)

    /**
     * Beitrag = Nährwert je 100 g (bzw. 100 ml) × verwendete Menge / 100.
     * Optionale Zutaten werden ignoriert, außer [includeOptional] ist gesetzt.
     */
    fun calculate(
        recipe: Recipe,
        ingredients: Map<String, Ingredient>,
        servings: Int = recipe.defaultServings,
        includeOptional: Boolean = false,
    ): NutritionResult {
        val scaled = RecipeScaler.scale(recipe, servings)
        // Menge 0 = „nach Bedarf“ (z. B. Salz): ohne Beitrag und ohne Einfluss auf die Vollständigkeit
        val lines = scaled.ingredients.filter { (includeOptional || !it.optional) && it.amount.signum() > 0 }
        val totals = mutableMapOf<Nutrient, BigDecimal>()
        val known = mutableMapOf<Nutrient, Int>()

        for (line in lines) {
            val ing = ingredients[line.ingredientId]
            val profile = ing?.nutrients
            val basisDimension = when (profile?.basis) {
                NutrientBasis.PER_100_G -> Dimension.MASS
                NutrientBasis.PER_100_ML -> Dimension.VOLUME
                null -> null
            }
            val used = if (ing != null && basisDimension != null) {
                UnitConverter.convert(line.quantity, basisDimension, ing.conversion)
            } else {
                null
            }
            if (profile == null || used == null) continue
            for (n in Nutrient.entries) {
                val per100 = profile.values[n] ?: continue
                // Explizite Skala: Kotlins `/` behielte die Skala des Dividenden (bei 5E+1 → −1) und rundete auf Zehner.
                val contribution = (per100 * used.baseAmount).divide(HUNDRED, MATH_SCALE, RoundingMode.HALF_UP)
                totals[n] = (totals[n] ?: BigDecimal.ZERO) + contribution
                known[n] = (known[n] ?: 0) + 1
            }
        }
        val completeness = Nutrient.entries.associateWith { n ->
            if (lines.isEmpty()) 0.0 else (known[n] ?: 0).toDouble() / lines.size
        }
        return NutritionResult(totals, completeness, servings)
    }
}

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
    val missingIngredientIds: Set<String>,
) {
    val overallCompleteness: Double
        get() = if (completeness.isEmpty()) 0.0 else completeness.values.average()

    fun isComplete(n: Nutrient) = (completeness[n] ?: 0.0) >= 1.0

    fun perServing(n: Nutrient): BigDecimal? {
        if (servings <= 0) return null
        return totals[n]?.divide(BigDecimal(servings), MATH_SCALE, RoundingMode.HALF_UP)
    }

    companion object {
        fun kjToKcal(kj: BigDecimal): BigDecimal = kj.divide(BigDecimal("4.184"), 1, RoundingMode.HALF_UP)
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
        val lines = scaled.ingredients.filter { includeOptional || !it.optional }
        val totals = mutableMapOf<Nutrient, BigDecimal>()
        val known = mutableMapOf<Nutrient, Int>()
        val missing = mutableSetOf<String>()

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
            if (profile == null || used == null) {
                missing += line.ingredientId
                continue
            }
            var anyMissing = false
            for (n in Nutrient.entries) {
                val per100 = profile.values[n]
                if (per100 == null) {
                    anyMissing = true
                    continue
                }
                val contribution = per100 * used.baseAmount / HUNDRED
                totals[n] = (totals[n] ?: BigDecimal.ZERO) + contribution
                known[n] = (known[n] ?: 0) + 1
            }
            if (anyMissing) missing += line.ingredientId
        }
        val completeness = Nutrient.entries.associateWith { n ->
            if (lines.isEmpty()) 0.0 else (known[n] ?: 0).toDouble() / lines.size
        }
        return NutritionResult(totals, completeness, servings, missing)
    }
}

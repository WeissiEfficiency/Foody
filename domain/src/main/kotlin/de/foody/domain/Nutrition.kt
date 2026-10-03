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
    /** Energie (kJ) je Rezeptzeile (ID der Zeile) für die angezeigte Portionenzahl – Menge × Wert je 100 g. */
    val lineEnergyKj: Map<String, BigDecimal> = emptyMap(),
    /** Warum eine Zeile mit Menge nicht eingerechnet werden konnte. */
    val lineGaps: Map<String, LineGap> = emptyMap(),
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

/** Grund, warum eine Zutat mit Menge nicht in die Nährwerte eingeht. */
enum class LineGap {
    /** Für die Zutat sind keine Nährwerte hinterlegt. */
    NO_VALUES,

    /** Menge lässt sich nicht in Gramm/ml umrechnen (z. B. Stück ohne Stückgewicht). */
    NO_WEIGHT,
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
        val missing = mutableSetOf<String>()
        val lineEnergy = mutableMapOf<String, BigDecimal>()
        val gaps = mutableMapOf<String, LineGap>()

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
                gaps[line.id] = if (profile == null || profile.values[Nutrient.ENERGY_KJ] == null) LineGap.NO_VALUES else LineGap.NO_WEIGHT
                continue
            }
            var anyMissing = false
            for (n in Nutrient.entries) {
                val per100 = profile.values[n]
                if (per100 == null) {
                    anyMissing = true
                    continue
                }
                // Explizite Skala: Kotlins `/` behielte die Skala des Dividenden (bei 5E+1 → −1) und rundete auf Zehner.
                val contribution = (per100 * used.baseAmount).divide(HUNDRED, MATH_SCALE, RoundingMode.HALF_UP)
                totals[n] = (totals[n] ?: BigDecimal.ZERO) + contribution
                known[n] = (known[n] ?: 0) + 1
                if (n == Nutrient.ENERGY_KJ) lineEnergy[line.id] = contribution
            }
            if (anyMissing) missing += line.ingredientId
            // Ohne Energiewert fehlt die Zeile in den kcal je Zutat – dann mit Grund statt stillschweigend
            if (profile.values[Nutrient.ENERGY_KJ] == null) gaps[line.id] = LineGap.NO_VALUES
        }
        val completeness = Nutrient.entries.associateWith { n ->
            if (lines.isEmpty()) 0.0 else (known[n] ?: 0).toDouble() / lines.size
        }
        return NutritionResult(totals, completeness, servings, missing, lineEnergy, gaps)
    }
}

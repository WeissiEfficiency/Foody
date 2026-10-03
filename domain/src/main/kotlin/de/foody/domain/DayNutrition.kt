package de.foody.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Nährwerte eines Plantags **pro Person**: Summe der Werte je Portion aller geplanten Mahlzeiten.
 * Die Portionenzahl einer Mahlzeit (Haushaltsgröße) ändert daran nichts – wer zu viert isst, isst trotzdem
 * je eine Portion. [complete] ist false, sobald einer Mahlzeit Energiewerte fehlen; dann ist kcal eine Untergrenze.
 */
data class DayNutrition(val kcal: Int, val protein: Int, val carbs: Int, val fat: Int, val complete: Boolean) {
    companion object {
        fun of(meals: List<Recipe>, ingredients: Map<String, Ingredient>): DayNutrition? {
            if (meals.isEmpty()) return null
            var kj = BigDecimal.ZERO
            var p = BigDecimal.ZERO
            var c = BigDecimal.ZERO
            var f = BigDecimal.ZERO
            var complete = true
            for (recipe in meals) {
                val n = NutritionCalculator.calculate(recipe, ingredients)
                kj += n.perServing(Nutrient.ENERGY_KJ) ?: BigDecimal.ZERO
                p += n.perServing(Nutrient.PROTEIN_G) ?: BigDecimal.ZERO
                c += n.perServing(Nutrient.CARBS_G) ?: BigDecimal.ZERO
                f += n.perServing(Nutrient.FAT_G) ?: BigDecimal.ZERO
                if (!n.isComplete(Nutrient.ENERGY_KJ)) complete = false
            }
            fun BigDecimal.int() = setScale(0, RoundingMode.HALF_UP).toInt()
            return DayNutrition(NutritionResult.kjToKcal(kj).int(), p.int(), c.int(), f.int(), complete)
        }
    }
}

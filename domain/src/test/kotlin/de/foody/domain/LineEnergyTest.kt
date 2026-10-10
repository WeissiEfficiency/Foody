package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/** Energie je Zutat = Menge in Gramm × Energie je 100 g ÷ 100 – sichtbar je Zeile, Lücken mit Grund. */
class LineEnergyTest {
    private fun bd(s: String) = BigDecimal(s)
    private fun profile(kj: String) = NutrientProfile(NutrientBasis.PER_100_G, mapOf(Nutrient.ENERGY_KJ to bd(kj)))

    @Test fun lineEnergyIsAmountTimesValuePer100g() {
        val pork = Ingredient("pork", "Schweinebraten", nutrients = profile("860"))
        val steak = Ingredient("steak", "Rindersteak", ConversionInfo(pieceWeightG = bd("200")), profile("560"))
        val recipe = Recipe("r", "Braten", 4, listOf(
            RecipeIngredient("a", "pork", bd("2"), MeasureUnit.KILOGRAM),
            RecipeIngredient("b", "steak", bd("4"), MeasureUnit.PIECE),
        ))
        val result = NutritionCalculator.calculate(recipe, mapOf("pork" to pork, "steak" to steak), servings = 4)
        // 2000 g × 860 kJ / 100 = 17200 kJ; 4 × 200 g × 560 / 100 = 4480 kJ
        assertEquals(0, bd("17200").compareTo(result.lineEnergyKj.getValue("a")))
        assertEquals(0, bd("4480").compareTo(result.lineEnergyKj.getValue("b")))
        // Halbe Portionenzahl → halbe Menge → halbe Energie je Zeile
        val half = NutritionCalculator.calculate(recipe, mapOf("pork" to pork, "steak" to steak), servings = 2)
        assertEquals(0, bd("8600").compareTo(half.lineEnergyKj.getValue("a")))
    }

    @Test fun lineEnergyPerServingAddsUpToEnergyPerServing() {
        val pork = Ingredient("pork", "Schweinebraten", nutrients = profile("860"))
        val steak = Ingredient("steak", "Rindersteak", ConversionInfo(pieceWeightG = bd("200")), profile("560"))
        val recipe = Recipe("r", "Braten", 4, listOf(
            RecipeIngredient("a", "pork", bd("2"), MeasureUnit.KILOGRAM),
            RecipeIngredient("b", "steak", bd("4"), MeasureUnit.PIECE),
        ))
        val result = NutritionCalculator.calculate(recipe, mapOf("pork" to pork, "steak" to steak), servings = 4)
        // 17200 kJ ÷ 4 = 4300 kJ; 4480 kJ ÷ 4 = 1120 kJ – zusammen genau der Wert je Portion
        assertEquals(0, bd("4300").compareTo(result.lineEnergyPerServingKj("a")))
        assertEquals(0, bd("1120").compareTo(result.lineEnergyPerServingKj("b")))
        assertEquals(0, result.perServing(Nutrient.ENERGY_KJ)!!.compareTo(bd("5420")))
        // Mehr Portionen ändern den Wert je Portion nicht
        val double = NutritionCalculator.calculate(recipe, mapOf("pork" to pork, "steak" to steak), servings = 8)
        assertEquals(0, bd("4300").compareTo(double.lineEnergyPerServingKj("a")))
    }

    @Test fun gapsNameTheReason() {
        val roulade = Ingredient("roulade", "Rinderroulade", nutrients = profile("580")) // kein Stückgewicht
        val beer = Ingredient("beer", "Bier") // keine Nährwerte
        val recipe = Recipe("r", "Rouladen", 4, listOf(
            RecipeIngredient("a", "roulade", bd("8"), MeasureUnit.PIECE),
            RecipeIngredient("b", "beer", bd("1"), MeasureUnit.LITER),
        ))
        val result = NutritionCalculator.calculate(recipe, mapOf("roulade" to roulade, "beer" to beer))
        assertEquals(mapOf("a" to LineGap.NO_WEIGHT, "b" to LineGap.NO_VALUES), result.lineGaps)
    }
}

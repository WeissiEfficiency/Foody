package de.foody.domain

import java.math.BigDecimal
import java.time.LocalDate

enum class NutrientBasis { PER_100_G, PER_100_ML }

enum class Nutrient { ENERGY_KJ, PROTEIN_G, CARBS_G, FAT_G, FIBER_G, SUGAR_G, SALT_G }

/** Nährwerte je Basis. Ein fehlender Eintrag (null) bedeutet „unbekannt“, nicht 0. */
data class NutrientProfile(
    val basis: NutrientBasis,
    val values: Map<Nutrient, BigDecimal?>,
    val source: String = "manuell",
)

data class Ingredient(
    val id: String,
    val name: String,
    val conversion: ConversionInfo = ConversionInfo(),
    val nutrients: NutrientProfile? = null,
    val category: String? = null,
)

data class RecipeIngredient(
    val id: String,
    val ingredientId: String,
    val amount: BigDecimal,
    val unit: MeasureUnit,
    val optional: Boolean = false,
    val note: String? = null,
) {
    val quantity: Quantity get() = Quantity.of(amount, unit)
}

data class Recipe(
    val id: String,
    val name: String,
    val defaultServings: Int,
    val ingredients: List<RecipeIngredient>,
)

data class MealSlot(
    val id: String,
    val date: LocalDate,
    val slotType: String,
    val recipeId: String,
    val servings: Int,
)

data class PantryItem(
    val ingredientId: String,
    val quantity: Quantity,
)

data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(!endInclusive.isBefore(start)) { "Ende liegt vor Start" }
    }

    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(endInclusive)

    val days: List<LocalDate>
        get() = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(endInclusive) }.toList()

    companion object {
        fun ofDays(start: LocalDate, count: Int): DateRange {
            require(count >= 1)
            return DateRange(start, start.plusDays(count.toLong() - 1))
        }
    }
}

package de.foody.domain

import java.math.BigDecimal
import java.math.RoundingMode

object RecipeScaler {
    /** skalierte Menge = Rezeptmenge × geplante Portionen / Standardportionen */
    fun scale(amount: BigDecimal, defaultServings: Int, targetServings: Int): BigDecimal {
        require(defaultServings > 0) { "Standardportionen müssen > 0 sein" }
        require(targetServings >= 0)
        return (amount * BigDecimal(targetServings))
            .divide(BigDecimal(defaultServings), MATH_SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros()
    }

    fun scale(recipe: Recipe, targetServings: Int): Recipe = recipe.copy(
        defaultServings = targetServings,
        ingredients = recipe.ingredients.map {
            it.copy(amount = scale(it.amount, recipe.defaultServings, targetServings))
        },
    )
}

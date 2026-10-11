package de.foody.domain

/**
 * Wie viele Pflichtzutaten fehlen einem Rezept, gemessen am Vorrat?
 *
 * Bewusst ohne Mengen: Vorräte stehen oft in anderen Einheiten als im Rezept („1 Packung“ gegen „200 g“),
 * ein Mengenvergleich würde mehr falsche „fehlt“ liefern als er klärt. Grundzutaten, die praktisch jeder
 * zu Hause hat ([IngredientCatalog.assumedAtHome]), zählen als vorhanden.
 */
object PantryCoverage {
    data class Requirement(val recipeId: String, val ingredientId: String, val ingredientName: String)

    /** Fehlende Pflichtzutaten je Rezept; Rezepte ohne Pflichtzutaten fehlen in der Map (nichts zu bewerten). */
    fun missingByRecipe(requirements: List<Requirement>, inPantry: Set<String>): Map<String, Int> =
        requirements.groupBy { it.recipeId }.mapValues { (_, reqs) ->
            reqs.filterNot { it.ingredientId in inPantry || IngredientCatalog.assumedAtHome(it.ingredientName) }
                .distinctBy { it.ingredientId }
                .size
        }
}

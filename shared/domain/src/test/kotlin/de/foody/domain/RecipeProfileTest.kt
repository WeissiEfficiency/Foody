package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeProfileTest {
    private fun bd(s: String) = BigDecimal(s)
    private fun ing(id: String, name: String, category: String? = null, kj: String? = null, p: String? = null, c: String? = null) =
        Ingredient(
            id, name, category = category,
            nutrients = kj?.let {
                NutrientProfile(NutrientBasis.PER_100_G, mapOf(
                    Nutrient.ENERGY_KJ to bd(it), Nutrient.PROTEIN_G to p?.let(::bd), Nutrient.CARBS_G to c?.let(::bd),
                ))
            },
        )

    private fun recipe(vararg lines: Pair<String, String>) =
        Recipe("r", "Test", 2, lines.mapIndexed { i, (id, g) -> RecipeIngredient("l$i", id, bd(g), MeasureUnit.GRAM) })

    @Test fun dietFromIngredients() {
        val all = listOf(
            ing("tofu", "Tofu", "Kühlregal"), ing("hafer", "Hafermilch", "Kühlregal"), ing("nuss", "Erdnussbutter"),
            ing("mehl", "Weizenmehl"), ing("ei", "Ei", "Kühlregal"), ing("lachs", "Lachs", "Fleisch & Fisch"),
            ing("fond", "Fleischbrühe"), ing("butter", "Butter", "Kühlregal"),
        ).associateBy { it.id }
        fun diets(vararg ids: String) = RecipeProfiles.profile(recipe(*ids.map { it to "100" }.toTypedArray()), all).diets

        assertTrue(Diet.VEGAN in diets("tofu", "hafer", "nuss", "mehl"), "Hafermilch, Erdnussbutter, Weizenmehl sind vegan")
        assertEquals(setOf(Diet.VEGETARIAN), diets("mehl", "ei") - setOf(Diet.LIGHT, Diet.LOW_CARB, Diet.HIGH_PROTEIN))
        assertFalse(Diet.VEGETARIAN in diets("mehl", "lachs"))
        assertFalse(Diet.VEGETARIAN in diets("mehl", "fond"), "Fleischbrühe ist nicht vegetarisch")
        assertFalse(Diet.VEGAN in diets("mehl", "butter"))
    }

    @Test fun meatWordsMatchWholeWordParts() {
        fun meat(name: String) = DietRules.isMeatOrFish(ing("x", name))
        assertFalse(meat("Polenta"), "enthält „ente“, ist aber Maisgrieß")
        assertFalse(meat("Wildkräuter"))
        assertTrue(meat("Entenkeule"))
        assertTrue(meat("Hackfleisch, gemischt"))
        assertTrue(meat("Thunfisch"))
        assertTrue(meat("Rehgulasch"))
    }

    @Test fun nutritionFiltersNeedReliableValues() {
        val all = listOf(
            ing("haehnchen", "Hähnchenbrust", "Fleisch & Fisch", kj = "450", p = "23", c = "0"),
            ing("brokkoli", "Brokkoli", kj = "136", p = "3", c = "2.7"),
            ing("unbekannt", "Geheimzutat"),
        ).associateBy { it.id }
        // 2 Portionen: 400 g Hähnchen + 300 g Brokkoli → je Portion ≈ 50 g Eiweiß, 4 g KH, ≈ 264 kcal
        val good = RecipeProfiles.profile(recipe("haehnchen" to "400", "brokkoli" to "300"), all)
        assertEquals(setOf(Diet.HIGH_PROTEIN, Diet.LOW_CARB, Diet.LIGHT), good.diets)
        assertEquals(264, good.kcalPerServing)

        // Mit einer unbekannten Hauptzutat (2 von 3 bekannt) gilt nichts davon als gesichert
        val shaky = RecipeProfiles.profile(recipe("haehnchen" to "100", "brokkoli" to "100", "unbekannt" to "800"), all)
        assertTrue(shaky.diets.none { it in setOf(Diet.HIGH_PROTEIN, Diet.LOW_CARB, Diet.LIGHT) })
    }
}

package de.foody.domain

import java.math.RoundingMode

/** Filter fürs Entdecken – wie in gängigen Rezept-Apps. */
enum class Diet { VEGETARIAN, VEGAN, HIGH_PROTEIN, LOW_CARB, LIGHT }

/**
 * Kurzprofil eines Rezepts für Liste, Filter und Sortierung: Nährwerte je Portion und Ernährungsform.
 * Nährwert-Filter (eiweißreich, Low Carb, leicht) gelten nur bei ausreichend vollständigen Werten – sonst
 * würde ein Rezept mit fehlenden Zutaten fälschlich als „leicht“ erscheinen.
 */
data class RecipeProfile(
    val kcalPerServing: Int?,
    val proteinPerServing: Int?,
    val carbsPerServing: Int?,
    /** Anteil der Zutaten mit bekannter Energie (0..1). */
    val completeness: Double,
    val diets: Set<Diet>,
) {
    val reliable: Boolean get() = completeness >= RELIABLE

    companion object {
        const val RELIABLE = 0.8
        const val HIGH_PROTEIN_G = 25
        const val LOW_CARB_G = 20
        const val LIGHT_KCAL = 500
    }
}

object RecipeProfiles {
    fun profile(recipe: Recipe, ingredients: Map<String, Ingredient>): RecipeProfile {
        val nutrition = NutritionCalculator.calculate(recipe, ingredients)
        fun per(n: Nutrient) = nutrition.perServing(n)
        val kcal = per(Nutrient.ENERGY_KJ)?.let(NutritionResult::kjToKcal)?.setScale(0, RoundingMode.HALF_UP)?.toInt()
        val protein = per(Nutrient.PROTEIN_G)?.setScale(0, RoundingMode.HALF_UP)?.toInt()
        val carbs = per(Nutrient.CARBS_G)?.setScale(0, RoundingMode.HALF_UP)?.toInt()
        val completeness = nutrition.completeness[Nutrient.ENERGY_KJ] ?: 0.0

        val names = recipe.ingredients.filter { !it.optional }.mapNotNull { ingredients[it.ingredientId] }
        val diets = buildSet {
            if (names.none(DietRules::isMeatOrFish)) {
                add(Diet.VEGETARIAN)
                if (names.none(DietRules::isAnimalProduct)) add(Diet.VEGAN)
            }
            if (completeness >= RecipeProfile.RELIABLE) {
                if ((protein ?: 0) >= RecipeProfile.HIGH_PROTEIN_G) add(Diet.HIGH_PROTEIN)
                if (carbs != null && carbs <= RecipeProfile.LOW_CARB_G) add(Diet.LOW_CARB)
                if (kcal != null && kcal <= RecipeProfile.LIGHT_KCAL) add(Diet.LIGHT)
            }
        }
        return RecipeProfile(kcal, protein, carbs, completeness, diets)
    }
}

/**
 * Ernährungsform über Kategorie und Namen der Zutaten. Bewusst vorsichtig: lieber ein vegetarisches Rezept
 * nicht als solches erkennen als Fleisch übersehen. Pflanzliche Ausnahmen („Hafermilch“, „Erdnussbutter“)
 * werden vor den tierischen Stichwörtern geprüft.
 */
object DietRules {
    private const val MEAT_CATEGORY = "Fleisch & Fisch"
    private val meatWords = listOf(
        "fleisch", "speck", "schinken", "wurst", "würstchen", "salami", "hack", "huhn", "hühner", "hähnchen", "pute",
        "ente", "gans", "lamm", "kalb", "rind", "schwein", "wildfleisch", "wildschwein", "reh", "hirsch", "leber", "steak", "schnitzel",
        "braten", "haxe", "roulade", "lachs", "thunfisch", "fisch", "kabeljau", "garnele", "sardelle", "muschel",
        "gelatine", "fleischbrühe", "rinderfond", "geflügel", "bacon", "chorizo", "salsiccia", "tatar", "gulasch",
    )
    private val plantPrefixes = listOf("hafer", "soja", "mandel", "kokos", "reis", "erdnuss", "nuss", "gemüse", "pilz", "kakao")
    private val animalWords = listOf(
        "milch", "butter", "sahne", "schmand", "crème", "creme", "quark", "joghurt", "käse", "emmentaler", "gouda",
        "feta", "mozzarella", "parmesan", "mascarpone", "camembert", "gorgonzola", "ricotta", "ei", "eier", "eigelb",
        "eiweiß", "honig", "löffelbiskuit", "biskuit", "schmalz",
    )

    fun isMeatOrFish(i: Ingredient): Boolean {
        if (i.category == MEAT_CATEGORY) return true
        val n = i.name.lowercase()
        if (plantPrefixes.any { n.startsWith(it) }) return false
        // Wortanfang oder -ende, nicht irgendwo: sonst wäre „Polenta“ eine Ente
        val words = n.split(Regex("""[^\p{L}]+""")).filter { it.isNotEmpty() }
        return words.any { w -> meatWords.any { w.startsWith(it) || w.endsWith(it) } }
    }

    fun isAnimalProduct(i: Ingredient): Boolean {
        val n = i.name.lowercase()
        if (plantPrefixes.any { n.startsWith(it) }) return false
        val words = n.split(Regex("""[^\p{L}]+""")).filter { it.isNotEmpty() }
        // „Ei“ nur als ganzes Wort – sonst wäre „Weizenmehl“ oder „Eis“ betroffen
        return words.any { w -> w in setOf("ei", "eier") } ||
            animalWords.filter { it.length > 3 }.any { word -> words.any { it.contains(word) } }
    }
}

/** Sortierung der Rezeptliste. */
enum class RecipeSort { NAME, NEWEST, KCAL_ASC, PROTEIN_DESC }

package de.foody.core.data

import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.domain.ConversionInfo
import de.foody.domain.Ingredient
import de.foody.domain.IngredientCatalog
import de.foody.domain.Nutrient
import de.foody.domain.NutrientProfile
import de.foody.domain.Recipe
import de.foody.domain.RecipeIngredient

/*
 * Datenbankzeilen → Modelle aus :domain, wie die v1-Mapper (`Mappers.kt`), damit Nährwerte gleich gerechnet werden.
 */

/** Rezept mit seinen Zeilen in Anzeigereihenfolge; Arbeitsschritte braucht die Rechnung nicht. */
fun RezeptEntity.alsRecipe(zeilen: List<RezeptZutatEntity>): Recipe =
    Recipe(id, name, defaultServings, zeilen.sortedBy { it.sortOrder }.map { it.alsRecipeIngredient() })

fun RezeptZutatEntity.alsRecipeIngredient(): RecipeIngredient =
    RecipeIngredient(id, ingredientId, amount, unit, optional, preparationNote)

fun ZutatEntity.alsIngredient(): Ingredient = Ingredient(
    id = id,
    name = canonicalName,
    category = category,
    // Packungs- und Dosengewichte sind Faustwerte je Zutat aus dem Katalog (kein eigenes Datenbankfeld)
    conversion = ConversionInfo(
        densityGPerMl, pieceWeightG,
        packageWeightG = IngredientCatalog.packageWeightG(canonicalName),
        canWeightG = IngredientCatalog.canWeightG(canonicalName),
    ),
    nutrients = nutrientBasis?.let { basis ->
        NutrientProfile(
            basis = basis,
            values = mapOf(
                Nutrient.ENERGY_KJ to energyKj,
                Nutrient.PROTEIN_G to protein,
                Nutrient.CARBS_G to carbs,
                Nutrient.FAT_G to fat,
                Nutrient.FIBER_G to fiber,
                Nutrient.SUGAR_G to sugar,
                Nutrient.SALT_G to salt,
            ),
            source = nutrientSource ?: "",
        )
    },
)

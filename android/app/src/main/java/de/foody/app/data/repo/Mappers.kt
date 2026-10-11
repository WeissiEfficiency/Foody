package de.foody.app.data.repo

import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.db.MealSlotEntity
import de.foody.app.data.db.PantryItemEntity
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.db.RecipeIngredientEntity
import de.foody.domain.ConversionInfo
import de.foody.domain.Einordnung
import de.foody.domain.Gang
import de.foody.domain.Mahlzeit
import de.foody.domain.RezeptEinordnung
import de.foody.domain.Ingredient
import de.foody.domain.MealSlot
import de.foody.domain.Nutrient
import de.foody.domain.NutrientProfile
import de.foody.domain.PantryItem
import de.foody.domain.Quantity
import de.foody.domain.IngredientCatalog
import de.foody.domain.Recipe
import de.foody.domain.RecipeIngredient
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** Festgelegte oder vermutete Einordnung; wird bei jedem Lesen berechnet, nie gespeichert. */
fun RecipeEntity.einordnung(): Einordnung = RezeptEinordnung.einordnen(
    name, tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }, Mahlzeit.mengeAus(mahlzeiten), Gang.mengeAus(gaenge),
)

fun IngredientEntity.toDomain() = Ingredient(
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

fun RecipeIngredientEntity.toDomain() = RecipeIngredient(id, ingredientId, amount, unit, optional, preparationNote)

fun RecipeEntity.toDomain(lines: List<RecipeIngredientEntity>) =
    Recipe(id, name, defaultServings, lines.sortedBy { it.sortOrder }.map { it.toDomain() })

fun MealSlotEntity.toDomain() = MealSlot(id, date, slotType, recipeId, servings)

fun PantryItemEntity.toDomain() = PantryItem(ingredientId, Quantity.of(amount, unit))

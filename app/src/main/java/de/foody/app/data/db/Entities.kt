package de.foody.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import de.foody.domain.TagebuchArt
import java.math.BigDecimal
import java.time.LocalDate

@Entity(tableName = "ingredient", indices = [Index("canonicalName", unique = true), Index("barcode")])
data class IngredientEntity(
    @PrimaryKey val id: String,
    val canonicalName: String,
    val category: String? = null,
    val densityGPerMl: BigDecimal? = null,
    val pieceWeightG: BigDecimal? = null,
    val nutrientBasis: NutrientBasis? = null,
    val energyKj: BigDecimal? = null,
    val protein: BigDecimal? = null,
    val carbs: BigDecimal? = null,
    val fat: BigDecimal? = null,
    val fiber: BigDecimal? = null,
    val sugar: BigDecimal? = null,
    val salt: BigDecimal? = null,
    val nutrientSource: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int = 1,
    /** Strichcode (EAN/UPC) der Packung, damit ein erneuter Scan die Zutat offline findet. Seit DB v9. */
    val barcode: String? = null,
)

@Entity(tableName = "recipe", indices = [Index("name"), Index("sourceUrl")])
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val defaultServings: Int,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val imageUri: String?,
    val notes: String?,
    val tags: String,
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Int = 1,
    /** Seit DB v2. */
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    /** Quell-URL eines importierten Rezepts; erkennt doppelte Importe. Seit DB v2. */
    val sourceUrl: String? = null,
    /** Eigene Bewertung 1–5, null = unbewertet. Seit DB v3. */
    val rating: Int? = null,
    /** Festgelegte Mahlzeiten (Enum-Namen, kommagetrennt); null = vermuten, "" = bewusst keine. Seit DB v7. */
    val mahlzeiten: String? = null,
    /** Festgelegte Gänge wie [mahlzeiten]. Seit DB v7. */
    val gaenge: String? = null,
)

@Entity(
    tableName = "recipe_ingredient",
    foreignKeys = [
        ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(IngredientEntity::class, ["id"], ["ingredientId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("recipeId"), Index("ingredientId")],
)
data class RecipeIngredientEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val ingredientId: String,
    val amount: BigDecimal,
    val unit: MeasureUnit,
    val sortOrder: Int,
    val preparationNote: String?,
    val optional: Boolean,
)

@Entity(
    tableName = "instruction_step",
    foreignKeys = [ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recipeId")],
)
data class InstructionStepEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int,
    val text: String,
)

@Entity(
    tableName = "meal_slot",
    foreignKeys = [ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("date"), Index("recipeId")],
)
data class MealSlotEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val slotType: String,
    val recipeId: String,
    val servings: Int,
    val cookedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "pantry_item",
    foreignKeys = [ForeignKey(IngredientEntity::class, ["id"], ["ingredientId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("ingredientId")],
)
data class PantryItemEntity(
    @PrimaryKey val id: String,
    val ingredientId: String,
    val amount: BigDecimal,
    val unit: MeasureUnit,
    val bestBeforeDate: LocalDate?,
    val updatedAt: Long,
)

@Entity(tableName = "shopping_list")
data class ShoppingListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rangeStart: LocalDate? = null,
    val rangeEnd: LocalDate? = null,
    val generationVersion: Int = 1,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "shopping_item",
    foreignKeys = [ForeignKey(ShoppingListEntity::class, ["id"], ["listId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("listId")],
)
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    /** null bei manuellen Freitexteinträgen. */
    val ingredientId: String? = null,
    /** Snapshot des Namens, damit spätere Umbenennungen die Liste nicht verändern. */
    val name: String,
    /** Menge in [unit]; null = ohne Mengenangabe. */
    val amount: BigDecimal? = null,
    val unit: MeasureUnit? = null,
    val checked: Boolean = false,
    val manual: Boolean = false,
    val category: String? = null,
    val sortOrder: Int,
    /** Eigene Angabe zum Artikel („500 g“, „Bio“, „reif“) – wie die Spezifikation in Einkaufs-Apps. Seit DB v3. */
    val note: String? = null,
    /** Zeitpunkt der letzten Änderung (Epoch-ms), von Triggern gepflegt; 0 = nie. Seit DB v4. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    /** Zeitpunkt der letzten Änderung von [checked] (Epoch-ms), von Triggern gepflegt. Seit DB v4. */
    @ColumnInfo(defaultValue = "0") val checkedChangedAt: Long = 0,
)

@Entity(
    tableName = "shopping_item_source",
    foreignKeys = [ForeignKey(ShoppingItemEntity::class, ["id"], ["shoppingItemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("shoppingItemId")],
)
data class ShoppingItemSourceEntity(
    @PrimaryKey val id: String,
    val shoppingItemId: String,
    val mealSlotId: String,
    val recipeIngredientId: String,
    val recipeName: String,
    val date: LocalDate,
    val contributedAmount: BigDecimal,
    val unit: MeasureUnit,
)

/**
 * Tagebuch-Eintrag (seit DB v8): was ein Mensch des Haushalts gegessen hat, gerechnet pro Person.
 * Nährwerte sind beim Eintragen festgehalten; `rezeptId`, `zutatId` und `planEintragId` sind lose Referenzen
 * ohne Fremdschlüssel, damit Löschen von Rezept, Zutat oder Plan-Eintrag den Verlauf nie verändert.
 */
@Entity(tableName = "tagebuch_eintrag", indices = [Index("datum"), Index("planEintragId")])
data class TagebuchEintragEntity(
    @PrimaryKey val id: String,
    val datum: LocalDate,
    /** `Mahlzeit`-Enum-Name. */
    val mahlzeit: String,
    val art: TagebuchArt,
    val name: String,
    val rezeptId: String? = null,
    val planEintragId: String? = null,
    val portionen: BigDecimal? = null,
    val zutatId: String? = null,
    val menge: BigDecimal? = null,
    val einheit: MeasureUnit? = null,
    val energieKj: BigDecimal? = null,
    val eiweiss: BigDecimal? = null,
    val kohlenhydrate: BigDecimal? = null,
    val fett: BigDecimal? = null,
    val vollstaendig: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

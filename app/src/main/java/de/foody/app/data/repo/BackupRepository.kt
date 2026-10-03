package de.foody.app.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.db.InstructionStepEntity
import de.foody.app.data.db.MealSlotEntity
import de.foody.app.data.db.PantryItemEntity
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.db.RecipeIngredientEntity
import de.foody.app.data.db.ShoppingItemEntity
import de.foody.app.data.db.ShoppingItemSourceEntity
import de.foody.app.data.db.ShoppingListEntity
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JSON-Sicherung aller lokalen Daten. Zahlen als Strings (verlustfrei), Datumswerte als ISO-8601.
 */
@Serializable
data class BackupDto(
    val formatVersion: Int = 1,
    val ingredients: List<Ingredient>,
    val recipes: List<Recipe>,
    val recipeIngredients: List<RecipeIngredient>,
    val steps: List<Step>,
    val mealSlots: List<Slot>,
    val pantry: List<Pantry>,
    val shoppingLists: List<ShopList>,
    val shoppingItems: List<ShopItem>,
    val shoppingSources: List<ShopSource>,
) {
    @Serializable data class Ingredient(
        val id: String, val name: String, val category: String?, val density: String?, val pieceWeight: String?,
        val basis: String?, val energyKj: String?, val protein: String?, val carbs: String?, val fat: String?,
        val fiber: String?, val sugar: String?, val salt: String?, val source: String?, val createdAt: Long, val updatedAt: Long,
    )
    @Serializable data class Recipe(
        val id: String, val name: String, val servings: Int, val prep: Int?, val cook: Int?, val imageUri: String?,
        val notes: String?, val tags: String, val archivedAt: Long?, val createdAt: Long, val updatedAt: Long,
        // Seit DB v2; Standardwerte halten ältere Sicherungen lesbar
        val favorite: Boolean = false, val sourceUrl: String? = null,
    )
    @Serializable data class RecipeIngredient(
        val id: String, val recipeId: String, val ingredientId: String, val amount: String, val unit: String,
        val sortOrder: Int, val note: String?, val optional: Boolean,
    )
    @Serializable data class Step(val id: String, val recipeId: String, val position: Int, val text: String)
    @Serializable data class Slot(
        val id: String, val date: String, val slotType: String, val recipeId: String, val servings: Int,
        val cookedAt: Long?, val createdAt: Long, val updatedAt: Long,
    )
    @Serializable data class Pantry(
        val id: String, val ingredientId: String, val amount: String, val unit: String, val bestBefore: String?, val updatedAt: Long,
    )
    @Serializable data class ShopList(
        val id: String, val name: String, val start: String?, val end: String?, val version: Int, val createdAt: Long, val updatedAt: Long,
    )
    @Serializable data class ShopItem(
        val id: String, val listId: String, val ingredientId: String?, val name: String, val amount: String?, val unit: String?,
        val checked: Boolean, val manual: Boolean, val category: String?, val sortOrder: Int,
    )
    @Serializable data class ShopSource(
        val id: String, val itemId: String, val mealSlotId: String, val recipeIngredientId: String, val recipeName: String,
        val date: String, val amount: String, val unit: String,
    )
}

@Singleton
class BackupRepository @Inject constructor(
    private val db: FoodyDatabase,
    @ApplicationContext private val context: Context,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val dto = buildDto()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.encodeToString(BackupDto.serializer(), dto).toByteArray()) }
            ?: error("Datei nicht beschreibbar")
    }

    /** Ersetzt alle lokalen Daten atomar durch den Inhalt der Datei. */
    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Datei nicht lesbar")
        val dto = json.decodeFromString(BackupDto.serializer(), text)
        require(dto.formatVersion == 1) { "Unbekanntes Format ${dto.formatVersion}" }
        db.withTransaction {
            db.maintenanceDao().clearAll()
            restore(dto)
        }
    }

    suspend fun deleteAll() = db.withTransaction { db.maintenanceDao().clearAll() }

    private suspend fun buildDto(): BackupDto {
        val i = db.ingredientDao(); val r = db.recipeDao(); val m = db.mealPlanDao(); val p = db.pantryDao(); val s = db.shoppingDao()
        fun BigDecimal?.s() = this?.toPlainString()
        return BackupDto(
            ingredients = i.getAll().map {
                BackupDto.Ingredient(it.id, it.canonicalName, it.category, it.densityGPerMl.s(), it.pieceWeightG.s(), it.nutrientBasis?.name,
                    it.energyKj.s(), it.protein.s(), it.carbs.s(), it.fat.s(), it.fiber.s(), it.sugar.s(), it.salt.s(), it.nutrientSource, it.createdAt, it.updatedAt)
            },
            recipes = r.getAll().map {
                BackupDto.Recipe(it.id, it.name, it.defaultServings, it.prepMinutes, it.cookMinutes, it.imageUri, it.notes, it.tags, it.archivedAt, it.createdAt, it.updatedAt, it.favorite, it.sourceUrl)
            },
            recipeIngredients = r.getAllIngredients().map {
                BackupDto.RecipeIngredient(it.id, it.recipeId, it.ingredientId, it.amount.toPlainString(), it.unit.name, it.sortOrder, it.preparationNote, it.optional)
            },
            steps = r.getAllSteps().map { BackupDto.Step(it.id, it.recipeId, it.position, it.text) },
            mealSlots = m.getAll().map { BackupDto.Slot(it.id, it.date.toString(), it.slotType, it.recipeId, it.servings, it.cookedAt, it.createdAt, it.updatedAt) },
            pantry = p.getAll().map { BackupDto.Pantry(it.id, it.ingredientId, it.amount.toPlainString(), it.unit.name, it.bestBeforeDate?.toString(), it.updatedAt) },
            shoppingLists = s.getLists().map { BackupDto.ShopList(it.id, it.name, it.rangeStart?.toString(), it.rangeEnd?.toString(), it.generationVersion, it.createdAt, it.updatedAt) },
            shoppingItems = s.getAllItems().map {
                BackupDto.ShopItem(it.id, it.listId, it.ingredientId, it.name, it.amount.s(), it.unit?.name, it.checked, it.manual, it.category, it.sortOrder)
            },
            shoppingSources = s.getAllSources().map {
                BackupDto.ShopSource(it.id, it.shoppingItemId, it.mealSlotId, it.recipeIngredientId, it.recipeName, it.date.toString(), it.contributedAmount.toPlainString(), it.unit.name)
            },
        )
    }

    private suspend fun restore(d: BackupDto) {
        fun String?.bd() = this?.let(::BigDecimal)
        d.ingredients.forEach {
            db.ingredientDao().upsert(
                IngredientEntity(it.id, it.name, it.category, it.density.bd(), it.pieceWeight.bd(), it.basis?.let(NutrientBasis::valueOf),
                    it.energyKj.bd(), it.protein.bd(), it.carbs.bd(), it.fat.bd(), it.fiber.bd(), it.sugar.bd(), it.salt.bd(), it.source, it.createdAt, it.updatedAt),
            )
        }
        d.recipes.forEach {
            db.recipeDao().upsert(RecipeEntity(it.id, it.name, it.servings, it.prep, it.cook, it.imageUri, it.notes, it.tags, it.archivedAt, it.createdAt, it.updatedAt, favorite = it.favorite, sourceUrl = it.sourceUrl))
        }
        db.recipeDao().insertIngredients(d.recipeIngredients.map {
            RecipeIngredientEntity(it.id, it.recipeId, it.ingredientId, BigDecimal(it.amount), MeasureUnit.valueOf(it.unit), it.sortOrder, it.note, it.optional)
        })
        db.recipeDao().insertSteps(d.steps.map { InstructionStepEntity(it.id, it.recipeId, it.position, it.text) })
        d.mealSlots.forEach {
            db.mealPlanDao().upsert(MealSlotEntity(it.id, LocalDate.parse(it.date), it.slotType, it.recipeId, it.servings, it.cookedAt, it.createdAt, it.updatedAt))
        }
        d.pantry.forEach {
            db.pantryDao().upsert(PantryItemEntity(it.id, it.ingredientId, BigDecimal(it.amount), MeasureUnit.valueOf(it.unit), it.bestBefore?.let(LocalDate::parse), it.updatedAt))
        }
        d.shoppingLists.forEach {
            db.shoppingDao().upsertList(ShoppingListEntity(it.id, it.name, it.start?.let(LocalDate::parse), it.end?.let(LocalDate::parse), it.version, it.createdAt, it.updatedAt))
        }
        db.shoppingDao().insertItems(d.shoppingItems.map {
            ShoppingItemEntity(it.id, it.listId, it.ingredientId, it.name, it.amount.bd(), it.unit?.let(MeasureUnit::valueOf), it.checked, it.manual, it.category, it.sortOrder)
        })
        db.shoppingDao().insertSources(d.shoppingSources.map {
            ShoppingItemSourceEntity(it.id, it.itemId, it.mealSlotId, it.recipeIngredientId, it.recipeName, LocalDate.parse(it.date), BigDecimal(it.amount), MeasureUnit.valueOf(it.unit))
        })
    }
}

package de.foody.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface IngredientDao {
    @Query("SELECT * FROM ingredient ORDER BY canonicalName COLLATE NOCASE")
    fun observeAll(): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredient")
    suspend fun getAll(): List<IngredientEntity>

    @Query("SELECT * FROM ingredient WHERE id = :id")
    suspend fun get(id: String): IngredientEntity?

    @Query("SELECT * FROM ingredient WHERE id IN (:ids)")
    suspend fun getByIds(ids: Collection<String>): List<IngredientEntity>

    @Query("SELECT * FROM ingredient WHERE canonicalName = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): IngredientEntity?

    /** Exakter (binärer) Namenstreffer – passt zum eindeutigen Index auf `canonicalName`. */
    @Query("SELECT * FROM ingredient WHERE canonicalName = :name LIMIT 1")
    suspend fun findByNameExact(name: String): IngredientEntity?

    @Upsert suspend fun upsert(e: IngredientEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(e: List<IngredientEntity>)

    @Query("SELECT COUNT(*) FROM recipe_ingredient WHERE ingredientId = :id")
    suspend fun usageCount(id: String): Int

    @Query("DELETE FROM ingredient WHERE id = :id") suspend fun delete(id: String)

    // Zusammenführen: alle Verweise auf [from] auf [to] umhängen (innerhalb einer Transaktion aufrufen)
    @Query("UPDATE recipe_ingredient SET ingredientId = :to WHERE ingredientId = :from")
    suspend fun repointRecipeLines(from: String, to: String)
    @Query("UPDATE pantry_item SET ingredientId = :to WHERE ingredientId = :from")
    suspend fun repointPantry(from: String, to: String)
    @Query("UPDATE shopping_item SET ingredientId = :to WHERE ingredientId = :from")
    suspend fun repointShoppingItems(from: String, to: String)
}

@Dao
interface RecipeDao {
    @Query(
        """SELECT * FROM recipe
           WHERE (:archived = 1 AND archivedAt IS NOT NULL OR :archived = 0 AND archivedAt IS NULL)
             AND (name LIKE '%' || :query || '%' ESCAPE '!'
                  OR tags LIKE '%' || :query || '%' ESCAPE '!'
                  OR id IN (SELECT ri.recipeId FROM recipe_ingredient ri JOIN ingredient i ON i.id = ri.ingredientId
                            WHERE i.canonicalName LIKE '%' || :query || '%' ESCAPE '!'))
           ORDER BY name COLLATE NOCASE""",
    )
    /** Sucht in Name, Tags und Zutaten. [query] muss für LIKE maskiert sein (siehe RecipeRepository). */
    fun observe(query: String, archived: Boolean): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipe WHERE archivedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<RecipeEntity>>

    /** Alle Rezepte inkl. archivierter – ohne die Such-Unterabfrage über Zutaten. */
    @Query("SELECT * FROM recipe ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipe WHERE id = :id") fun observe(id: String): Flow<RecipeEntity?>
    @Query("SELECT * FROM recipe WHERE id = :id") suspend fun get(id: String): RecipeEntity?
    @Query("SELECT * FROM recipe") suspend fun getAll(): List<RecipeEntity>
    @Query("SELECT * FROM recipe WHERE id IN (:ids)") suspend fun getByIds(ids: Collection<String>): List<RecipeEntity>

    @Query("SELECT * FROM recipe_ingredient WHERE recipeId = :id ORDER BY sortOrder")
    fun observeIngredients(id: String): Flow<List<RecipeIngredientEntity>>
    @Query("SELECT * FROM recipe_ingredient WHERE recipeId = :id ORDER BY sortOrder")
    suspend fun getIngredients(id: String): List<RecipeIngredientEntity>
    @Query("SELECT * FROM recipe_ingredient") suspend fun getAllIngredients(): List<RecipeIngredientEntity>
    @Query("SELECT * FROM recipe_ingredient") fun observeAllIngredients(): Flow<List<RecipeIngredientEntity>>

    /** Pflichtzutaten aller Rezepte mit Namen – für „Aus dem Vorrat kochbar“. */
    @Query(
        "SELECT ri.recipeId, ri.ingredientId, i.canonicalName AS ingredientName FROM recipe_ingredient ri " +
            "JOIN ingredient i ON i.id = ri.ingredientId WHERE ri.optional = 0",
    )
    fun observeRequired(): Flow<List<RequiredIngredientRow>>
    @Query("SELECT * FROM recipe_ingredient WHERE recipeId IN (:recipeIds) ORDER BY sortOrder")
    suspend fun getIngredientsFor(recipeIds: Collection<String>): List<RecipeIngredientEntity>

    @Query("SELECT * FROM instruction_step WHERE recipeId = :id ORDER BY position")
    fun observeSteps(id: String): Flow<List<InstructionStepEntity>>
    @Query("SELECT * FROM instruction_step WHERE recipeId = :id ORDER BY position")
    suspend fun getSteps(id: String): List<InstructionStepEntity>
    @Query("SELECT * FROM instruction_step") suspend fun getAllSteps(): List<InstructionStepEntity>

    @Upsert suspend fun upsert(r: RecipeEntity)
    @Insert suspend fun insertIngredients(l: List<RecipeIngredientEntity>)
    @Insert suspend fun insertSteps(l: List<InstructionStepEntity>)
    @Query("DELETE FROM recipe_ingredient WHERE recipeId = :id") suspend fun deleteIngredients(id: String)
    @Query("DELETE FROM instruction_step WHERE recipeId = :id") suspend fun deleteSteps(id: String)

    @Transaction
    suspend fun save(r: RecipeEntity, ingredients: List<RecipeIngredientEntity>, steps: List<InstructionStepEntity>) {
        upsert(r)
        deleteIngredients(r.id)
        deleteSteps(r.id)
        insertIngredients(ingredients)
        insertSteps(steps)
    }

    /**
     * Liest das gespeicherte Rezept und schreibt das Ergebnis von [build] in derselben Transaktion; so überschreibt
     * Speichern kein Foto, das ein Sync zwischenzeitlich verlinkt hat.
     */
    @Transaction
    suspend fun saveBuilt(
        id: String?,
        build: (RecipeEntity?) -> Triple<RecipeEntity, List<RecipeIngredientEntity>, List<InstructionStepEntity>>,
    ): String {
        val (entity, ingredients, steps) = build(id?.let { get(it) })
        save(entity, ingredients, steps)
        return entity.id
    }

    @Query("SELECT COUNT(*) FROM recipe WHERE imageUri = :uri")
    suspend fun countByImage(uri: String): Int

    @Query("UPDATE recipe SET imageUri = :uri, updatedAt = :now WHERE id = :id")
    suspend fun setImage(id: String, uri: String?, now: Long)

    @Query("SELECT * FROM recipe WHERE sourceUrl = :url LIMIT 1")
    suspend fun findBySourceUrl(url: String): RecipeEntity?

    @Query("UPDATE recipe SET favorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, now: Long)

    @Query("UPDATE recipe SET rating = :rating, updatedAt = :now WHERE id = :id")
    suspend fun setRating(id: String, rating: Int?, now: Long)

    @Query("UPDATE recipe SET archivedAt = :at, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: String, at: Long?, now: Long)

    @Query("DELETE FROM recipe WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface MealPlanDao {
    /** Wie oft ein Rezept im Planer als „gekocht“ bestätigt wurde. */
    @Query("SELECT recipeId, COUNT(*) AS count FROM meal_slot WHERE cookedAt IS NOT NULL GROUP BY recipeId")
    fun observeCookedCounts(): Flow<List<CookedCount>>

    @Query("SELECT * FROM meal_slot WHERE date BETWEEN :start AND :end ORDER BY date, slotType")
    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<MealSlotEntity>>

    @Query("SELECT * FROM meal_slot WHERE date BETWEEN :start AND :end ORDER BY date, slotType")
    suspend fun getRange(start: LocalDate, end: LocalDate): List<MealSlotEntity>

    @Query("SELECT * FROM meal_slot") suspend fun getAll(): List<MealSlotEntity>
    @Query("SELECT * FROM meal_slot WHERE id = :id") suspend fun get(id: String): MealSlotEntity?
    @Query("SELECT * FROM meal_slot WHERE id IN (:ids)") suspend fun getByIds(ids: Collection<String>): List<MealSlotEntity>

    @Upsert suspend fun upsert(s: MealSlotEntity)
    @Query("DELETE FROM meal_slot WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry_item") fun observeAll(): Flow<List<PantryItemEntity>>
    @Query("SELECT * FROM pantry_item") suspend fun getAll(): List<PantryItemEntity>
    @Query("SELECT * FROM pantry_item WHERE id = :id") suspend fun get(id: String): PantryItemEntity?
    @Upsert suspend fun upsert(p: PantryItemEntity)
    @Query("DELETE FROM pantry_item WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_list ORDER BY createdAt DESC") fun observeLists(): Flow<List<ShoppingListEntity>>
    @Query("SELECT * FROM shopping_list") suspend fun getLists(): List<ShoppingListEntity>
    @Query("SELECT * FROM shopping_list WHERE id = :id") suspend fun getList(id: String): ShoppingListEntity?

    @Query("SELECT * FROM shopping_item WHERE listId = :listId ORDER BY checked, category, sortOrder")
    fun observeItems(listId: String): Flow<List<ShoppingItemEntity>>
    @Query("SELECT * FROM shopping_item WHERE id = :id") suspend fun getItem(id: String): ShoppingItemEntity?
    @Query("SELECT * FROM shopping_item WHERE listId = :listId") suspend fun getItems(listId: String): List<ShoppingItemEntity>
    @Query("SELECT * FROM shopping_item") suspend fun getAllItems(): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_item_source WHERE shoppingItemId = :itemId")
    suspend fun getSources(itemId: String): List<ShoppingItemSourceEntity>
    @Query("SELECT * FROM shopping_item_source") suspend fun getAllSources(): List<ShoppingItemSourceEntity>

    @Upsert suspend fun upsertList(l: ShoppingListEntity)
    @Upsert suspend fun upsertItem(i: ShoppingItemEntity)
    @Insert suspend fun insertItems(i: List<ShoppingItemEntity>)
    @Insert suspend fun insertSources(s: List<ShoppingItemSourceEntity>)
    @Query("DELETE FROM shopping_item_source WHERE shoppingItemId = :itemId") suspend fun deleteSources(itemId: String)
    @Query("UPDATE shopping_item SET checked = :checked WHERE id = :id") suspend fun setChecked(id: String, checked: Boolean)
    @Query("UPDATE shopping_item SET note = :note WHERE id = :id") suspend fun setNote(id: String, note: String?)
    @Query("DELETE FROM shopping_item WHERE id = :id") suspend fun deleteItem(id: String)
    @Query("DELETE FROM shopping_list WHERE id = :id") suspend fun deleteList(id: String)

    @Transaction
    suspend fun insertSnapshot(list: ShoppingListEntity, items: List<ShoppingItemEntity>, sources: List<ShoppingItemSourceEntity>) {
        upsertList(list)
        insertItems(items)
        insertSources(sources)
    }
}

@Dao
interface MaintenanceDao {
    @Query("DELETE FROM shopping_item_source") suspend fun clearSources()
    @Query("DELETE FROM shopping_item") suspend fun clearItems()
    @Query("DELETE FROM shopping_list") suspend fun clearLists()
    @Query("DELETE FROM pantry_item") suspend fun clearPantry()
    @Query("DELETE FROM meal_slot") suspend fun clearSlots()
    @Query("DELETE FROM instruction_step") suspend fun clearSteps()
    @Query("DELETE FROM recipe_ingredient") suspend fun clearRecipeIngredients()
    @Query("DELETE FROM recipe") suspend fun clearRecipes()
    @Query("DELETE FROM ingredient") suspend fun clearIngredients()
    @Query("DELETE FROM tagebuch_eintrag") suspend fun clearTagebuch()

    /** Löscht alle Daten in FK-sicherer Reihenfolge; innerhalb einer Transaktion aufrufen. */
    suspend fun clearAll() {
        clearTagebuch(); clearSources(); clearItems(); clearLists(); clearPantry(); clearSlots()
        clearSteps(); clearRecipeIngredients(); clearRecipes(); clearIngredients()
    }
}

data class RequiredIngredientRow(val recipeId: String, val ingredientId: String, val ingredientName: String)

data class CookedCount(val recipeId: String, val count: Int)

@Dao
interface TagebuchDao {
    @Query("SELECT * FROM tagebuch_eintrag WHERE datum BETWEEN :start AND :end ORDER BY datum, createdAt")
    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<TagebuchEintragEntity>>

    @Query("SELECT * FROM tagebuch_eintrag WHERE id = :id") suspend fun get(id: String): TagebuchEintragEntity?
    @Query("SELECT * FROM tagebuch_eintrag") suspend fun getAll(): List<TagebuchEintragEntity>
    @Upsert suspend fun upsert(e: TagebuchEintragEntity)
    @Query("DELETE FROM tagebuch_eintrag WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT DISTINCT planEintragId FROM tagebuch_eintrag WHERE planEintragId IS NOT NULL")
    fun observeUebernommenePlanIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM tagebuch_eintrag WHERE planEintragId = :planEintragId)")
    suspend fun existsForPlan(planEintragId: String): Boolean
}

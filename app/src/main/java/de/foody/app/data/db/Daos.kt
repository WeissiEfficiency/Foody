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

    @Query("SELECT * FROM ingredient WHERE canonicalName = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): IngredientEntity?

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

    @Query("SELECT * FROM recipe WHERE id = :id") fun observe(id: String): Flow<RecipeEntity?>
    @Query("SELECT * FROM recipe WHERE id = :id") suspend fun get(id: String): RecipeEntity?
    @Query("SELECT * FROM recipe") suspend fun getAll(): List<RecipeEntity>

    @Query("SELECT * FROM recipe_ingredient WHERE recipeId = :id ORDER BY sortOrder")
    fun observeIngredients(id: String): Flow<List<RecipeIngredientEntity>>
    @Query("SELECT * FROM recipe_ingredient WHERE recipeId = :id ORDER BY sortOrder")
    suspend fun getIngredients(id: String): List<RecipeIngredientEntity>
    @Query("SELECT * FROM recipe_ingredient") suspend fun getAllIngredients(): List<RecipeIngredientEntity>

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

    @Query("UPDATE recipe SET archivedAt = :at, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: String, at: Long?, now: Long)

    @Query("DELETE FROM recipe WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface MealPlanDao {
    @Query("SELECT * FROM meal_slot WHERE date BETWEEN :start AND :end ORDER BY date, slotType")
    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<MealSlotEntity>>

    @Query("SELECT * FROM meal_slot WHERE date BETWEEN :start AND :end ORDER BY date, slotType")
    suspend fun getRange(start: LocalDate, end: LocalDate): List<MealSlotEntity>

    @Query("SELECT * FROM meal_slot") suspend fun getAll(): List<MealSlotEntity>
    @Query("SELECT * FROM meal_slot WHERE id = :id") suspend fun get(id: String): MealSlotEntity?

    @Upsert suspend fun upsert(s: MealSlotEntity)
    @Query("DELETE FROM meal_slot WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry_item") fun observeAll(): Flow<List<PantryItemEntity>>
    @Query("SELECT * FROM pantry_item") suspend fun getAll(): List<PantryItemEntity>
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

    /** Löscht alle Daten in FK-sicherer Reihenfolge; innerhalb einer Transaktion aufrufen. */
    suspend fun clearAll() {
        clearSources(); clearItems(); clearLists(); clearPantry(); clearSlots()
        clearSteps(); clearRecipeIngredients(); clearRecipes(); clearIngredients()
    }
}

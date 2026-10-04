package de.foody.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.sync.SyncApplier
import de.foody.app.sync.SyncMapper
import de.foody.domain.MeasureUnit
import de.foody.sync.protocol.IngredientPayload
import de.foody.sync.protocol.MealSlotPayload
import de.foody.sync.protocol.PantryItemPayload
import de.foody.sync.protocol.RecipePayload
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.ShoppingItemPayload
import de.foody.sync.protocol.ShoppingListPayload
import de.foody.sync.protocol.SyncRecord
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [SyncApplier]: Server-Datensätze konfliktsicher in die lokale Datenbank übernehmen. */
@RunWith(AndroidJUnit4::class)
class SyncApplierTest {
    private lateinit var db: FoodyDatabase
    private lateinit var applier: SyncApplier

    @Before fun setUp() = runTest {
        db = syncTestDb()
        db.activateSyncForTest()
        applier = SyncApplier(db)
    }

    @After fun tearDown() = db.close()

    private fun rec(type: RecordType, id: String, payload: Any, rev: Long = 1, updatedAt: Long = 100) =
        SyncRecord(id = id, type = type, updatedAt = updatedAt, rev = rev, payload = SyncMapper.toJson(payload))

    private fun gone(type: RecordType, id: String, rev: Long) =
        SyncRecord(id = id, type = type, deleted = true, updatedAt = 100, rev = rev)

    private fun ingredientRec(id: String, name: String, rev: Long = 1) = rec(RecordType.INGREDIENT, id, IngredientPayload(name = name), rev)

    private fun line(id: String, ingredientId: String, order: Int = 0) =
        RecipePayload.Line(id, ingredientId, "1", "GRAM", order, null, false)

    private fun recipePayload(name: String, lines: List<RecipePayload.Line>, steps: List<RecipePayload.Step> = emptyList()) =
        RecipePayload(name = name, servings = 2, tags = "", favorite = false, lines = lines, steps = steps)

    private fun localIngredient(id: String, name: String) = IngredientEntity(id = id, canonicalName = name, createdAt = 0, updatedAt = 0)

    private fun draft(vararg ingredientIds: String) = RecipeDraft(
        id = null, name = "Lokal", defaultServings = 2,
        ingredients = ingredientIds.map { RecipeDraft.Line(it, BigDecimal.ONE, MeasureUnit.GRAM, null, false) },
    )

    @Test fun appliesAllTypesInDependencyOrder() = runTest {
        val records = listOf(
            rec(
                RecordType.SHOPPING_ITEM, "it",
                ShoppingItemPayload(listId = "l", name = "Milch", checked = false, checkedChangedAt = 0, manual = true, sortOrder = 0, sources = emptyList()),
            ),
            rec(RecordType.SHOPPING_LIST, "l", ShoppingListPayload(name = "Liste", version = 1)),
            rec(RecordType.PANTRY_ITEM, "p", PantryItemPayload(ingredientId = "i", amount = "2", unit = "GRAM")),
            rec(RecordType.MEAL_SLOT, "s", MealSlotPayload(date = "2026-01-01", slotType = "DINNER", recipeId = "r", servings = 2)),
            rec(RecordType.RECIPE, "r", recipePayload("Suppe", listOf(line("rl", "i")))),
            ingredientRec("i", "Salz"),
        )
        val result = applier.apply(records, nextCursor = 42)
        assertEquals(6, result.applied)
        assertEquals(0, result.problems)
        assertNotNull(db.ingredientDao().get("i"))
        assertEquals("Suppe", db.recipeDao().get("r")?.name)
        assertNotNull(db.mealPlanDao().get("s"))
        assertNotNull(db.pantryDao().get("p"))
        assertNotNull(db.shoppingDao().getList("l"))
        assertNotNull(db.shoppingDao().getItem("it"))
        assertEquals(emptyList(), db.syncDao().outbox())
        val state = db.syncDao().getState()!!
        assertEquals(42L, state.cursor)
        assertFalse(state.applyingRemote)
        assertNotNull(state.lastSyncAt)
        assertEquals(1L, db.syncDao().revOf("recipe", "r"))
    }

    @Test fun pendingLocalEditWins() = runTest {
        db.ingredientDao().upsert(localIngredient("i", "Salz"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("i"))
        db.syncDao().clearOutbox()
        db.recipeDao().setFavorite(rid, true, 5) // offene lokale Änderung
        assertTrue(db.syncDao().isQueued("recipe", rid))

        val result = applier.apply(listOf(rec(RecordType.RECIPE, rid, recipePayload("Fremd", listOf(line("x", "i"))), rev = 9)), 3)
        assertEquals(1, result.skippedPending)
        assertEquals(0, result.applied)
        assertEquals("Lokal", db.recipeDao().get(rid)?.name)
        assertNull(db.syncDao().revOf("recipe", rid))
    }

    @Test fun remoteUpdateReplacesLinesAndSteps() = runTest {
        db.ingredientDao().upsert(localIngredient("a", "A"))
        db.ingredientDao().upsert(localIngredient("b", "B"))
        db.ingredientDao().upsert(localIngredient("c", "C"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("a", "b", "c"))
        db.syncDao().clearOutbox()

        val payload = recipePayload("Neu", listOf(line("n1", "a")), listOf(RecipePayload.Step("s1", 0, "Eins"), RecipePayload.Step("s2", 1, "Zwei")))
        val result = applier.apply(listOf(rec(RecordType.RECIPE, rid, payload, rev = 4)), 1)
        assertEquals(1, result.applied)
        assertEquals("Neu", db.recipeDao().get(rid)?.name)
        assertEquals(1, db.recipeDao().getIngredients(rid).size)
        assertEquals(2, db.recipeDao().getSteps(rid).size)
        assertEquals(emptyList(), db.syncDao().outbox())
    }

    @Test fun sameNameDifferentCaseMergesIntoRemote() = runTest {
        db.ingredientDao().upsert(localIngredient("L", "zwiebel"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("L"))
        db.pantryDao().upsert(de.foody.app.data.db.PantryItemEntity("p", "L", BigDecimal.ONE, MeasureUnit.GRAM, null, 0))
        db.syncDao().clearOutbox()

        val result = applier.apply(listOf(ingredientRec("R", "Zwiebel", rev = 2)), 7)
        assertEquals(1, result.merged)
        assertNull(db.ingredientDao().get("L"))
        assertNotNull(db.ingredientDao().get("R"))
        assertEquals("R", db.recipeDao().getIngredients(rid).single().ingredientId)
        assertEquals("R", db.pantryDao().get("p")?.ingredientId)
        val outbox = db.syncDao().outbox()
        assertTrue(outbox.any { it.type == "recipe" && it.recordId == rid && !it.deleted })
        assertTrue(outbox.any { it.type == "pantry_item" && it.recordId == "p" && !it.deleted })
        assertTrue(outbox.none { it.recordId == "L" })
        assertFalse(db.syncDao().getState()!!.applyingRemote)
    }

    @Test fun deleteOfReferencedIngredientRevivesIt() = runTest {
        db.ingredientDao().upsert(localIngredient("I", "Salz"))
        RecipeRepository(db.recipeDao()).save(draft("I"))
        db.syncDao().clearOutbox()

        val result = applier.apply(listOf(gone(RecordType.INGREDIENT, "I", rev = 7)), 1)
        assertEquals(1, result.revived)
        assertNotNull(db.ingredientDao().get("I"))
        assertEquals(7L, db.syncDao().revOf("ingredient", "I"))
        val outbox = db.syncDao().outbox()
        assertEquals(listOf("ingredient" to "I"), outbox.map { it.type to it.recordId })
        assertFalse(outbox.single().deleted)
    }

    @Test fun remoteDeleteCascadesWithoutQueueing() = runTest {
        db.ingredientDao().upsert(localIngredient("i", "Salz"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("i"))
        db.mealPlanDao().upsert(
            de.foody.app.data.db.MealSlotEntity("s1", java.time.LocalDate.of(2026, 1, 1), "DINNER", rid, 2, createdAt = 0, updatedAt = 0),
        )
        db.mealPlanDao().upsert(
            de.foody.app.data.db.MealSlotEntity("s2", java.time.LocalDate.of(2026, 1, 2), "DINNER", rid, 2, createdAt = 0, updatedAt = 0),
        )
        db.syncDao().clearOutbox()

        val result = applier.apply(listOf(gone(RecordType.RECIPE, rid, rev = 3)), 1)
        assertEquals(1, result.applied)
        assertNull(db.recipeDao().get(rid))
        assertEquals(emptyList(), db.mealPlanDao().getAll())
        assertEquals(emptyList(), db.syncDao().outbox())
    }

    @Test fun missingReferenceIsRecordedAsProblem() = runTest {
        val slot = rec(RecordType.MEAL_SLOT, "s", MealSlotPayload(date = "2026-01-01", slotType = "DINNER", recipeId = "nope", servings = 2))
        val result = applier.apply(listOf(slot), 1)
        assertEquals(1, result.problems)
        assertNull(db.mealPlanDao().get("s"))
        assertEquals(listOf(Triple("meal_slot", "s", "missing_reference")), db.syncDao().problems().map { Triple(it.type, it.recordId, it.code) })
    }

    @Test fun remoteCheckStampIsKept() = runTest {
        val list = rec(RecordType.SHOPPING_LIST, "l", ShoppingListPayload(name = "Liste", version = 1))
        val item = rec(
            RecordType.SHOPPING_ITEM, "it",
            ShoppingItemPayload(listId = "l", name = "Milch", checked = true, checkedChangedAt = 555, manual = true, sortOrder = 0, sources = emptyList()),
        )
        applier.apply(listOf(list, item), 1)
        assertEquals(555L, db.shoppingDao().getItem("it")?.checkedChangedAt)
        // auch beim Aktualisieren bleibt der Server-Zeitstempel erhalten
        val again = rec(
            RecordType.SHOPPING_ITEM, "it",
            ShoppingItemPayload(listId = "l", name = "Milch", checked = false, checkedChangedAt = 777, manual = true, sortOrder = 0, sources = emptyList()),
            rev = 2,
        )
        applier.apply(listOf(again), 2)
        assertEquals(777L, db.shoppingDao().getItem("it")?.checkedChangedAt)
        assertEquals(emptyList(), db.syncDao().outbox())
    }

    @Test fun invalidPayloadBecomesProblemOthersApply() = runTest {
        val bad = rec(
            RecordType.RECIPE, "bad",
            recipePayload("Kaputt", listOf(RecipePayload.Line("rl", "i", "1", "BUCKET", 0, null, false))),
        )
        val result = applier.apply(listOf(ingredientRec("i", "Salz"), bad), 5)
        assertNotNull(db.ingredientDao().get("i"))
        assertNull(db.recipeDao().get("bad"))
        assertEquals(1, result.problems)
        assertEquals(1, result.applied)
        assertEquals(listOf(Triple("recipe", "bad", "invalid_payload")), db.syncDao().problems().map { Triple(it.type, it.recordId, it.code) })
        val state = db.syncDao().getState()!!
        assertFalse(state.applyingRemote)
        assertEquals(5L, state.cursor)
    }

    @Test fun pendingMealSlotKeepsRecipeAlive() = runTest {
        db.ingredientDao().upsert(localIngredient("i", "Salz"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("i"))
        db.syncDao().clearOutbox()
        db.mealPlanDao().upsert(
            de.foody.app.data.db.MealSlotEntity("s", java.time.LocalDate.of(2026, 1, 1), "DINNER", rid, 2, createdAt = 0, updatedAt = 0),
        ) // offene lokale Änderung
        val result = applier.apply(listOf(gone(RecordType.RECIPE, rid, rev = 6)), 1)
        assertEquals(1, result.revived)
        assertNotNull(db.recipeDao().get(rid))
        assertNotNull(db.mealPlanDao().get("s"))
        assertEquals(6L, db.syncDao().revOf("recipe", rid))
        assertTrue(db.syncDao().outbox().any { it.type == "recipe" && it.recordId == rid && !it.deleted })
    }

    @Test fun pendingPantryItemKeepsIngredientAlive() = runTest {
        db.ingredientDao().upsert(localIngredient("i", "Salz"))
        db.syncDao().clearOutbox()
        db.pantryDao().upsert(de.foody.app.data.db.PantryItemEntity("p", "i", BigDecimal.ONE, MeasureUnit.GRAM, null, 0))
        val result = applier.apply(listOf(gone(RecordType.INGREDIENT, "i", rev = 8)), 1)
        assertEquals(1, result.revived)
        assertNotNull(db.ingredientDao().get("i"))
        assertNotNull(db.pantryDao().get("p"))
        assertEquals(8L, db.syncDao().revOf("ingredient", "i"))
        assertTrue(db.syncDao().outbox().any { it.type == "ingredient" && it.recordId == "i" && !it.deleted })
    }

    @Test fun pendingShoppingItemKeepsListAlive() = runTest {
        db.shoppingDao().upsertList(de.foody.app.data.db.ShoppingListEntity("l", "Liste", createdAt = 0, updatedAt = 0))
        db.syncDao().clearOutbox()
        db.shoppingDao().upsertItem(de.foody.app.data.db.ShoppingItemEntity(id = "it", listId = "l", name = "Milch", sortOrder = 0))
        val result = applier.apply(listOf(gone(RecordType.SHOPPING_LIST, "l", rev = 5)), 1)
        assertEquals(1, result.revived)
        assertNotNull(db.shoppingDao().getList("l"))
        assertNotNull(db.shoppingDao().getItem("it"))
        assertTrue(db.syncDao().outbox().any { it.type == "shopping_list" && it.recordId == "l" && !it.deleted })
    }

    @Test fun exactNameMatchIsPreferredOverCaseInsensitive() = runTest {
        db.ingredientDao().upsert(localIngredient("X", "zwiebel"))
        db.ingredientDao().upsert(localIngredient("Y", "Zwiebel"))
        val result = applier.apply(listOf(ingredientRec("R", "Zwiebel")), 1)
        assertEquals(1, result.merged)
        assertNull(db.ingredientDao().get("Y"))
        assertNotNull(db.ingredientDao().get("X"))
        assertEquals("Zwiebel", db.ingredientDao().get("R")?.canonicalName)
        assertEquals(0, result.problems)
    }
}

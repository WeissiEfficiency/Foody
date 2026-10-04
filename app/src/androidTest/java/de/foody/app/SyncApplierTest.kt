package de.foody.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import androidx.test.core.app.ApplicationProvider
import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.db.SyncPhotoWantedEntity
import de.foody.app.data.db.SyncProblemEntity
import de.foody.app.data.db.SyncRecordRevEntity
import de.foody.app.sync.PhotoIndex
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
    private lateinit var photos: RecipePhotoStore
    private val files = ArrayList<java.io.File>()

    @Before fun setUp() = runTest {
        db = syncTestDb()
        db.activateSyncForTest()
        photos = RecipePhotoStore(ApplicationProvider.getApplicationContext(), db.recipeDao())
        applier = SyncApplier(db, PhotoIndex(db, photos))
    }

    @After fun tearDown() {
        files.forEach { it.delete() }
        db.close()
    }

    private fun ownPhoto(bytes: ByteArray): Pair<String, String> {
        val f = photos.newPhotoFile().also { it.writeBytes(bytes); files += it }
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return photos.storedUri(f) to sha
    }

    /** Lokales Rezept mit Zutat und gesetztem [imageUri]; Outbox danach leer. */
    private suspend fun recipeWithImage(imageUri: String?): String {
        db.ingredientDao().upsert(localIngredient("i", "Salz"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("i"))
        db.recipeDao().upsert(db.recipeDao().get(rid)!!.copy(imageUri = imageUri))
        db.syncDao().clearOutbox()
        return rid
    }

    private fun photoRec(id: String, photo: String?) =
        rec(RecordType.RECIPE, id, recipePayload("R", listOf(line("l", "i"))).copy(photo = photo), rev = 5)

    @Test fun knownPhotoIsLinked() = runTest {
        val (uri, sha) = ownPhoto(byteArrayOf(9, 8, 7))
        PhotoIndex(db, photos).hashOf(uri) // Cache füllen, wie es der Push täte
        val rid = recipeWithImage(null)
        db.syncDao().upsertPhotoWanted(SyncPhotoWantedEntity(rid, sha))
        applier.apply(listOf(photoRec(rid, sha)), 1)
        assertEquals(uri, db.recipeDao().get(rid)?.imageUri)
        assertTrue(db.syncDao().photosWanted().isEmpty())
        assertEquals(emptyList(), db.syncDao().outbox())
    }

    @Test fun unknownPhotoIsWanted() = runTest {
        val rid = recipeWithImage("content://media/1")
        val sha = "b".repeat(64)
        applier.apply(listOf(photoRec(rid, sha)), 1)
        assertEquals("content://media/1", db.recipeDao().get(rid)?.imageUri)
        assertEquals(listOf(SyncPhotoWantedEntity(rid, sha)), db.syncDao().photosWanted())
    }

    @Test fun nullPhotoKeepsGalleryLink() = runTest {
        val rid = recipeWithImage("content://media/1")
        applier.apply(listOf(photoRec(rid, null)), 1)
        assertEquals("content://media/1", db.recipeDao().get(rid)?.imageUri)
        assertEquals(emptyList(), db.syncDao().outbox())
    }

    @Test fun nullPhotoRemovesOwnPhoto() = runTest {
        val (uri, _) = ownPhoto(byteArrayOf(5, 5))
        val rid = recipeWithImage(uri)
        db.syncDao().upsertPhotoWanted(SyncPhotoWantedEntity(rid, "c".repeat(64)))
        applier.apply(listOf(photoRec(rid, null)), 1)
        assertNull(db.recipeDao().get(rid)?.imageUri)
        assertTrue(db.syncDao().photosWanted().isEmpty())
        assertEquals(emptyList(), db.syncDao().outbox())
    }

    @Test fun knownRevIsSkipped() = runTest {
        val rid = recipeWithImage("content://media/1")
        db.syncDao().setRev(SyncRecordRevEntity("recipe", rid, 5))
        db.syncDao().addProblem(SyncProblemEntity("recipe", rid, "photo_unsyncable", 1))
        val result = applier.apply(listOf(photoRec(rid, null)), 1) // rev 5 = bekannt (eigenes Echo)
        assertEquals(1, result.skippedPending)
        assertEquals(0, result.applied)
        assertEquals(listOf("photo_unsyncable"), db.syncDao().problems().map { it.code })
        // Eine neuere Revision wird angewendet
        val newer = applier.apply(listOf(rec(RecordType.RECIPE, rid, recipePayload("R2", listOf(line("l", "i"))), rev = 6)), 2)
        assertEquals(1, newer.applied)
        assertEquals("R2", db.recipeDao().get(rid)?.name)
    }

    @Test fun nullPhotoFromOthersKeepsUnsyncableOwnPhoto() = runTest {
        val (uri, _) = ownPhoto(byteArrayOf(5, 5))
        val rid = recipeWithImage(uri)
        db.syncDao().addProblem(SyncProblemEntity("recipe", rid, "photo_unsyncable", 1))
        applier.apply(listOf(photoRec(rid, null)), 1) // Bearbeitung eines anderen Geräts, rev 5 neu
        assertEquals(uri, db.recipeDao().get(rid)?.imageUri)
        assertEquals(listOf("photo_unsyncable"), db.syncDao().problems().map { it.code })
        // Mit Foto vom Server: normale Übernahme, Problem weg
        applier.apply(listOf(rec(RecordType.RECIPE, rid, recipePayload("R", listOf(line("l", "i"))).copy(photo = "d".repeat(64)), rev = 6)), 2)
        assertEquals(emptyList(), db.syncDao().problems())
    }

    @Test fun deletedRecipeDropsWish() = runTest {
        val rid = recipeWithImage(null)
        db.syncDao().upsertPhotoWanted(SyncPhotoWantedEntity(rid, "d".repeat(64)))
        applier.apply(listOf(gone(RecordType.RECIPE, rid, 6)), 1)
        assertNull(db.recipeDao().get(rid))
        assertTrue(db.syncDao().photosWanted().isEmpty())
    }

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
        assertEquals(5L, db.syncDao().revOf("shopping_list", "l"))
        assertTrue(db.syncDao().outbox().any { it.type == "shopping_list" && it.recordId == "l" && !it.deleted })
    }

    @Test fun pendingShoppingItemKeepsIngredientAlive() = runTest {
        db.ingredientDao().upsert(localIngredient("i", "Salz"))
        db.shoppingDao().upsertList(de.foody.app.data.db.ShoppingListEntity("l", "Liste", createdAt = 0, updatedAt = 0))
        db.syncDao().clearOutbox()
        db.shoppingDao().upsertItem(
            de.foody.app.data.db.ShoppingItemEntity(id = "it", listId = "l", ingredientId = "i", name = "Salz", sortOrder = 0),
        )
        val result = applier.apply(listOf(gone(RecordType.INGREDIENT, "i", rev = 8)), 1)
        assertEquals(1, result.revived)
        assertNotNull(db.ingredientDao().get("i"))
        assertEquals(8L, db.syncDao().revOf("ingredient", "i"))
        assertTrue(db.syncDao().outbox().any { it.type == "ingredient" && it.recordId == "i" && !it.deleted })
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

    @Test fun writeConstraintErrorOnlySkipsThatRecord() = runTest {
        db.ingredientDao().upsert(localIngredient("i", "Salz"))
        db.recipeDao().upsert(
            de.foody.app.data.db.RecipeEntity(
                id = "A", name = "Lokal", defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = null,
                notes = null, tags = "", archivedAt = null, createdAt = 0, updatedAt = 0,
            ),
        )
        db.recipeDao().insertIngredients(
            listOf(de.foody.app.data.db.RecipeIngredientEntity("x", "A", "i", BigDecimal.ONE, MeasureUnit.GRAM, 0, null, false)),
        )
        db.syncDao().clearOutbox()
        // Rezept B kollidiert beim Schreiben der Zeile "x" mit dem Primaerschluessel von Rezept A
        val records = listOf(
            ingredientRec("C", "Pfeffer"),
            rec(RecordType.RECIPE, "B", recipePayload("Remote", listOf(line("x", "i")))),
        )
        val result = applier.apply(records, nextCursor = 9)
        assertNotNull(db.ingredientDao().get("C"))
        assertNull(db.recipeDao().get("B"))
        assertNull(db.syncDao().revOf("recipe", "B"))
        assertEquals(1, result.problems)
        assertEquals(1, result.applied)
        assertEquals(listOf(Triple("recipe", "B", "apply_failed")), db.syncDao().problems().map { Triple(it.type, it.recordId, it.code) })
        val state = db.syncDao().getState()!!
        assertEquals(9L, state.cursor)
        assertFalse(state.applyingRemote)
        assertEquals("Lokal", db.recipeDao().get("A")?.name)
        assertEquals(listOf("x"), db.recipeDao().getIngredients("A").map { it.id })
        assertEquals(emptyList(), db.syncDao().outbox())
    }

    @Test fun problemIsClearedAfterLaterSuccess() = runTest {
        val slot = rec(RecordType.MEAL_SLOT, "s", MealSlotPayload(date = "2026-01-01", slotType = "DINNER", recipeId = "r", servings = 2))
        applier.apply(listOf(slot), 1)
        assertEquals(1, db.syncDao().problems().size)
        val result = applier.apply(listOf(recipeRec(), slot), 2)
        assertEquals(0, result.problems)
        assertNotNull(db.mealPlanDao().get("s"))
        assertEquals(emptyList(), db.syncDao().problems())
    }

    private fun recipeRec() = rec(RecordType.RECIPE, "r", recipePayload("Suppe", emptyList()))

    @Test fun mergeQueuedRecipeIsStillOverwrittenByServerRecordInSamePull() = runTest {
        db.ingredientDao().upsert(localIngredient("L", "zwiebel"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("L"))
        db.syncDao().clearOutbox()
        val result = applier.apply(
            listOf(
                ingredientRec("R", "Zwiebel", rev = 2),
                rec(RecordType.RECIPE, rid, recipePayload("Vom Server", listOf(line("srv", "R"))), rev = 3),
            ),
            nextCursor = 5,
        )
        assertEquals(0, result.skippedPending)
        assertEquals("Vom Server", db.recipeDao().get(rid)?.name)
    }

    @Test fun selfQueuedExemptionEndsWhenQueuedAtChanged() = runTest {
        db.recipeDao().upsert(
            de.foody.app.data.db.RecipeEntity(
                id = "A", name = "Lokal", defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = null,
                notes = null, tags = "", archivedAt = null, createdAt = 0, updatedAt = 0,
            ),
        )
        val queuedAt = db.syncDao().outbox().single { it.recordId == "A" }.queuedAt
        val key = "recipe" to "A"
        assertFalse(applier.shouldSkipPending("recipe", "A", mapOf(key to queuedAt)))
        // Nutzeränderung nach der Selbst-Vormerkung: anderer Zeitstempel -> echte lokale Änderung
        assertTrue(applier.shouldSkipPending("recipe", "A", mapOf(key to queuedAt - 1)))
        assertTrue(applier.shouldSkipPending("recipe", "A", emptyMap()))
        assertFalse(applier.shouldSkipPending("recipe", "none", emptyMap()))
    }

    @Test fun deletionOfAbsentRecordClearsProblem() = runTest {
        val slot = rec(RecordType.MEAL_SLOT, "s", MealSlotPayload(date = "2026-01-01", slotType = "DINNER", recipeId = "r", servings = 2))
        applier.apply(listOf(slot), 1)
        assertEquals(1, db.syncDao().problems().size)
        applier.apply(listOf(gone(RecordType.MEAL_SLOT, "s", rev = 4)), 2)
        assertEquals(emptyList(), db.syncDao().problems())
    }
}

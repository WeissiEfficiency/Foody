package de.foody.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.db.MealSlotEntity
import de.foody.app.data.db.PantryItemEntity
import de.foody.app.data.db.ShoppingItemEntity
import de.foody.app.data.db.ShoppingListEntity
import de.foody.app.data.db.SyncOutboxEntity
import de.foody.app.data.db.SyncProblemEntity
import de.foody.app.data.db.SyncRecordRevEntity
import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import androidx.test.core.app.ApplicationProvider
import de.foody.app.sync.PhotoIndex
import de.foody.app.sync.SyncLocalStore
import de.foody.domain.MeasureUnit
import de.foody.sync.protocol.PayloadValidator
import de.foody.sync.protocol.RecipePayload
import de.foody.sync.protocol.decode
import de.foody.sync.protocol.RecordType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [SyncLocalStore]: Aktivieren/Deaktivieren und Aufbau der Push-Datensätze aus der Outbox. */
@RunWith(AndroidJUnit4::class)
class SyncLocalStoreTest {
    private lateinit var db: FoodyDatabase
    private lateinit var store: SyncLocalStore
    private lateinit var photos: RecipePhotoStore

    @Before fun setUp() {
        db = syncTestDb()
        photos = RecipePhotoStore(ApplicationProvider.getApplicationContext(), db.recipeDao())
        store = SyncLocalStore(db, PhotoIndex(db, photos))
    }

    @After fun tearDown() = db.close()

    /** Gültige JPEG-Signatur (FF D8 FF) plus [tail]: nur solche Fotos überträgt der Sync. */
    private fun jpegBytes(vararg tail: Int) =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(tail.size) { tail[it].toByte() }

    private suspend fun pendingPhoto(rid: String): String? {
        queue(RecordType.RECIPE, rid)
        val payload = store.pendingRecords().single { it.id == rid }.payload!!
        return (RecordType.RECIPE.decode(payload) as RecipePayload).photo
    }

    @Test fun pendingRecipeCarriesPhotoHash() = runTest {
        db.ingredientDao().upsert(ingredient("i1"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("i1"))
        val file = photos.newPhotoFile().also { it.writeBytes(jpegBytes(1, 2, 3)) }
        try {
            db.recipeDao().upsert(db.recipeDao().get(rid)!!.copy(imageUri = photos.storedUri(file)))
            val expected = java.security.MessageDigest.getInstance("SHA-256").digest(jpegBytes(1, 2, 3))
                .joinToString("") { "%02x".format(it) }
            assertEquals(expected, pendingPhoto(rid))
        } finally {
            file.delete()
        }
    }

    private suspend fun recipeWithImage(uri: String?): String {
        db.ingredientDao().upsert(ingredient("i1"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("i1"))
        db.recipeDao().upsert(db.recipeDao().get(rid)!!.copy(imageUri = uri))
        return rid
    }

    @Test fun pendingRecipeWithWishSendsWantedHash() = runTest {
        val rid = recipeWithImage(null)
        db.syncDao().upsertPhotoWanted(de.foody.app.data.db.SyncPhotoWantedEntity(rid, "e".repeat(64)))
        assertEquals("e".repeat(64), pendingPhoto(rid))
    }

    @Test fun localPhotoChangeDropsWish() = runTest {
        val rid = recipeWithImage(null)
        db.syncDao().upsertPhotoWanted(de.foody.app.data.db.SyncPhotoWantedEntity(rid, "e".repeat(64)))
        val file = photos.newPhotoFile().also { it.writeBytes(jpegBytes(4, 4)) }
        try {
            db.recipeDao().upsert(db.recipeDao().get(rid)!!.copy(imageUri = photos.storedUri(file)))
            assertTrue(db.syncDao().photosWanted().isEmpty())
            val expected = java.security.MessageDigest.getInstance("SHA-256").digest(jpegBytes(4, 4))
                .joinToString("") { "%02x".format(it) }
            assertEquals(expected, pendingPhoto(rid))
        } finally {
            file.delete()
        }
    }

    @Test fun remoteImageChangeKeepsWish() = runTest {
        val rid = recipeWithImage(null)
        db.syncDao().upsertPhotoWanted(de.foody.app.data.db.SyncPhotoWantedEntity(rid, "e".repeat(64)))
        db.syncDao().setApplyingRemote(true)
        db.recipeDao().upsert(db.recipeDao().get(rid)!!.copy(imageUri = "content://x/1"))
        db.syncDao().setApplyingRemote(false)
        assertEquals(1, db.syncDao().photosWanted().size)
    }

    @Test fun missingOwnFileSendsNoPhoto() = runTest {
        val file = photos.newPhotoFile() // existiert nicht
        val rid = recipeWithImage(photos.storedUri(file))
        assertNull(pendingPhoto(rid))
    }

    @Test fun unreadableOwnFileIsDeferred() = runTest {
        val file = photos.newPhotoFile().also { it.writeBytes(jpegBytes(1)) }
        try {
            val rid = recipeWithImage(photos.storedUri(file))
            file.setReadable(false, false)
            org.junit.Assume.assumeFalse("Datei trotzdem lesbar", file.canRead())
            queue(RecordType.RECIPE, rid)
            assertTrue(store.pendingBatch().none { it.record.id == rid })
            assertTrue(db.syncDao().isQueued("recipe", rid))
        } finally {
            file.setReadable(true, false)
            file.delete()
        }
    }

    @Test fun restoreDropsStaleWish() = runTest {
        val rid = recipeWithImage(null)
        db.syncDao().upsertPhotoWanted(de.foody.app.data.db.SyncPhotoWantedEntity(rid, "e".repeat(64)))
        val file = photos.newPhotoFile().also { it.writeBytes(jpegBytes(7, 7)) }
        try {
            // Wiederherstellen aus einer Sicherung: Rezept löschen, mit eigenem Foto neu einfügen
            val old = db.recipeDao().get(rid)!!
            db.recipeDao().delete(rid)
            db.recipeDao().upsert(old.copy(imageUri = photos.storedUri(file)))
            assertTrue(db.syncDao().photosWanted().isEmpty())
            val expected = java.security.MessageDigest.getInstance("SHA-256").digest(jpegBytes(7, 7))
                .joinToString("") { "%02x".format(it) }
            assertEquals(expected, pendingPhoto(rid))
        } finally {
            file.delete()
        }
    }

    @Test fun galleryLinkSendsNoPhoto() = runTest {
        db.ingredientDao().upsert(ingredient("i1"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("i1"))
        db.recipeDao().upsert(db.recipeDao().get(rid)!!.copy(imageUri = "content://media/external/images/1"))
        assertNull(pendingPhoto(rid))
    }

    private fun ingredient(id: String) = IngredientEntity(id = id, canonicalName = "Zutat $id", createdAt = 0, updatedAt = 0)

    private fun draft(vararg ingredientIds: String) = RecipeDraft(
        id = null, name = "Rezept", defaultServings = 2,
        ingredients = ingredientIds.map { RecipeDraft.Line(it, BigDecimal.ONE, MeasureUnit.GRAM, null, false) },
        steps = listOf("Schritt eins"),
    )

    private suspend fun queue(type: RecordType, id: String, deleted: Boolean = false, at: Long = 1) =
        db.syncDao().enqueue(SyncOutboxEntity(type.wire, id, deleted, at))

    private suspend fun seedAll(): String {
        db.ingredientDao().upsert(ingredient("i1"))
        db.ingredientDao().upsert(ingredient("i2"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("i1"))
        db.mealPlanDao().upsert(
            MealSlotEntity("s1", LocalDate.of(2026, 1, 1), "DINNER", rid, 2, createdAt = 0, updatedAt = 0),
        )
        db.pantryDao().upsert(PantryItemEntity("p1", "i1", BigDecimal.ONE, MeasureUnit.GRAM, null, 0))
        db.shoppingDao().upsertList(ShoppingListEntity("l", "Liste", createdAt = 0, updatedAt = 0))
        db.shoppingDao().upsertItem(ShoppingItemEntity(id = "it1", listId = "l", name = "Milch", sortOrder = 0))
        return rid
    }

    @Test fun activateWithUploadQueuesEverything() = runTest {
        seedAll()
        store.activate("http://server", "h1", uploadExisting = true)
        val outbox = db.syncDao().outbox()
        assertEquals(7, outbox.size)
        assertTrue(outbox.none { it.deleted })
        val state = db.syncDao().getState()!!
        assertTrue(state.active)
        assertEquals("http://server", state.serverUrl)
        assertEquals("h1", state.householdId)
        assertEquals(0L, state.cursor)
    }

    @Test fun activateWithoutUploadQueuesNothing() = runTest {
        seedAll()
        store.activate("http://server", "h1", uploadExisting = false)
        assertEquals(emptyList(), db.syncDao().outbox())
        db.ingredientDao().upsert(ingredient("i1").copy(category = "Obst"))
        assertEquals(1, db.syncDao().outbox().size)
    }

    @Test fun pendingRecordsAreInDependencyOrder() = runTest {
        val rid = seedAll()
        queue(RecordType.SHOPPING_ITEM, "it1", at = 1)
        queue(RecordType.RECIPE, rid, at = 2)
        queue(RecordType.INGREDIENT, "i1", at = 3)
        val records = store.pendingRecords()
        assertEquals(listOf(RecordType.INGREDIENT, RecordType.RECIPE, RecordType.SHOPPING_ITEM), records.map { it.type })
        records.forEach { assertNull(PayloadValidator.validate(it), "Payload gültig: $it") }
    }

    @Test fun deletionsComeLastInReverseOrder() = runTest {
        db.ingredientDao().upsert(ingredient("i1"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("i1"))
        db.activateSyncForTest()
        db.recipeDao().delete(rid)
        db.ingredientDao().delete("i1")
        db.shoppingDao().upsertList(ShoppingListEntity("l", "Liste", createdAt = 0, updatedAt = 0))
        val records = store.pendingRecords()
        assertEquals(
            listOf(RecordType.SHOPPING_LIST, RecordType.RECIPE, RecordType.INGREDIENT),
            records.map { it.type },
        )
        assertEquals(listOf(false, true, true), records.map { it.deleted })
        assertNull(records[1].payload)
        assertNull(records[2].payload)
        assertEquals(rid, records[1].id)
    }

    @Test fun baseRevComesFromRecordRev() = runTest {
        db.ingredientDao().upsert(ingredient("i1"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("i1"))
        db.activateSyncForTest()
        db.syncDao().setRev(SyncRecordRevEntity("recipe", rid, 42))
        db.recipeDao().setFavorite(rid, true, 5)
        assertEquals(42L, store.pendingRecords().single().baseRev)

        db.syncDao().clearOutbox()
        db.ingredientDao().upsert(ingredient("i1").copy(category = "Obst"))
        assertNull(store.pendingRecords().single().baseRev)
    }

    @Test fun vanishedRowIsSentAsDeletion() = runTest {
        db.ingredientDao().upsert(ingredient("i1"))
        val rid = RecipeRepository(db.recipeDao()).save(draft("i1"))
        db.activateSyncForTest()
        db.syncDao().setApplyingRemote(true)
        db.recipeDao().delete(rid)
        db.syncDao().setApplyingRemote(false)
        queue(RecordType.RECIPE, rid, deleted = false, at = 77)
        val record = store.pendingRecords().single()
        assertTrue(record.deleted)
        assertEquals(rid, record.id)
        assertNull(record.payload)
        assertEquals(77L, record.updatedAt)
    }

    @Test fun limitIsRespected() = runTest {
        listOf("i1", "i2", "i3").forEach { db.ingredientDao().upsert(ingredient(it)) }
        db.activateSyncForTest()
        listOf("i1", "i2", "i3").forEach { queue(RecordType.INGREDIENT, it) }
        assertEquals(2, store.pendingRecords(limit = 2).size)
        assertEquals(3, db.syncDao().outbox().size, "Outbox bleibt unverändert")
    }

    @Test fun deactivateClearsSyncTables() = runTest {
        seedAll()
        store.activate("http://server", "h1", uploadExisting = false)
        db.ingredientDao().upsert(ingredient("i1").copy(category = "Obst"))
        db.syncDao().setRev(SyncRecordRevEntity("ingredient", "i1", 3))
        db.syncDao().addProblem(SyncProblemEntity("ingredient", "i1", "X", 1))
        store.deactivate()
        assertEquals(emptyList(), db.syncDao().outbox())
        assertNull(db.syncDao().revOf("ingredient", "i1"))
        assertEquals(emptyList(), db.syncDao().problems())
        val state = db.syncDao().getState()!!
        assertFalse(state.active)
        assertNull(state.serverUrl)
        assertNull(state.householdId)
    }
}

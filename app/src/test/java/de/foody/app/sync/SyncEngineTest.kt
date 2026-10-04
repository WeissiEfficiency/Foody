package de.foody.app.sync

import androidx.room.Room
import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.db.SYNC_CALLBACK
import de.foody.app.data.db.ShoppingItemEntity
import de.foody.app.data.db.ShoppingListEntity
import de.foody.app.data.db.SyncPhotoWantedEntity
import de.foody.app.data.db.SyncRecordRevEntity
import de.foody.sync.protocol.MealSlotPayload
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.domain.MeasureUnit
import de.foody.sync.protocol.AuthResponse
import de.foody.sync.protocol.DeviceDto
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.HouseholdDto
import de.foody.sync.protocol.IngredientPayload
import de.foody.sync.protocol.InviteDto
import de.foody.sync.protocol.LoginRequest
import de.foody.sync.protocol.PhotoHash
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.PullResponse
import de.foody.sync.protocol.PushResponse
import de.foody.sync.protocol.PushResult
import de.foody.sync.protocol.PushStatus
import de.foody.sync.protocol.RecipePayload
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.RegisterRequest
import de.foody.sync.protocol.ShoppingItemPayload
import de.foody.sync.protocol.SyncRecord
import de.foody.sync.protocol.decode
import java.math.BigDecimal
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Fake-[SyncApi] mit Protokoll der Aufrufe; Push und Pull lassen sich pro Test belegen. */
private class FakeSyncApi : SyncApi {
    val pushes = ArrayList<List<SyncRecord>>()
    val pulls = ArrayList<Long>()
    var onPush: suspend (List<SyncRecord>) -> PushResponse = { PushResponse(emptyList()) }
    var onPull: suspend (Long) -> PullResponse = { PullResponse(emptyList(), it, false) }

    override suspend fun push(records: List<SyncRecord>): PushResponse {
        pushes += records
        return onPush(records)
    }

    override suspend fun pull(since: Long, limit: Int): PullResponse {
        pulls += since
        return onPull(since)
    }

    override suspend fun login(req: LoginRequest): AuthResponse = error("unused")
    override suspend fun register(req: RegisterRequest): AuthResponse = error("unused")
    override suspend fun households(): List<HouseholdDto> = error("unused")
    override suspend fun createHousehold(name: String): HouseholdDto = error("unused")
    override suspend fun selectHousehold(id: String) = error("unused")
    override suspend fun createInvite(): InviteDto = error("unused")
    override suspend fun devices(): List<DeviceDto> = error("unused")
    override suspend fun revokeDevice(id: String) = error("unused")
    val uploads = ArrayList<Pair<String, ByteArray>>()
    val order = ArrayList<String>()
    var serverPhotos = HashMap<String, ByteArray>()
    var downloads = 0

    override suspend fun photosMissing(hashes: List<String>): List<String> =
        hashes.filter { it !in serverPhotos && uploads.none { u -> u.first == it } }

    override suspend fun uploadPhoto(sha256: String, bytes: ByteArray) {
        order += "upload"
        uploads += sha256 to bytes
    }

    override suspend fun downloadPhoto(sha256: String): ByteArray? {
        downloads++
        return serverPhotos[sha256]
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SyncEngineTest {
    private lateinit var db: FoodyDatabase
    private lateinit var api: FakeSyncApi
    private lateinit var engine: SyncEngine
    private lateinit var photoStore: RecipePhotoStore

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            FoodyDatabase::class.java,
        ).addCallback(FoodyDatabase.SYNC_CALLBACK).allowMainThreadQueries().build()
        api = FakeSyncApi()
        photoStore = RecipePhotoStore(RuntimeEnvironment.getApplication(), db.recipeDao())
        val photoIndex = PhotoIndex(db, photoStore)
        engine = SyncEngine(db, SyncLocalStore(db, photoIndex), SyncApplier(db, photoIndex), api, Clock.systemUTC(), photoIndex, photoStore)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun activate(cursor: Long = 0) {
        val dao = db.syncDao()
        dao.upsertState((dao.getState() ?: error("sync_state fehlt")).copy(active = true, cursor = cursor))
    }

    private fun ingredient(id: String, name: String = "Zutat $id") =
        IngredientEntity(id = id, canonicalName = name, createdAt = 0, updatedAt = 1)

    private fun accepted(r: SyncRecord, rev: Long) = PushResult(r.id, r.type, PushStatus.ACCEPTED, rev = rev)

    private fun ingredientRecord(id: String, name: String, rev: Long) = SyncRecord(
        id = id, type = RecordType.INGREDIENT, updatedAt = 10, rev = rev,
        payload = SyncMapper.toJson(IngredientPayload(name = name)),
    )

    private fun jpeg(vararg tail: Int) =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(tail.size) { tail[it].toByte() }

    private fun ownPhoto(bytes: ByteArray): String {
        val f = photoStore.newPhotoFile().also { it.writeBytes(bytes) }
        return photoStore.storedUri(f)
    }

    /** Lokales Rezept mit Zutat und [imageUri]. */
    private suspend fun recipe(id: String, imageUri: String?): String {
        db.ingredientDao().upsert(ingredient("i", "Salz"))
        val rid = RecipeRepository(db.recipeDao()).save(
            RecipeDraft(
                id = id, name = "R", defaultServings = 2,
                ingredients = listOf(RecipeDraft.Line("i", BigDecimal.ONE, MeasureUnit.GRAM, null, false)),
            ),
        )
        db.recipeDao().upsert(db.recipeDao().get(rid)!!.copy(imageUri = imageUri))
        return rid
    }

    private fun photoRecord(id: String, photo: String?, rev: Long = 5) = SyncRecord(
        id = id, type = RecordType.RECIPE, updatedAt = 50, rev = rev,
        payload = SyncMapper.toJson(
            RecipePayload(
                name = "R", servings = 2, photo = photo, tags = "", favorite = false,
                lines = listOf(RecipePayload.Line("l", "i", "1", "GRAM", 0, null, false)), steps = emptyList(),
            ),
        ),
    )

    @Test
    fun missingPhotosAreUploadedBeforePush() = runTest {
        activate()
        val bytes = jpeg(1, 2, 3)
        val rid = recipe("r1", ownPhoto(bytes))
        api.onPush = { api.order += "push"; PushResponse(it.map { r -> accepted(r, 3) }) }
        val outcome = engine.run()
        assertTrue(outcome is SyncOutcome.Success, outcome.toString())
        assertEquals(listOf(PhotoHash.of(bytes)), api.uploads.map { it.first })
        assertTrue(bytes.contentEquals(api.uploads.single().second))
        assertEquals(listOf("upload", "push"), api.order.take(2))
        val sent = api.pushes.flatten().first { it.id == rid }
        assertEquals(PhotoHash.of(bytes), (RecordType.RECIPE.decode(sent.payload!!) as RecipePayload).photo)
    }

    @Test
    fun uploadFailureKeepsOutboxAndAbortsAsTransient() = runTest {
        activate()
        recipe("r1", ownPhoto(jpeg(4)))
        val failing = object : SyncApi by api {
            override suspend fun uploadPhoto(sha256: String, bytes: ByteArray) = throw SyncApiException.Transient(503, null)
        }
        val photoIndex = PhotoIndex(db, photoStore)
        val e = SyncEngine(db, SyncLocalStore(db, photoIndex), SyncApplier(db, photoIndex), failing, Clock.systemUTC(), photoIndex, photoStore)
        val outcome = e.run()
        assertTrue(outcome is SyncOutcome.Failed && outcome.transient, outcome.toString())
        assertTrue(db.syncDao().outbox().any { it.type == "recipe" })
        assertTrue(api.pushes.isEmpty())
    }

    @Test
    fun wantedPhotoIsDownloadedAndLinkedWithoutOutboxEcho() = runTest {
        activate()
        val bytes = jpeg(7, 7, 7)
        val hash = PhotoHash.of(bytes)
        api.serverPhotos[hash] = bytes
        db.ingredientDao().upsert(ingredient("i", "Salz"))
        db.syncDao().clearOutbox()
        api.onPull = { PullResponse(listOf(photoRecord("r1", hash)), 9, false) }
        val outcome = engine.run()
        assertTrue(outcome is SyncOutcome.Success && outcome.problems == 0, outcome.toString())
        val uri = assertNotNull(db.recipeDao().get("r1")?.imageUri)
        assertTrue(bytes.contentEquals(photoStore.fileOf(uri)!!.readBytes()))
        assertTrue(db.syncDao().photosWanted().isEmpty())
        assertEquals(emptyList(), db.syncDao().outbox())
        assertEquals(hash, PhotoIndex(db, photoStore).hashOf(uri))
        assertEquals(uri, PhotoIndex(db, photoStore).uriFor(hash))
        assertEquals(0, photoStore.fileOf(uri)!!.parentFile!!.listFiles { f -> f.name.endsWith(".tmp") }!!.size)
    }

    @Test
    fun photoNotOnServerStaysWanted() = runTest {
        activate()
        val hash = PhotoHash.of(jpeg(5))
        db.ingredientDao().upsert(ingredient("i", "Salz"))
        db.syncDao().clearOutbox()
        api.onPull = { PullResponse(listOf(photoRecord("r1", hash)), 9, false) }
        assertTrue(engine.run() is SyncOutcome.Success)
        assertNull(db.recipeDao().get("r1")!!.imageUri)
        assertEquals(listOf(hash), db.syncDao().photosWanted().map { it.sha256 })
        // Später ist es da: der nächste Lauf holt es nach
        api.serverPhotos[hash] = jpeg(5)
        api.onPull = { PullResponse(emptyList(), it, false) }
        assertTrue(engine.run() is SyncOutcome.Success)
        assertNotNull(db.recipeDao().get("r1")!!.imageUri)
        assertTrue(db.syncDao().photosWanted().isEmpty())
        assertEquals(emptyList(), db.syncDao().outbox())
    }

    @Test
    fun corruptDownloadIsRejected() = runTest {
        activate()
        val hash = PhotoHash.of(jpeg(1))
        api.serverPhotos[hash] = jpeg(2) // falscher Inhalt
        db.ingredientDao().upsert(ingredient("i", "Salz"))
        db.syncDao().clearOutbox()
        api.onPull = { PullResponse(listOf(photoRecord("r1", hash)), 9, false) }
        assertTrue(engine.run() is SyncOutcome.Success)
        assertNull(db.recipeDao().get("r1")!!.imageUri)
        assertEquals(listOf(hash), db.syncDao().photosWanted().map { it.sha256 }) // Wunsch bleibt
        assertEquals(listOf("photo_mismatch"), db.syncDao().problems().map { it.code })
        api.onPull = { PullResponse(emptyList(), it, false) }
        engine.run()
        assertEquals(1, api.downloads) // kein erneuter Abruf, solange das Problem besteht
        assertEquals(listOf(hash), db.syncDao().photosWanted().map { it.sha256 })
    }

    private fun recipePhoto(pushed: List<SyncRecord>, id: String) =
        (RecordType.RECIPE.decode(pushed.first { it.id == id }.payload!!) as RecipePayload).photo

    @Test
    fun oversizedOwnPhotoIsSentWithoutPhotoAndProblem() = runTest {
        activate()
        val big = jpeg() + ByteArray(Protocol.MAX_PHOTO_BYTES.toInt()) // 10 MB + 3
        val rid = recipe("r1", ownPhoto(big))
        api.onPush = { PushResponse(it.map { r -> accepted(r, 3) }) }
        val outcome = engine.run()
        assertTrue(outcome is SyncOutcome.Success, outcome.toString())
        assertNull(recipePhoto(api.pushes.flatten(), rid))
        assertTrue(api.uploads.isEmpty())
        assertEquals(listOf(rid to "photo_unsyncable"), db.syncDao().problems().map { it.recordId to it.code })
        assertTrue(db.syncDao().outbox().none { it.type == "recipe" })
    }

    @Test
    fun nonJpegOwnPhotoIsSentWithoutPhotoAndProblem() = runTest {
        activate()
        val rid = recipe("r1", ownPhoto(byteArrayOf(1, 2, 3, 4)))
        api.onPush = { PushResponse(it.map { r -> accepted(r, 3) }) }
        assertTrue(engine.run() is SyncOutcome.Success)
        assertNull(recipePhoto(api.pushes.flatten(), rid))
        assertEquals(listOf(rid to "photo_unsyncable"), db.syncDao().problems().map { it.recordId to it.code })
    }

    @Test
    fun uploadClientErrorDefersOnlyThatRecipe() = runTest {
        activate()
        val badBytes = jpeg(6, 6)
        val bad = recipe("r1", ownPhoto(badBytes))
        val good = RecipeRepository(db.recipeDao()).save(
            RecipeDraft(
                id = null, name = "G", defaultServings = 2,
                ingredients = listOf(RecipeDraft.Line("i", BigDecimal.ONE, MeasureUnit.GRAM, null, false)),
            ),
        )
        val goodBytes = jpeg(8, 8)
        db.recipeDao().upsert(db.recipeDao().get(good)!!.copy(imageUri = ownPhoto(goodBytes)))
        val refusing = object : SyncApi by api {
            override suspend fun uploadPhoto(sha256: String, bytes: ByteArray) {
                if (sha256 == PhotoHash.of(badBytes)) throw SyncApiException.ClientError(400, null)
                api.uploadPhoto(sha256, bytes)
            }
        }
        api.onPush = { PushResponse(it.map { r -> accepted(r, 3) }) }
        val photoIndex = PhotoIndex(db, photoStore)
        val e = SyncEngine(db, SyncLocalStore(db, photoIndex), SyncApplier(db, photoIndex), refusing, Clock.systemUTC(), photoIndex, photoStore)
        val outcome = e.run()
        assertTrue(outcome is SyncOutcome.Success, outcome.toString())
        assertEquals(listOf(good), api.pushes.flatten().filter { it.type == RecordType.RECIPE }.map { it.id })
        assertTrue(api.pulls.isNotEmpty(), "Pull läuft trotzdem")
        assertTrue(db.syncDao().outbox().any { it.recordId == bad })
        assertEquals(listOf(bad to "photo_unsyncable"), db.syncDao().problems().map { it.recordId to it.code })
    }

    @Test
    fun orphanWishIsDropped() = runTest {
        activate()
        db.syncDao().upsertPhotoWanted(SyncPhotoWantedEntity("gone", PhotoHash.of(jpeg(3))))
        assertTrue(engine.run() is SyncOutcome.Success)
        assertTrue(db.syncDao().photosWanted().isEmpty())
        assertEquals(0, api.downloads)
    }

    @Test
    fun inactiveSyncDoesNothing() = runTest {
        db.ingredientDao().upsert(ingredient("a"))
        assertEquals(SyncOutcome.Success(0, 0, 0), engine.run())
        assertTrue(api.pushes.isEmpty() && api.pulls.isEmpty())
    }

    @Test
    fun acceptedRecordsAreDequeued() = runTest {
        activate()
        db.ingredientDao().upsert(ingredient("a"))
        api.onPush = { PushResponse(it.map { r -> accepted(r, 3) }) }
        val outcome = engine.run()
        assertEquals(SyncOutcome.Success(1, 0, 0), outcome)
        assertEquals(emptyList(), db.syncDao().outbox())
        assertEquals(3L, db.syncDao().revOf("ingredient", "a"))
        assertNull(db.syncDao().getState()!!.lastError)
        assertNotNull(db.syncDao().getState()!!.lastSyncAt)
    }

    @Test
    fun editDuringPushKeepsEntry() = runTest {
        activate()
        db.ingredientDao().upsert(ingredient("a"))
        api.onPush = {
            // Nutzeraktion während des Pushs
            db.ingredientDao().upsert(ingredient("a").copy(category = "Gewürz", updatedAt = 2))
            PushResponse(it.map { r -> accepted(r, 3) })
        }
        engine.run()
        assertEquals(listOf("a"), db.syncDao().outbox().map { it.recordId })
        assertEquals(3L, db.syncDao().revOf("ingredient", "a"))
    }

    @Test
    fun mergedAppliesCurrent() = runTest {
        activate(cursor = 7)
        db.shoppingDao().upsertList(ShoppingListEntity("l", "Liste", createdAt = 0, updatedAt = 1))
        db.shoppingDao().upsertItem(ShoppingItemEntity("it", "l", name = "Milch", manual = true, sortOrder = 0, updatedAt = 1))
        val current = SyncRecord(
            id = "it", type = RecordType.SHOPPING_ITEM, updatedAt = 99, rev = 5,
            payload = SyncMapper.toJson(
                ShoppingItemPayload(
                    listId = "l", name = "Milch", checked = true, checkedChangedAt = 99, manual = true,
                    sortOrder = 0, sources = emptyList(),
                ),
            ),
        )
        api.onPush = {
            PushResponse(
                it.map { r ->
                    if (r.id == "it") PushResult(r.id, r.type, PushStatus.MERGED, rev = 5, current = current) else accepted(r, 1)
                },
            )
        }
        assertTrue(engine.run() is SyncOutcome.Success)
        assertTrue(db.shoppingDao().getItem("it")!!.checked)
        assertEquals(emptyList(), db.syncDao().outbox())
        assertEquals(5L, db.syncDao().revOf("shopping_item", "it"))
        // `current` darf den Pull-Cursor nicht verschieben: der Pull startet weiter beim alten Cursor
        assertEquals(listOf(7L), api.pulls)
        assertEquals(7L, db.syncDao().getState()!!.cursor)
    }

    @Test
    fun mergedWithEditDuringPushKeepsLocalVersion() = runTest {
        activate()
        db.shoppingDao().upsertList(ShoppingListEntity("l", "Liste", createdAt = 0, updatedAt = 1))
        db.shoppingDao().upsertItem(ShoppingItemEntity("it", "l", name = "Milch", manual = true, sortOrder = 0, updatedAt = 1))
        db.syncDao().setRev(SyncRecordRevEntity("shopping_item", "it", 2))
        val current = SyncRecord(
            id = "it", type = RecordType.SHOPPING_ITEM, updatedAt = 99, rev = 5,
            payload = SyncMapper.toJson(
                ShoppingItemPayload(
                    listId = "l", name = "Server", checked = true, checkedChangedAt = 99, manual = true,
                    sortOrder = 0, sources = emptyList(),
                ),
            ),
        )
        api.onPush = {
            db.shoppingDao().upsertItem(db.shoppingDao().getItem("it")!!.copy(name = "Lokal", updatedAt = 2))
            PushResponse(
                it.map { r ->
                    if (r.id == "it") PushResult(r.id, r.type, PushStatus.MERGED, rev = 5, current = current) else accepted(r, 1)
                },
            )
        }
        engine.run()
        assertEquals("Lokal", db.shoppingDao().getItem("it")!!.name)
        assertEquals(listOf("it"), db.syncDao().outbox().map { it.recordId })
        // Alte Revision bleibt, damit der nächste Push erneut einen Konflikt auslöst
        assertEquals(2L, db.syncDao().revOf("shopping_item", "it"))
        assertFalse(db.shoppingDao().getItem("it")!!.checked)
    }

    @Test
    fun rejectedMissingReferenceStaysQueued() = runTest {
        activate()
        db.ingredientDao().upsert(ingredient("a"))
        api.onPush = { PushResponse(it.map { r -> PushResult(r.id, r.type, PushStatus.REJECTED, code = ErrorCode.MISSING_REFERENCE) }) }
        val outcome = engine.run()
        assertEquals(SyncOutcome.Success(0, 0, 1), outcome)
        assertEquals(listOf("a"), db.syncDao().outbox().map { it.recordId })
        assertEquals("missing_reference", db.syncDao().problems().single().code)
        // Der verbliebene Eintrag führt nicht zu einer Endlosschleife.
        assertEquals(1, api.pushes.size)
    }

    @Test
    fun rejectedInvalidIsDropped() = runTest {
        activate()
        db.ingredientDao().upsert(ingredient("a"))
        api.onPush = { PushResponse(it.map { r -> PushResult(r.id, r.type, PushStatus.REJECTED, code = ErrorCode.INVALID_PAYLOAD) }) }
        assertEquals(SyncOutcome.Success(0, 0, 1), engine.run())
        assertEquals(emptyList(), db.syncDao().outbox())
        assertEquals("invalid_payload", db.syncDao().problems().single().code)
    }

    @Test
    fun unauthorizedStopsCleanly() = runTest {
        activate()
        db.ingredientDao().upsert(ingredient("a"))
        api.onPush = { throw SyncApiException.Unauthorized(401, ErrorCode.UNAUTHORIZED) }
        assertEquals(SyncOutcome.Unauthorized, engine.run())
        assertEquals(listOf("a"), db.syncDao().outbox().map { it.recordId })
        assertEquals("unauthorized", db.syncDao().getState()!!.lastError)
        assertTrue(api.pulls.isEmpty())
    }

    @Test
    fun otherFailuresAreReportedWithoutDetails() = runTest {
        activate()
        api.onPull = { throw SyncApiException.Transient(503, null) }
        val t = engine.run()
        assertTrue(t is SyncOutcome.Failed && t.transient)
        assertTrue(db.syncDao().getState()!!.lastError!!.startsWith("transient: "))

        api.onPull = { throw IllegalStateException("geheimer Inhalt") }
        val f = engine.run()
        assertTrue(f is SyncOutcome.Failed && !f.transient)
        assertEquals("failed: IllegalStateException", db.syncDao().getState()!!.lastError)

        api.onPull = { throw SyncApiException.ProtocolMismatch(409, ErrorCode.SERVER_TOO_OLD) }
        assertEquals(SyncOutcome.ProtocolMismatch(true), engine.run())
        assertEquals("protocol_server_too_old", db.syncDao().getState()!!.lastError)
        api.onPull = { throw SyncApiException.ProtocolMismatch(409, ErrorCode.PROTOCOL_TOO_OLD) }
        assertEquals(SyncOutcome.ProtocolMismatch(false), engine.run())
        assertEquals("protocol_too_old", db.syncDao().getState()!!.lastError)
        api.onPull = { throw SyncApiException.NoHousehold(409, ErrorCode.NO_HOUSEHOLD) }
        assertEquals(SyncOutcome.NoHousehold, engine.run())
        assertEquals("no_household", db.syncDao().getState()!!.lastError)
    }

    @Test
    fun cursorExpiredTriggersFullResync() = runTest {
        activate(cursor = 5)
        db.ingredientDao().upsert(ingredient("A"))
        db.ingredientDao().upsert(ingredient("B"))
        db.ingredientDao().upsert(ingredient("C"))
        db.syncDao().dequeue("ingredient", "A")
        db.syncDao().dequeue("ingredient", "B")
        // C bleibt vorgemerkt (der Server lehnt wegen fehlender Verweise ab, der Eintrag bleibt)
        api.onPush = { PushResponse(it.map { r -> PushResult(r.id, r.type, PushStatus.REJECTED, code = ErrorCode.MISSING_REFERENCE) }) }
        api.onPull = { since ->
            if (since != 0L) throw SyncApiException.CursorExpired(410, ErrorCode.CURSOR_EXPIRED)
            PullResponse(listOf(ingredientRecord("A", "Zutat A", 1)), 42, false)
        }
        val outcome = engine.run()
        assertTrue(outcome is SyncOutcome.Success, "$outcome")
        assertEquals(listOf(5L, 0L), api.pulls)
        assertNotNull(db.ingredientDao().get("A"))
        assertNull(db.ingredientDao().get("B"))
        assertNotNull(db.ingredientDao().get("C"))
        // Die Löschung von B darf nichts in die Outbox stellen
        assertEquals(listOf("C"), db.syncDao().outbox().map { it.recordId })
        assertEquals(42L, db.syncDao().getState()!!.cursor)
        assertFalse(db.syncDao().getState()!!.applyingRemote)
    }

    @Test
    fun fullResyncKeepsIngredientStillUsedByRecipe() = runTest {
        activate(cursor = 5)
        db.ingredientDao().upsert(ingredient("A"))
        db.ingredientDao().upsert(ingredient("B"))
        // Das Rezept bleibt vorgemerkt (offene lokale Änderung) und verwendet B
        RecipeRepository(db.recipeDao()).save(
            RecipeDraft(
                id = null, name = "R", defaultServings = 2,
                ingredients = listOf(RecipeDraft.Line("B", BigDecimal.ONE, MeasureUnit.GRAM, null, false)),
            ),
        )
        db.syncDao().dequeue("ingredient", "A")
        db.syncDao().dequeue("ingredient", "B")
        api.onPull = { since ->
            if (since != 0L) throw SyncApiException.CursorExpired(410, ErrorCode.CURSOR_EXPIRED)
            PullResponse(listOf(ingredientRecord("A", "Zutat A", 1)), 7, false)
        }
        assertTrue(engine.run() is SyncOutcome.Success)
        // B steht in Rezeptzeilen (RESTRICT): nicht löschen, kein Absturz
        assertEquals(1, db.recipeDao().getAll().size)
        assertNotNull(db.ingredientDao().get("B"))
        assertNotNull(db.ingredientDao().get("A"))
        // Stehen gelassen, aber dem Server unbekannt: wieder vorgemerkt, damit er konvergiert
        assertTrue(db.syncDao().isQueued("ingredient", "B"))
        assertEquals(7L, db.syncDao().getState()!!.cursor)
    }

    @Test
    fun fullResyncKeepsRecipeReferencedByRemoteMealSlot() = runTest {
        activate(cursor = 5)
        db.ingredientDao().upsert(ingredient("I"))
        val rid = RecipeRepository(db.recipeDao()).save(
            RecipeDraft(
                id = null, name = "R", defaultServings = 2,
                ingredients = listOf(RecipeDraft.Line("I", BigDecimal.ONE, MeasureUnit.GRAM, null, false)),
            ),
        )
        db.syncDao().clearOutbox()
        val slot = SyncRecord(
            id = "S", type = RecordType.MEAL_SLOT, updatedAt = 10, rev = 2,
            payload = SyncMapper.toJson(MealSlotPayload(date = "2026-01-01", slotType = "DINNER", recipeId = rid, servings = 2)),
        )
        api.onPull = { since ->
            if (since != 0L) throw SyncApiException.CursorExpired(410, ErrorCode.CURSOR_EXPIRED)
            PullResponse(listOf(slot), 9, false)
        }
        assertTrue(engine.run() is SyncOutcome.Success)
        assertNotNull(db.mealPlanDao().get("S"))
        assertNotNull(db.recipeDao().get(rid))
        assertTrue(db.syncDao().isQueued("recipe", rid))
    }

    @Test
    fun fullResyncKeepsListWithQueuedItem() = runTest {
        activate(cursor = 5)
        db.shoppingDao().upsertList(ShoppingListEntity("l", "Liste", createdAt = 0, updatedAt = 1))
        db.shoppingDao().upsertItem(ShoppingItemEntity("it", "l", name = "Milch", manual = true, sortOrder = 0, updatedAt = 1))
        db.syncDao().dequeue("shopping_list", "l")
        api.onPull = { since ->
            if (since != 0L) throw SyncApiException.CursorExpired(410, ErrorCode.CURSOR_EXPIRED)
            PullResponse(emptyList(), 9, false)
        }
        assertTrue(engine.run() is SyncOutcome.Success)
        assertNotNull(db.shoppingDao().getList("l"))
        assertNotNull(db.shoppingDao().getItem("it"))
        assertTrue(db.syncDao().isQueued("shopping_list", "l"))
    }

    @Test
    fun fullResyncSurvivesMalformedServerRecord() = runTest {
        activate(cursor = 5)
        db.ingredientDao().upsert(ingredient("B"))
        db.syncDao().dequeue("ingredient", "B")
        val bad = SyncRecord(
            id = "bad", type = RecordType.RECIPE, updatedAt = 10, rev = 3,
            payload = kotlinx.serialization.json.buildJsonObject { put("unbekannt", kotlinx.serialization.json.JsonPrimitive(1)) },
        )
        api.onPull = { since ->
            if (since != 0L) throw SyncApiException.CursorExpired(410, ErrorCode.CURSOR_EXPIRED)
            PullResponse(listOf(bad), 11, false)
        }
        val outcome = engine.run()
        assertTrue(outcome is SyncOutcome.Success, "$outcome")
        assertEquals(11L, db.syncDao().getState()!!.cursor)
        assertNull(db.ingredientDao().get("B"))
        assertEquals("invalid_payload", db.syncDao().problems().single().code)
    }

    @Test
    fun pullIsAppliedOnceAfterAllPages() = runTest {
        activate()
        val cursorsSeen = ArrayList<Long>()
        val ingredientsSeen = ArrayList<Int>()
        api.onPull = { since ->
            cursorsSeen += db.syncDao().getState()!!.cursor
            ingredientsSeen += db.ingredientDao().getAll().size
            when (since) {
                0L -> PullResponse(listOf(ingredientRecord("a", "A", 1)), 10, true)
                10L -> PullResponse(listOf(ingredientRecord("b", "B", 2)), 20, true)
                else -> PullResponse(listOf(ingredientRecord("c", "C", 3)), 30, false)
            }
        }
        val outcome = engine.run()
        assertEquals(SyncOutcome.Success(0, 3, 0), outcome)
        assertEquals(listOf(0L, 10L, 20L), api.pulls)
        // Vor dem Ende der letzten Seite ist weder etwas angewendet noch der Cursor bewegt.
        assertEquals(listOf(0L, 0L, 0L), cursorsSeen)
        assertEquals(listOf(0, 0, 0), ingredientsSeen)
        assertEquals(3, db.ingredientDao().getAll().size)
        assertEquals(30L, db.syncDao().getState()!!.cursor)
    }

    @Test
    fun tooLargeBatchIsSplitAndEverythingIsPushed() = runTest {
        activate(cursor = 3)
        for (i in 1..5) db.ingredientDao().upsert(ingredient("i$i"))
        api.onPush = { records ->
            if (records.size > 2) throw SyncApiException.TooLarge(413, ErrorCode.TOO_LARGE)
            PushResponse(records.map { accepted(it, 1) })
        }
        val outcome = engine.run()
        assertEquals(SyncOutcome.Success(5, 0, 0), outcome)
        assertEquals(emptyList(), db.syncDao().outbox())
        assertEquals(listOf(3L), api.pulls)
        assertNull(db.syncDao().getState()!!.lastError)
    }

    @Test
    fun singleTooLargeRecordBecomesProblemAndOthersArePushed() = runTest {
        activate(cursor = 3)
        for (i in 1..4) db.ingredientDao().upsert(ingredient("i$i"))
        api.onPush = { records ->
            if (records.any { it.id == "i3" }) throw SyncApiException.TooLarge(413, ErrorCode.TOO_LARGE)
            PushResponse(records.map { accepted(it, 1) })
        }
        val outcome = engine.run()
        assertEquals(SyncOutcome.Success(3, 0, 1), outcome)
        val problem = db.syncDao().problems().single()
        assertEquals("too_large", problem.code)
        assertEquals("i3", problem.recordId)
        assertEquals(emptyList(), db.syncDao().outbox())
        // Der Pull läuft trotzdem.
        assertEquals(listOf(3L), api.pulls)
    }

    @Test
    fun fullResyncKeepsRejectedLocalRecordAndItsProblem() = runTest {
        activate(cursor = 5)
        db.ingredientDao().upsert(ingredient("R"))
        api.onPush = { PushResponse(it.map { r -> PushResult(r.id, r.type, PushStatus.REJECTED, code = ErrorCode.INVALID_PAYLOAD) }) }
        api.onPull = { since ->
            if (since != 0L) throw SyncApiException.CursorExpired(410, ErrorCode.CURSOR_EXPIRED)
            PullResponse(emptyList(), 9, false)
        }
        assertTrue(engine.run() is SyncOutcome.Success)
        assertNotNull(db.ingredientDao().get("R"))
        assertEquals("invalid_payload", db.syncDao().problems().single().code)
        assertEquals(9L, db.syncDao().getState()!!.cursor)
    }

    @Test
    fun stuckCursorFailsTransiently() = runTest {
        activate()
        api.onPull = { since -> PullResponse(emptyList(), since, true) }
        assertEquals(SyncOutcome.Failed(true, "cursor_stuck"), engine.run())
        assertEquals(1, api.pulls.size)
        assertEquals("transient: cursor_stuck", db.syncDao().getState()!!.lastError)
    }
}

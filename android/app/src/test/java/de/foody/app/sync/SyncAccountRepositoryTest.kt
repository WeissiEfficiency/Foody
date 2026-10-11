package de.foody.app.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.db.SYNC_CALLBACK
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.domain.MeasureUnit
import io.ktor.client.HttpClient
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Token nur im Speicher (der echte Store braucht den AndroidKeyStore). */
private class MemoryTokenStore(context: Context) : TokenStore(context) {
    var token: String? = null
    override fun load(): String? = token
    override fun save(token: String) {
        this.token = token
    }

    override fun clear() {
        token = null
    }
}

/** Konto-Funktionen gegen den echten `:server` (in-process) mit In-Memory-Room-DB. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SyncAccountRepositoryTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var db: FoodyDatabase
    private lateinit var tokens: MemoryTokenStore
    private lateinit var scope: CoroutineScope
    private val url = "http://localhost"

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().build())
        db = Room.inMemoryDatabaseBuilder(app, FoodyDatabase::class.java)
            .addCallback(FoodyDatabase.SYNC_CALLBACK).allowMainThreadQueries().build()
        tokens = MemoryTokenStore(app)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun repo(client: HttpClient): SyncAccountRepository {
        val photoStore = RecipePhotoStore(app, db.recipeDao())
        val photoIndex = PhotoIndex(db, photoStore)
        val store = SyncLocalStore(db, photoIndex)
        val factory = SyncEngineFactory(db, store, SyncApplier(db, photoIndex), tokens, photoIndex, photoStore)
        return SyncAccountRepository(db, tokens, store, SyncScheduler(app, db, factory), client, scope)
    }

    private suspend fun savedRecipe(): String {
        val salz = IngredientRepository(db, db.ingredientDao(), app).getOrCreate("Salz")
        val line = RecipeDraft.Line(salz.id, BigDecimal(1), MeasureUnit.GRAM, null, false)
        return RecipeRepository(db.recipeDao()).save(RecipeDraft(null, "Ei", 2, ingredients = listOf(line)))
    }

    @Test
    fun loginStoresTokenAndReturnsHousehold() = syncServerTest { _, _ ->
        val repo = repo(client)
        val first = repo.login(url, TEST_ADMIN_USER, TEST_ADMIN_PASSWORD, "Test")
        assertNotNull(tokens.load())
        assertNull(first.householdId) // neues Gerät, noch kein Haushalt
        val household = repo.createHousehold(url, "Zuhause")
        repo.selectHousehold(url, household.id)
        assertEquals(listOf(household.id), repo.households(url).map { it.id })
        // Zweites Gerät desselben Kontos wird dem Haushalt nicht automatisch zugeordnet; nach selectHousehold schon.
        assertEquals(false, db.syncDao().getState()?.active ?: false, "Login allein aktiviert den Sync nicht")
    }

    @Test
    fun registerWithInviteJoinsHousehold() = syncServerTest { _, _ ->
        val repo = repo(client)
        repo.login(url, TEST_ADMIN_USER, TEST_ADMIN_PASSWORD, "A")
        val household = repo.createHousehold(url, "Zuhause")
        repo.selectHousehold(url, household.id)
        repo.activate(url, household.id, FirstSync.DOWNLOAD_ONLY)
        val invite = repo.createInvite()

        tokens.clear()
        val joined = repo.register(url, invite.code, "bea", "geheimgeheim2", "B")
        assertEquals(household.id, joined.householdId)
        assertNotNull(tokens.load())
    }

    @Test
    fun activateUploadAllQueuesLocalData() = syncServerTest { _, _ ->
        val repo = repo(client)
        val rid = savedRecipe()
        repo.login(url, TEST_ADMIN_USER, TEST_ADMIN_PASSWORD, "A")
        val household = repo.createHousehold(url, "Zuhause")
        repo.selectHousehold(url, household.id)
        assertEquals(emptyList(), db.syncDao().outbox(), "vor dem Aktivieren wird nichts vorgemerkt")

        repo.activate(url, household.id, FirstSync.UPLOAD_ALL)

        val state = assertNotNull(db.syncDao().getState())
        assertTrue(state.active)
        assertEquals(url, state.serverUrl)
        assertEquals(household.id, state.householdId)
        assertTrue(db.syncDao().outbox().any { it.type == "recipe" && it.recordId == rid })
    }

    @Test
    fun activateDownloadOnlyQueuesNothing() = syncServerTest { _, _ ->
        val repo = repo(client)
        savedRecipe()
        repo.login(url, TEST_ADMIN_USER, TEST_ADMIN_PASSWORD, "A")
        val household = repo.createHousehold(url, "Zuhause")
        repo.selectHousehold(url, household.id)

        repo.activate(url, household.id, FirstSync.DOWNLOAD_ONLY)

        assertTrue(db.syncDao().getState()!!.active)
        assertEquals(emptyList(), db.syncDao().outbox())
    }

    @Test
    fun hasLocalDataIgnoresSeedIngredients() = syncServerTest { _, _ ->
        val repo = repo(client)
        db.ingredientDao().upsert(IngredientEntity(id = "seed-1", canonicalName = "Mehl", createdAt = 0, updatedAt = 0))
        assertFalse(repo.hasLocalData(), "Zutaten allein sind keine Nutzerdaten")
        savedRecipe()
        assertTrue(repo.hasLocalData())
    }

    @Test
    fun disconnectRevokesOwnDeviceAndClearsLocalState() = syncServerTest { _, _ ->
        val repo = repo(client)
        repo.login(url, TEST_ADMIN_USER, TEST_ADMIN_PASSWORD, "A")
        val household = repo.createHousehold(url, "Zuhause")
        repo.selectHousehold(url, household.id)
        repo.activate(url, household.id, FirstSync.UPLOAD_ALL)
        val oldToken = tokens.load()!!

        repo.disconnect()

        assertNull(tokens.load())
        val state = db.syncDao().getState()!!
        assertFalse(state.active)
        assertNull(state.serverUrl)
        // Das alte Token ist beim Server ungültig.
        tokens.token = oldToken
        assertFailsWith<SyncApiException.Unauthorized> { repo.households(url) }
    }

    @Test
    fun disconnectWorksOffline() = runBlocking {
        val repo = repo(defaultHttpClient()) // nichts hört auf Port 1
        tokens.token = "altes-token"
        val dead = "http://localhost:1"
        savedRecipe()
        db.syncDao().upsertState(
            (db.syncDao().getState() ?: de.foody.app.data.db.SyncStateEntity()).copy(active = true, serverUrl = dead, householdId = "h"),
        )

        repo.disconnect()

        assertNull(tokens.load())
        val state = db.syncDao().getState()!!
        assertFalse(state.active)
        assertNull(state.serverUrl)
        assertTrue(repo.hasLocalData(), "lokale Daten bleiben")
    }

    @Test
    fun wrongUrlNeverActivates() = runBlocking {
        val repo = repo(defaultHttpClient())
        val e = assertFailsWith<SyncApiException> { repo.login("http://localhost:1", "x", "y", "Test") }
        assertTrue(e is SyncApiException.Transient)
        assertNull(tokens.load())
        assertFalse(db.syncDao().getState()?.active ?: false)
    }
}

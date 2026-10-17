package de.foody.app.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.SYNC_CALLBACK
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.domain.MeasureUnit
import io.ktor.client.HttpClient
import java.math.BigDecimal
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

/** Konto-Funktionen ohne Server (Trennen offline, falsche Adresse) mit In-Memory-Room-DB. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
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

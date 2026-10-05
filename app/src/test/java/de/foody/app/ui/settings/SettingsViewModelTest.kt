package de.foody.app.ui.settings

import android.app.Application
import android.net.Uri
import androidx.room.Room
import de.foody.app.R
import de.foody.app.data.GoalPreferences
import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.ThemePreferences
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.db.SYNC_CALLBACK
import de.foody.app.data.db.SyncStateEntity
import de.foody.app.data.repo.BackupRepository
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Löschen und Import sind bei verbundener Synchronisierung gesperrt (sonst würde der Haushalt auf dem Server geleert). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsViewModelTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var db: FoodyDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(app, FoodyDatabase::class.java)
            .addCallback(FoodyDatabase.SYNC_CALLBACK).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(): SettingsViewModel {
        val backup = BackupRepository(db, app, RecipePhotoStore(app, db.recipeDao()))
        return SettingsViewModel(backup, db, ThemePreferences(app), GoalPreferences(app))
    }

    private suspend fun recipe() = db.recipeDao().upsert(RecipeEntity("r1", "Linsensuppe", 2, null, null, null, null, "", null, 1L, 1L))

    private suspend fun setActive(active: Boolean) =
        db.syncDao().upsertState(SyncStateEntity(active = active, serverUrl = "https://x.org", householdId = "h1"))

    private suspend fun SettingsViewModel.nextMessage(): Int = withTimeout(10_000) { message.first { it != null } }!!

    @Test
    fun deleteAllIsBlockedWhileSyncIsActive() = runBlocking {
        setActive(true)
        recipe()
        db.syncDao().clearOutbox()
        val vm = vm()
        vm.deleteAll()
        assertEquals(R.string.sync_blocks_destructive, vm.nextMessage())
        assertNotNull(db.recipeDao().get("r1"), "Rezept bleibt")
        assertTrue(db.syncDao().outbox().none { it.deleted }, "keine Löschmarkierung in der Outbox")
    }

    @Test
    fun importIsBlockedWhileSyncIsActive() = runBlocking {
        setActive(true)
        recipe()
        db.syncDao().clearOutbox()
        val vm = vm()
        vm.import(Uri.parse("content://nowhere/backup.zip"))
        assertEquals(R.string.sync_blocks_destructive, vm.nextMessage())
        assertNotNull(db.recipeDao().get("r1"))
        assertTrue(db.syncDao().outbox().none { it.deleted })
    }

    @Test
    fun deleteAllWorksWhenSyncIsInactive() = runBlocking {
        setActive(false)
        recipe()
        val vm = vm()
        vm.deleteAll()
        assertEquals(R.string.data_deleted, vm.nextMessage())
        assertNull(db.recipeDao().get("r1"))
    }

    @Test
    fun uiCheckReportsBlockedState() = runBlocking {
        setActive(true)
        val vm = vm()
        assertTrue(vm.refuseWhileSyncActive())
        assertEquals(R.string.sync_blocks_destructive, vm.nextMessage())
        vm.messageShown()
        setActive(false)
        assertFalse(vm.refuseWhileSyncActive())
    }
}

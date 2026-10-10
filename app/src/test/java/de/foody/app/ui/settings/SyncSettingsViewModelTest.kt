package de.foody.app.ui.settings

import android.app.Application
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import de.foody.app.R
import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.db.SYNC_CALLBACK
import de.foody.app.data.db.SyncProblemEntity
import de.foody.app.data.db.SyncStateEntity
import de.foody.app.sync.FirstSync
import de.foody.app.sync.LoginResult
import de.foody.app.sync.PhotoIndex
import de.foody.app.sync.SyncAccounts
import de.foody.app.sync.SyncApiException
import de.foody.app.sync.SyncApplier
import de.foody.app.sync.SyncEngineFactory
import de.foody.app.sync.SyncLocalStore
import de.foody.app.sync.SyncScheduler
import de.foody.app.sync.TokenStore
import de.foody.sync.protocol.DeviceDto
import de.foody.sync.protocol.HouseholdDto
import de.foody.sync.protocol.InviteDto
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
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

/** Fake ohne Netzwerk; `disconnect` verhält sich wie das echte (Zustand zurücksetzen). */
private class FakeAccounts(private val db: FoodyDatabase) : SyncAccounts {
    var householdName: String? = "Zuhause"
    var invite = InviteDto("ABCD-1234", 1_900_000_000_000)
    var deviceList = listOf(
        DeviceDto("d1", "Pixel", 1_700_000_000_000, current = true),
        DeviceDto("d2", "Tablet", null, current = false),
    )
    var failDevices: Throwable? = null
    val revoked = mutableListOf<String>()
    var disconnects = 0

    override suspend fun households(url: String): List<HouseholdDto> =
        householdName?.let { listOf(HouseholdDto("h1", it, "owner")) } ?: throw SyncApiException.Transient(0, null, RuntimeException("x"))

    override suspend fun createInvite() = invite

    override suspend fun devices(): List<DeviceDto> = failDevices?.let { throw it } ?: deviceList

    override suspend fun revokeDevice(id: String) {
        revoked += id
        deviceList = deviceList.filterNot { it.id == id }
    }

    override suspend fun discardToken() = Unit

    override suspend fun disconnect(serverUrl: String?) {
        disconnects++
        db.syncDao().upsertState(SyncStateEntity())
    }

    override suspend fun login(url: String, username: String, password: String, deviceName: String): LoginResult = TODO()
    override suspend fun register(url: String, code: String, username: String, password: String, deviceName: String): LoginResult = TODO()
    override suspend fun createHousehold(url: String, name: String): HouseholdDto = TODO()
    override suspend fun selectHousehold(url: String, id: String) = TODO()
    override suspend fun hasLocalData(): Boolean = TODO()
    override suspend fun activate(url: String, householdId: String, mode: FirstSync) = TODO()
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SyncSettingsViewModelTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var db: FoodyDatabase
    private lateinit var fake: FakeAccounts
    private lateinit var scheduler: SyncScheduler

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().build())
        db = Room.inMemoryDatabaseBuilder(app, FoodyDatabase::class.java)
            .addCallback(FoodyDatabase.SYNC_CALLBACK).allowMainThreadQueries().build()
        fake = FakeAccounts(db)
        val photoStore = RecipePhotoStore(app, db.recipeDao())
        val photoIndex = PhotoIndex(db, photoStore)
        val store = SyncLocalStore(db, photoIndex)
        scheduler = SyncScheduler(app, db, SyncEngineFactory(db, store, SyncApplier(db, photoIndex), TokenStore(app), photoIndex, photoStore))
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm() = SyncSettingsViewModel(db, fake, scheduler)

    private suspend fun StateFlow<SyncSettingsState>.await(predicate: (SyncSettingsState) -> Boolean): SyncSettingsState =
        withTimeout(10_000) { first(predicate) }

    private suspend fun connect(lastError: String? = null) = db.syncDao().upsertState(
        SyncStateEntity(
            active = true, serverUrl = "https://foody.example", householdId = "h1",
            lastSyncAt = System.currentTimeMillis(), lastError = lastError,
        ),
    )

    @Test
    fun offStateWhenNotActive() = runBlocking {
        val s = vm().state.await { true }
        assertFalse(s.connected)
        assertFalse(s.unauthorized)
        assertNull(s.householdName)
    }

    @Test
    fun connectedShowsHouseholdAndLastSync() = runBlocking {
        connect()
        val s = vm().state.await { it.connected && it.householdName != null }
        assertEquals("Zuhause", s.householdName)
        assertNotNull(s.lastSyncAt)
        assertFalse(s.unauthorized)
    }

    @Test
    fun householdLookupFailureStillConnected() = runBlocking {
        fake.householdName = null
        connect()
        val s = vm().state.await { it.connected }
        assertNull(s.householdName)
    }

    @Test
    fun revokedTokenShowsUnauthorizedAndNoLookup() = runBlocking {
        connect(lastError = "unauthorized")
        val s = vm().state.await { it.connected }
        assertTrue(s.unauthorized)
        assertNull(s.householdName)
    }

    @Test
    fun persistentErrorsMapToStatusNote() = runBlocking {
        val cases = listOf(
            "protocol_too_old" to R.string.sync_error_update_app,
            "protocol_server_too_old" to R.string.sync_error_update_server,
            "no_household" to R.string.sync_status_no_household,
            "failed: IllegalStateException" to R.string.sync_status_last_failed,
        )
        for ((error, note) in cases) {
            connect(lastError = error)
            val s = vm().state.await { it.connected }
            assertEquals(note, s.statusNote, error)
            assertEquals(error == "no_household", s.reconnectNeeded, error)
        }
    }

    @Test
    fun transientAndCursorStuckShowNoSpecialLine() = runBlocking {
        for (error in listOf("transient: timeout", "transient: cursor_stuck", null)) {
            connect(lastError = error)
            val s = vm().state.await { it.connected }
            assertNull(s.statusNote, error)
            assertFalse(s.reconnectNeeded, error)
        }
    }

    @Test
    fun unauthorizedNeedsReconnect() = runBlocking {
        connect(lastError = "unauthorized")
        assertTrue(vm().state.await { it.connected }.reconnectNeeded)
    }

    @Test
    fun syncNowRequestsWork() = runBlocking {
        connect()
        val vm = vm()
        vm.syncNow()
        val infos = WorkManager.getInstance(app).getWorkInfosForUniqueWork(SyncScheduler.NOW_WORK).get()
        assertEquals(1, infos.size)
        assertEquals(R.string.sync_now_started, vm.state.await { it.message != null }.message)
    }

    @Test
    fun problemsAreResolvedWithRecipeNameAndReason() = runBlocking {
        connect()
        db.recipeDao().upsert(
            RecipeEntity("r1", "Linsensuppe", 2, null, null, null, null, "", null, 1L, 1L),
        )
        db.syncDao().addProblem(SyncProblemEntity("recipe", "r1", "too_large", 1L))
        db.syncDao().addProblem(SyncProblemEntity("pantry_item", "p1", "apply_failed", 2L))
        db.syncDao().addProblem(SyncProblemEntity("shopping_item", "s1", "weird", 3L))
        db.syncDao().addProblem(SyncProblemEntity("shopping_list", "l1", "forbidden", 4L))
        val vm = vm()
        assertEquals(4, vm.state.await { it.problemCount == 4 }.problemCount)
        vm.loadProblems()
        val items = vm.state.await { it.problems != null }.problems!!
        val recipe = items.single { it.title == "Linsensuppe" }
        assertEquals(R.string.sync_type_recipe, recipe.type)
        assertEquals(R.string.sync_problem_too_large, recipe.reason)
        val pantry = items.single { it.type == R.string.sync_type_pantry_item }
        assertNull(pantry.title)
        assertEquals(R.string.sync_problem_apply_failed, pantry.reason)
        assertEquals(R.string.sync_problem_unknown, items.single { it.type == R.string.sync_type_shopping_item }.reason)
        assertEquals(R.string.sync_problem_rejected, items.single { it.type == R.string.sync_type_shopping_list }.reason)
    }

    @Test
    fun disconnectSwitchesToOff() = runBlocking {
        connect()
        val vm = vm()
        vm.state.await { it.connected }
        vm.disconnect()
        // Der Zustand „getrennt“ kommt aus der DB, die Meldung erst danach: auf beides warten (sonst flakey in der CI).
        val s = vm.state.await { !it.connected && it.message != null }
        assertEquals(1, fake.disconnects)
        assertNull(s.householdName)
        assertEquals(R.string.sync_disconnected, s.message)
    }

    @Test
    fun inviteLandsInStateAndCanBeDismissed() = runBlocking {
        connect()
        val vm = vm()
        vm.createInvite()
        assertEquals("ABCD-1234", vm.state.await { it.invite != null }.invite?.code)
        vm.dismissInvite()
        assertNull(vm.state.await { it.invite == null }.invite)
    }

    @Test
    fun devicesListAndRevoke() = runBlocking {
        connect()
        val vm = vm()
        vm.loadDevices()
        assertEquals(2, vm.state.await { it.devices != null }.devices!!.size)
        vm.revokeDevice("d2")
        assertEquals(listOf("d2"), fake.revoked)
        assertEquals(listOf("d1"), vm.state.await { it.devices?.size == 1 }.devices!!.map { it.id })
    }

    @Test
    fun errorBecomesMessage() = runBlocking {
        connect()
        fake.failDevices = SyncApiException.Transient(0, null, RuntimeException("offline"))
        val vm = vm()
        vm.loadDevices()
        val s = vm.state.await { it.message != null }
        assertEquals(R.string.sync_error_unreachable, s.message)
        assertNull(s.devices)
    }
}

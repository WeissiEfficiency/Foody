package de.foody.app.ui.sync

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import de.foody.app.R
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.SYNC_CALLBACK
import de.foody.app.data.db.SyncStateEntity
import de.foody.app.sync.FirstSync
import de.foody.app.sync.LoginResult
import de.foody.app.sync.SyncAccounts
import de.foody.app.sync.SyncApiException
import de.foody.sync.protocol.DeviceDto
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.HouseholdDto
import de.foody.sync.protocol.InviteDto
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private class FakeAccounts : SyncAccounts {
    var loginResult = LoginResult(null)
    var households = listOf(HouseholdDto("h1", "Zuhause", "owner"))
    var localData = false
    var loginError: Throwable? = null
    val calls = mutableListOf<String>()
    var activated: Triple<String, String, FirstSync>? = null
    var disconnects = 0

    override suspend fun login(url: String, username: String, password: String, deviceName: String): LoginResult {
        calls += "login"
        loginError?.let { throw it }
        return loginResult
    }
    override suspend fun register(url: String, code: String, username: String, password: String, deviceName: String): LoginResult {
        calls += "register:$code"
        return loginResult
    }
    override suspend fun households(url: String) = households
    override suspend fun createHousehold(url: String, name: String): HouseholdDto {
        calls += "create:$name"
        return HouseholdDto("new", name, "owner")
    }
    override suspend fun selectHousehold(url: String, id: String) { calls += "select:$id" }
    override suspend fun hasLocalData() = localData
    override suspend fun activate(url: String, householdId: String, mode: FirstSync) {
        calls += "activate"
        activated = Triple(url, householdId, mode)
    }
    override suspend fun createInvite(): InviteDto = TODO()
    override suspend fun devices(): List<DeviceDto> = TODO()
    override suspend fun revokeDevice(id: String) = TODO()
    override suspend fun disconnect() { disconnects++ }
}

private class FakeBackup : SetupBackup {
    var exportError: Throwable? = null
    val events = mutableListOf<String>()
    override suspend fun export(uri: Uri) {
        events += "export"
        exportError?.let { throw it }
    }
    override suspend fun deleteAll() { events += "deleteAll" }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SyncSetupViewModelTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var db: FoodyDatabase
    private lateinit var fake: FakeAccounts
    private lateinit var backup: FakeBackup

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(app, FoodyDatabase::class.java)
            .addCallback(FoodyDatabase.SYNC_CALLBACK).allowMainThreadQueries().build()
        fake = FakeAccounts()
        backup = FakeBackup()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(handle: SavedStateHandle = SavedStateHandle()) =
        SyncSetupViewModel(handle, fake, db, backup, CoroutineScope(UnconfinedTestDispatcher()))

    /** Bis zum Konto-Schritt mit Anmeldedaten. */
    private fun SyncSetupViewModel.toAccount(url: String = "https://foody.example.org/") {
        setUrl(url); submitUrl()
        setUsername("anna"); setPassword("geheim-passwort")
    }

    @Test
    fun invalidUrlShowsMessageAndStaysOnUrlStep() {
        val vm = vm()
        vm.setUrl("http://example.org"); vm.submitUrl()
        assertEquals(SetupStep.URL, vm.state.value.step)
        assertEquals(R.string.sync_error_invalid_url, vm.state.value.error)
    }

    @Test
    fun loginWithHouseholdAndNoLocalDataDownloadsOnlyWithoutQuestion() {
        fake.loginResult = LoginResult("h1")
        val vm = vm(); vm.toAccount(); vm.submitAccount()
        assertEquals(Triple("https://foody.example.org", "h1", FirstSync.DOWNLOAD_ONLY), fake.activated)
        assertEquals(SetupStep.DONE, vm.state.value.step)
        assertEquals("", vm.state.value.password)
    }

    @Test
    fun loginWithHouseholdAndLocalDataAsksFirstSync() {
        fake.loginResult = LoginResult("h1"); fake.localData = true
        val vm = vm(); vm.toAccount(); vm.submitAccount()
        assertEquals(SetupStep.FIRST_SYNC, vm.state.value.step)
        assertNull(fake.activated)
        vm.chooseMerge()
        assertEquals(FirstSync.UPLOAD_ALL, fake.activated?.third)
        assertEquals(SetupStep.DONE, vm.state.value.step)
    }

    @Test
    fun loginWithoutHouseholdOffersHouseholdStep() {
        val vm = vm(); vm.toAccount(); vm.submitAccount()
        assertEquals(SetupStep.HOUSEHOLD, vm.state.value.step)
        assertEquals(listOf("h1"), vm.state.value.households.map { it.id })
        assertNull(fake.activated)
    }

    @Test
    fun newHouseholdUploadsAllWithoutQuestion() {
        fake.localData = true
        val vm = vm(); vm.toAccount(); vm.submitAccount()
        vm.setNewHouseholdName("Neu"); vm.createHousehold()
        assertEquals(Triple("https://foody.example.org", "new", FirstSync.UPLOAD_ALL), fake.activated)
        assertEquals(SetupStep.DONE, vm.state.value.step)
    }

    @Test
    fun joiningExistingWithoutDataDownloadsOnly() {
        val vm = vm(); vm.toAccount(); vm.submitAccount()
        vm.selectHousehold("h1")
        assertTrue("select:h1" in fake.calls)
        assertEquals(FirstSync.DOWNLOAD_ONLY, fake.activated?.third)
        assertEquals(SetupStep.DONE, vm.state.value.step)
    }

    @Test
    fun joiningExistingWithDataAsks() {
        fake.localData = true
        val vm = vm(); vm.toAccount(); vm.submitAccount()
        vm.selectHousehold("h1")
        assertEquals(SetupStep.FIRST_SYNC, vm.state.value.step)
        assertNull(fake.activated)
    }

    @Test
    fun registerUsesInviteCode() {
        fake.loginResult = LoginResult("h1")
        val vm = vm(); vm.setUrl("https://x.org"); vm.submitUrl()
        vm.setRegister(true); vm.setInviteCode("ABCD"); vm.setUsername("bob"); vm.setPassword("passwort-123456")
        vm.submitAccount()
        assertTrue("register:ABCD" in fake.calls)
        assertEquals(SetupStep.DONE, vm.state.value.step)
    }

    @Test
    fun replaceWithoutSuccessfulBackupDeletesNothing() {
        fake.loginResult = LoginResult("h1"); fake.localData = true
        val vm = vm(); vm.toAccount(); vm.submitAccount()
        // Abbruch des Speicherdialogs
        vm.chooseReplace()
        vm.onBackupCancelled()
        assertEquals(SetupStep.FIRST_SYNC, vm.state.value.step)
        assertEquals(R.string.sync_setup_backup_cancelled, vm.state.value.error)
        // Export schlägt fehl
        backup.exportError = RuntimeException("voll")
        vm.onBackupChosen(Uri.parse("content://x/backup.zip"))
        assertEquals(listOf("export"), backup.events)
        assertEquals(SetupStep.FIRST_SYNC, vm.state.value.step)
        assertEquals(R.string.sync_setup_backup_failed, vm.state.value.error)
        assertNull(fake.activated)
    }

    @Test
    fun replaceAfterBackupDeletesThenDownloadsOnly() {
        fake.loginResult = LoginResult("h1"); fake.localData = true
        val vm = vm(); vm.toAccount(); vm.submitAccount()
        vm.onBackupChosen(Uri.parse("content://x/backup.zip"))
        assertEquals(listOf("export", "deleteAll"), backup.events)
        assertEquals(FirstSync.DOWNLOAD_ONLY, fake.activated?.third)
        assertEquals(SetupStep.DONE, vm.state.value.step)
    }

    @Test
    fun chooseReplaceRaisesOfferBackupFlag() {
        fake.loginResult = LoginResult("h1"); fake.localData = true
        val vm = vm(); vm.toAccount(); vm.submitAccount()
        vm.chooseReplace()
        assertTrue(vm.offerBackup.value)
        vm.offerBackupHandled()
        assertTrue(!vm.offerBackup.value)
    }

    @Test
    fun errorsAreShownAndStepStays() {
        fake.loginError = SyncApiException.ClientError(400, ErrorCode.INVALID_CREDENTIALS)
        val vm = vm(); vm.toAccount(); vm.submitAccount()
        assertEquals(SetupStep.ACCOUNT, vm.state.value.step)
        assertEquals(R.string.sync_error_invalid_credentials, vm.state.value.error)
        assertTrue(!vm.state.value.busy)
    }

    @Test
    fun processDeathRestoresStepUrlAndUsernameButNotPassword() {
        fake.loginResult = LoginResult("h1"); fake.localData = true
        val handle = SavedStateHandle()
        val first = vm(handle); first.toAccount(); first.submitAccount()
        assertEquals(SetupStep.FIRST_SYNC, first.state.value.step)
        val second = vm(handle)
        assertEquals(SetupStep.FIRST_SYNC, second.state.value.step)
        assertEquals("https://foody.example.org", second.state.value.url)
        assertEquals("anna", second.state.value.username)
        assertEquals("", second.state.value.password)
        // Das Passwort steht nirgends im SavedStateHandle
        assertTrue(handle.keys().none { handle.get<Any>(it) == "geheim-passwort" })
        second.chooseMerge()
        assertEquals(FirstSync.UPLOAD_ALL, fake.activated?.third)
        assertEquals("h1", fake.activated?.second)
    }

    @Test
    fun reconnectKeepsHouseholdAndUploadsWithoutQuestion() = runBlocking {
        db.syncDao().upsertState(
            SyncStateEntity(active = true, serverUrl = "https://foody.example.org", householdId = "h9", lastError = "unauthorized"),
        )
        fake.loginResult = LoginResult("h1") // der Server meldet einen anderen Haushalt; der bisherige gilt
        fake.localData = true
        val vm = vm(SavedStateHandle(mapOf("reconnect" to true)))
        withTimeout(10_000) { vm.state.first { it.url.isNotEmpty() } }
        assertEquals("https://foody.example.org", vm.state.value.url)
        vm.submitUrl(); vm.setUsername("anna"); vm.setPassword("pw-geheim-12345"); vm.submitAccount()
        withTimeout(10_000) { vm.state.first { it.step == SetupStep.DONE } }
        assertTrue("select:h9" in fake.calls)
        assertEquals(Triple("https://foody.example.org", "h9", FirstSync.UPLOAD_ALL), fake.activated)
        assertEquals(SetupStep.DONE, vm.state.value.step)
        assertEquals(0, backup.events.size)
    }

    @Test
    fun reconnectCancelNeverDisconnects() = runBlocking {
        db.syncDao().upsertState(SyncStateEntity(active = true, serverUrl = "https://x.org", householdId = "h9", lastError = "unauthorized"))
        val vm = vm(SavedStateHandle(mapOf("reconnect" to true)))
        withTimeout(10_000) { vm.state.first { it.url.isNotEmpty() } }
        vm.submitUrl(); vm.setUsername("anna"); vm.setPassword("pw-geheim-12345"); vm.submitAccount()
        withTimeout(10_000) { vm.state.first { it.step == SetupStep.DONE } }
        vm.cancel()
        assertEquals(0, fake.disconnects)
    }

    @Test
    fun cancelAfterLoginBeforeActivationDisconnects() {
        val vm = vm(); vm.toAccount(); vm.submitAccount() // HOUSEHOLD-Schritt, Token gespeichert
        vm.cancel()
        assertEquals(1, fake.disconnects)
    }

    @Test
    fun cancelWithoutLoginOrAfterDoneDoesNotDisconnect() {
        val a = vm(); a.setUrl("https://x.org"); a.submitUrl(); a.cancel()
        fake.loginResult = LoginResult("h1")
        val b = vm(); b.toAccount(); b.submitAccount(); b.cancel()
        assertEquals(0, fake.disconnects)
    }
}

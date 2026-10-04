package de.foody.app.sync

import android.content.Context
import androidx.room.Room
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.impl.WorkManagerImpl
import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.SYNC_CALLBACK
import de.foody.app.data.db.SyncOutboxEntity
import de.foody.sync.protocol.AuthResponse
import de.foody.sync.protocol.DeviceDto
import de.foody.sync.protocol.HouseholdDto
import de.foody.sync.protocol.InviteDto
import de.foody.sync.protocol.LoginRequest
import de.foody.sync.protocol.PullResponse
import de.foody.sync.protocol.PushResponse
import de.foody.sync.protocol.RegisterRequest
import de.foody.sync.protocol.SyncRecord
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Fake-[SyncApi]: der Pull wirft [failure] (oder liefert eine leere Seite), alles andere ist ungenutzt. */
private class ThrowingApi(private val failure: Exception?) : SyncApi {
    override suspend fun push(records: List<SyncRecord>): PushResponse = PushResponse(emptyList())
    override suspend fun pull(since: Long, limit: Int): PullResponse {
        failure?.let { throw it }
        return PullResponse(emptyList(), since, false)
    }

    override suspend fun login(req: LoginRequest): AuthResponse = error("unused")
    override suspend fun register(req: RegisterRequest): AuthResponse = error("unused")
    override suspend fun households(): List<HouseholdDto> = error("unused")
    override suspend fun createHousehold(name: String): HouseholdDto = error("unused")
    override suspend fun selectHousehold(id: String) = error("unused")
    override suspend fun createInvite(): InviteDto = error("unused")
    override suspend fun devices(): List<DeviceDto> = error("unused")
    override suspend fun revokeDevice(id: String) = error("unused")
    override suspend fun photosMissing(hashes: List<String>): List<String> = error("unused")
    override suspend fun uploadPhoto(sha256: String, bytes: ByteArray) = error("unused")
    override suspend fun downloadPhoto(sha256: String): ByteArray? = error("unused")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SyncWorkerTest {
    private lateinit var context: Context
    private lateinit var db: FoodyDatabase

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatabase::class.java)
            .addCallback(FoodyDatabase.SYNC_CALLBACK).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun activate() {
        val dao = db.syncDao()
        dao.upsertState((dao.getState() ?: error("sync_state fehlt")).copy(active = true, serverUrl = "https://x.test"))
    }

    private fun photoIndex() = PhotoIndex(db, RecipePhotoStore(context, db.recipeDao()))

    /** Factory, die eine Engine mit [api] liefert, oder `null` (inaktiv), wenn [api] fehlt. */
    private fun factory(api: SyncApi?) =
        object : SyncEngineFactory(db, SyncLocalStore(db, photoIndex()), SyncApplier(db, photoIndex()), TokenStore(context)) {
            private val engine = api?.let { SyncEngine(db, SyncLocalStore(db, photoIndex()), SyncApplier(db, photoIndex()), it, Clock.systemUTC()) }
            override suspend fun create(): SyncEngine? = engine
        }

    private fun worker(factory: SyncEngineFactory): SyncWorker =
        TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    SyncWorker(appContext, workerParameters, factory, SyncScheduler(context, db, factory))
            })
            .build()

    @Test
    fun transientFailureRetries() = runTest {
        activate()
        val result = worker(factory(ThrowingApi(SyncApiException.Transient(503, null)))).doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun permanentFailureFails() = runTest {
        activate()
        val result = worker(factory(ThrowingApi(SyncApiException.ClientError(400, null)))).doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun unauthorizedSucceedsWithoutRetry() = runTest {
        activate()
        val result = worker(factory(ThrowingApi(SyncApiException.Unauthorized(401, null)))).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun successSucceeds() = runTest {
        activate()
        assertEquals(ListenableWorker.Result.success(), worker(factory(ThrowingApi(null))).doWork())
    }

    private fun nowWork() = WorkManager.getInstance(context).getWorkInfosForUniqueWork(SyncScheduler.NOW_WORK).get()

    @Test
    fun workerRequestsAnotherRunOnlyForEntriesQueuedDuringTheRun() = runTest {
        activate()
        // Stehengebliebener älterer Eintrag (z. B. missing_reference): darf nicht erneut auslösen.
        db.syncDao().enqueue(SyncOutboxEntity("ingredient", "old", deleted = false, queuedAt = 1))
        assertEquals(ListenableWorker.Result.success(), worker(factory(ThrowingApi(null))).doWork())
        assertEquals(0, nowWork().size)
        // Während des Laufs (Pull) vorgemerkt: genau ein weiterer Lauf wird angefordert.
        val api = object : SyncApi by ThrowingApi(null) {
            override suspend fun pull(since: Long, limit: Int): PullResponse {
                db.syncDao().enqueue(SyncOutboxEntity("ingredient", "new", deleted = false, queuedAt = System.currentTimeMillis() + 1))
                return PullResponse(emptyList(), since, false)
            }
        }
        assertEquals(ListenableWorker.Result.success(), worker(factory(api)).doWork())
        assertEquals(1, nowWork().count { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun activeWithoutTokenMarksUnauthorizedAndSucceeds() = runTest {
        activate()
        val noToken = object : TokenStore(context) {
            override fun load(): String? = null
        }
        val real = SyncEngineFactory(db, SyncLocalStore(db, photoIndex()), SyncApplier(db, photoIndex()), noToken)
        assertEquals(ListenableWorker.Result.success(), worker(real).doWork())
        assertEquals("unauthorized", db.syncDao().getState()!!.lastError)
    }

    @Test
    fun inactiveSyncSucceeds() = runTest {
        assertEquals(ListenableWorker.Result.success(), worker(factory(null)).doWork())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SyncSchedulerTest {
    private lateinit var context: Context
    private lateinit var db: FoodyDatabase
    private lateinit var scheduler: SyncScheduler
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        workManager = WorkManager.getInstance(context)
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatabase::class.java)
            .addCallback(FoodyDatabase.SYNC_CALLBACK).allowMainThreadQueries().build()
        scheduler = newScheduler()
    }

    private var running = false

    private fun photoIndex() = PhotoIndex(db, RecipePhotoStore(context, db.recipeDao()))

    private fun newScheduler() = SyncScheduler(
        context, db,
        object : SyncEngineFactory(db, SyncLocalStore(db, photoIndex()), SyncApplier(db, photoIndex()), TokenStore(context)) {
            override val isSyncRunning: Boolean get() = running
        },
    )

    @After
    fun tearDown() = db.close()

    private fun infos(name: String) = workManager.getWorkInfosForUniqueWork(name).get()

    @Test
    fun requestSoonEnqueuesUniqueWorkWithDelay() {
        scheduler.requestSoon()
        scheduler.requestSoon()
        val infos = infos(SyncScheduler.NOW_WORK)
        assertEquals(1, infos.count { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun periodicIsKeptOnSecondCall() {
        scheduler.schedulePeriodic()
        val first = infos(SyncScheduler.PERIODIC_WORK).single()
        scheduler.schedulePeriodic()
        val second = infos(SyncScheduler.PERIODIC_WORK).single()
        assertEquals(first.id, second.id)
        assertEquals(WorkInfo.State.ENQUEUED, second.state)
    }

    @Test
    fun cancelAllCancelsBoth() {
        scheduler.schedulePeriodic()
        scheduler.requestSoon()
        scheduler.cancelAll()
        assertTrue(infos(SyncScheduler.PERIODIC_WORK).all { it.state == WorkInfo.State.CANCELLED })
        assertTrue(infos(SyncScheduler.NOW_WORK).all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun periodicUsesFifteenMinutesAndExponentialBackoff() {
        scheduler.schedulePeriodic()
        val info = infos(SyncScheduler.PERIODIC_WORK).single()
        assertEquals(TimeUnit.MINUTES.toMillis(15), info.periodicityInfo?.repeatIntervalMillis)
        val spec = (workManager as WorkManagerImpl).workDatabase.workSpecDao().getWorkSpec(info.id.toString())!!
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(30), spec.backoffDelayDuration)
    }

    @Test
    fun requestSoonUsesExponentialBackoff() {
        scheduler.requestSoon()
        val info = infos(SyncScheduler.NOW_WORK).single()
        val spec = (workManager as WorkManagerImpl).workDatabase.workSpecDao().getWorkSpec(info.id.toString())!!
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(30), spec.backoffDelayDuration)
    }

    @Test
    fun observerIgnoresIncreasesWhileEngineIsRunning() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val dao = db.syncDao()
            dao.upsertState((dao.getState() ?: error("sync_state fehlt")).copy(active = true))
            running = true
            scheduler.startObservingOutbox(scope)
            dao.enqueue(SyncOutboxEntity("ingredient", "a", deleted = false, queuedAt = 1))
            Thread.sleep(500)
            assertEquals(0, infos(SyncScheduler.NOW_WORK).size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun startObservingIsIdempotent() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            scheduler.startObservingOutbox(scope)
            scheduler.startObservingOutbox(scope)
            assertEquals(1, scope.coroutineContext[Job]!!.children.count())
        } finally {
            scope.cancel()
        }
    }

    private fun waitForNowWork(timeoutMs: Long = 5_000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (infos(SyncScheduler.NOW_WORK).any { it.state == WorkInfo.State.ENQUEUED }) return true
            Thread.sleep(50)
        }
        return false
    }

    @Test
    fun outboxIncreaseRequestsSyncOnlyWhenActive() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            scheduler.startObservingOutbox(scope)
            // Inaktiv: kein Auftrag.
            db.syncDao().enqueue(SyncOutboxEntity("ingredient", "a", deleted = false, queuedAt = 1))
            Thread.sleep(500)
            assertEquals(0, infos(SyncScheduler.NOW_WORK).size)
            // Aktiv: ein weiterer Eintrag löst aus.
            val dao = db.syncDao()
            dao.upsertState((dao.getState() ?: error("sync_state fehlt")).copy(active = true))
            dao.enqueue(SyncOutboxEntity("ingredient", "b", deleted = false, queuedAt = 2))
            assertTrue(waitForNowWork())
            // Abnahme (nach dem Push) löst nichts aus.
            workManager.cancelUniqueWork(SyncScheduler.NOW_WORK)
            dao.dequeue("ingredient", "a")
            Thread.sleep(500)
            assertTrue(infos(SyncScheduler.NOW_WORK).all { it.state == WorkInfo.State.CANCELLED })
            assertNotEquals(0, infos(SyncScheduler.NOW_WORK).size)
        } finally {
            scope.cancel()
        }
    }
}

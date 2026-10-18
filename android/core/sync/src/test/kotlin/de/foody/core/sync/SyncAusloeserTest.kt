package de.foody.core.sync

import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import de.foody.api.protocol.LoginRequest
import de.foody.api.protocol.Protocol
import de.foody.api.protocol.PullResponse
import de.foody.api.protocol.RecordType
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.fotos.FotoIndex
import de.foody.core.data.sync.SyncSpeicher
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Auslöser und Auftrag: Outbox-Änderungen werden entprellt eingeplant (ein Auftrag `foody-sync-v3`, 5 s, mit Netz),
 * inaktiver Sync plant nichts, Vordergrund stößt einen Lauf an, der alte periodische Auftrag der v1 wird abgemeldet.
 * WorkManager im Testmodus: Aufträge laufen erst, wenn der [androidx.work.testing.TestDriver] ihre Bedingungen erfüllt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class SyncAusloeserTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val datenbanken = ArrayList<FoodyDatenbank>()
    private lateinit var bereich: CoroutineScope

    /** Baut den [SyncAuftrag] mit den Teilen des laufenden Tests (wie Hilts Worker-Fabrik in der App). */
    private var auftragTeile: Pair<SyncLauf, SyncAusloeser>? = null
    private val fabrik = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? {
            val (lauf, ausloeser) = auftragTeile ?: return null
            return if (workerClassName == SyncAuftrag::class.java.name) SyncAuftrag(appContext, workerParameters, lauf, ausloeser) else null
        }
    }

    private val workManager get() = WorkManager.getInstance(context)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(fabrik).build(),
        )
        bereich = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        bereich.cancel()
        datenbanken.forEach { it.close() }
    }

    private fun neueDb(): FoodyDatenbank = Room.inMemoryDatabaseBuilder(context, FoodyDatenbank::class.java)
        .addCallback(FoodyDatenbank.CALLBACK)
        .allowMainThreadQueries()
        .build()
        .also { datenbanken += it }

    private inner class Geraet(zugang: suspend () -> ServerZugang?) {
        val db = neueDb()
        val ablage = FotoAblage(context)
        val speicher = SyncSpeicher(db, ablage, FotoIndex(db.syncDao(), ablage))
        val ausloeser = SyncAusloeser(context, speicher, db, bereich)
        val lauf = SyncLauf(speicher, zugang)

        init {
            auftragTeile = lauf to ausloeser
        }

        suspend fun zutat(id: String) = db.zutatDao().upsert(ZutatEntity(id = id, canonicalName = "Zutat $id", createdAt = 1, updatedAt = 1))
    }

    private fun auftraege(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(SyncAusloeser.AUFTRAG).get()

    private fun wartend(): List<WorkInfo> = auftraege().filter { it.state == WorkInfo.State.ENQUEUED }

    /** Wartet (höchstens 5 s), bis [bedingung] gilt – nur auf den Outbox-Flow von Room, nie auf die Entprellung. */
    private suspend fun warteBis(bedingung: () -> Boolean) {
        val ende = System.currentTimeMillis() + 5_000
        while (!bedingung()) {
            check(System.currentTimeMillis() < ende) { "Bedingung nicht erfüllt" }
            delay(10)
        }
    }

    @Test
    fun outboxAenderungPlantEinenAuftrag() = runBlocking {
        val g = Geraet { null }
        g.speicher.aktivieren("http://localhost:1", "konto", allesHochladen = false)
        g.ausloeser.beimStart()

        // Drei Änderungen kurz hintereinander: jede ersetzt den wartenden Auftrag (Entprellung), es bleibt einer.
        var letzte: UUID? = null
        for (id in listOf("a", "b", "c")) {
            g.zutat(id)
            warteBis { wartend().singleOrNull()?.id.let { it != null && it != letzte } }
            letzte = wartend().single().id
        }

        val infos = auftraege()
        assertEquals(1, infos.count { it.state == WorkInfo.State.ENQUEUED }, "genau ein eingeplanter Auftrag")
        assertTrue(infos.filter { it.id != letzte }.all { it.state == WorkInfo.State.CANCELLED }, "frühere ersetzt")
        val auftrag = infos.single { it.id == letzte }
        assertEquals(TimeUnit.SECONDS.toMillis(5), auftrag.initialDelayMillis)
        assertEquals(NetworkType.CONNECTED, auftrag.constraints.requiredNetworkType)
    }

    @Test
    fun inaktiverSyncPlantNichts() = runBlocking {
        val g = Geraet { error("ohne aktiven Sync kein Zugang") }
        g.ausloeser.beimStart()
        g.zutat("a")
        g.db.rezeptDao().upsert(
            RezeptEntity(
                id = "r1", name = "R", defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = null,
                notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
            ),
        )
        g.ausloeser.appImVordergrund().join()
        g.ausloeser.jetzt().join()
        delay(200) // Gelegenheit für den Outbox-Flow; ohne aktiven Sync wächst die Outbox gar nicht.
        assertTrue(g.db.syncDao().outbox().isEmpty())
        assertTrue(auftraege().isEmpty(), "nichts eingeplant")

        // Ein Auftrag, der trotzdem läuft (z. B. aus einer früheren Sitzung), endet erfolgreich ohne Server.
        val auftrag = TestListenableWorkerBuilder<SyncAuftrag>(context).setWorkerFactory(fabrik).build()
        assertEquals(ListenableWorker.Result.success(), auftrag.doWork())
    }

    @Test
    fun vordergrundStoesstLaufAn() = syncServerTest { server ->
        val (name, kontoId) = server.kontoAnlegen()
        var token: String? = null
        val zugang = KtorServerZugang(TEST_BASIS, client) { token }
        token = zugang.anmelden(LoginRequest(name, TEST_PASSWORT, "Testgerät")).token
        val g = Geraet { zugang }
        g.zutat("salz")
        g.speicher.aktivieren(TEST_BASIS, kontoId, allesHochladen = true)

        g.ausloeser.appImVordergrund().join()
        val auftrag = wartend().single()
        assertEquals(0, auftrag.initialDelayMillis, "Vordergrund wartet nicht auf die Entprellung")
        assertEquals(NetworkType.CONNECTED, auftrag.constraints.requiredNetworkType)

        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(auftrag.id)
        warteBis { workManager.getWorkInfoById(auftrag.id).get()!!.state == WorkInfo.State.SUCCEEDED }

        assertTrue(g.db.syncDao().outbox().isEmpty(), "gesendet")
        val stand = zugang.pull(0, Protocol.MAX_PULL_LIMIT).records.map { it.type to it.id }
        assertTrue(RecordType.INGREDIENT to "salz" in stand)
    }

    @Test
    fun aenderungWaehrendDesLaufsPlantNachlauf() = syncServerTest { server ->
        val (name, kontoId) = server.kontoAnlegen()
        var token: String? = null
        val echt = KtorServerZugang(TEST_BASIS, client) { token }
        token = echt.anmelden(LoginRequest(name, TEST_PASSWORT, "Testgerät")).token
        lateinit var g: Geraet
        var einmal = true
        // Nach dem Senden (beim Holen) ändert der Nutzer etwas: das geht erst mit dem nächsten Lauf hinaus.
        val zugang = object : ServerZugang by echt {
            override suspend fun pull(since: Long, limit: Int): PullResponse {
                if (einmal) {
                    einmal = false
                    g.zutat("spaet")
                    delay(300) // dem Outbox-Flow Zeit lassen, während der Lauf noch die Sperre hält
                }
                return echt.pull(since, limit)
            }
        }
        g = Geraet { zugang }
        g.zutat("frueh")
        g.speicher.aktivieren(TEST_BASIS, kontoId, allesHochladen = true)
        g.ausloeser.beimStart()
        // Erst die Beobachtung den Anfangsstand sehen lassen (plant entprellt), dann „Jetzt synchronisieren“.
        warteBis { wartend().isNotEmpty() }
        g.ausloeser.jetzt().join()
        val erster = wartend().single { it.initialDelayMillis == 0L }
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(erster.id)
        warteBis { workManager.getWorkInfoById(erster.id).get()!!.state == WorkInfo.State.SUCCEEDED }

        // Der laufende Auftrag wurde nicht abgebrochen (SUCCEEDED oben), ein weiterer ist eingeplant.
        assertTrue(g.db.syncDao().outbox().any { it.recordId == "spaet" }, "Änderung wartet auf den nächsten Lauf")
        warteBis { wartend().any { it.id != erster.id } }
    }

    @Test
    fun auftragWiederholtNurVoruebergehendeFehler() = runBlocking {
        val offline = Geraet { KtorServerZugang("http://localhost:1", standardHttpClient()) { "token" } }
        offline.speicher.aktivieren("http://localhost:1", "konto", allesHochladen = false)
        val auftrag = TestListenableWorkerBuilder<SyncAuftrag>(context).setWorkerFactory(fabrik).build()
        assertEquals(ListenableWorker.Result.retry(), auftrag.doWork(), "offline: später erneut")

        val ohneToken = Geraet { null }
        ohneToken.speicher.aktivieren("http://localhost:1", "konto", allesHochladen = false)
        val zweiter = TestListenableWorkerBuilder<SyncAuftrag>(context).setWorkerFactory(fabrik).build()
        assertEquals(ListenableWorker.Result.success(), zweiter.doWork(), "Anmeldung abgelaufen: steht in lastError")
        assertEquals(ServerFehler.ANMELDUNG_ABGELAUFEN, ohneToken.speicher.zustand().letzterFehler)
    }

    @Test
    fun alterPeriodischerAuftragWirdAbgemeldet() = runBlocking {
        val g = Geraet { null }
        // Mit Netz-Bedingung wie in der v1: im Testmodus bleibt er so eingeplant, statt sofort zu laufen.
        val alt = PeriodicWorkRequestBuilder<SyncAuftrag>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(SyncAusloeser.ALTER_AUFTRAG, ExistingPeriodicWorkPolicy.KEEP, alt).result.get()
        assertEquals(WorkInfo.State.ENQUEUED, workManager.getWorkInfoById(alt.id).get()!!.state)

        g.ausloeser.beimStart()

        assertEquals("foody-sync-periodic", SyncAusloeser.ALTER_AUFTRAG)
        assertEquals("foody-sync-v3", SyncAusloeser.AUFTRAG)
        assertEquals(WorkInfo.State.CANCELLED, workManager.getWorkInfoById(alt.id).get()!!.state)
    }
}

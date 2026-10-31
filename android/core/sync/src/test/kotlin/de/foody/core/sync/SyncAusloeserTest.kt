package de.foody.core.sync

import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
        val ausloeser = SyncAusloeser(context, speicher, db, bereich).also { it.appOffen = true }
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
        // Die Beobachtung hat den (unveränderten, leeren) Outbox-Stand verarbeitet: ohne aktiven Sync schreiben die
        // Trigger nichts, es kommt kein weiterer Stand.
        warteBis { g.ausloeser.outboxStaendeGesehen.get() >= 1 }
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
                    // Die Beobachtung hat den Zuwachs gesehen und – weil ein Auftrag läuft – zum Nachholen vermerkt.
                    warteBis { g.ausloeser.nachLaufVermerkt }
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
        val vorher = auftraege().map { it.id }.toSet()
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(erster.id)
        warteBis { workManager.getWorkInfoById(erster.id).get()!!.state.isFinished }

        // Der laufende Auftrag wurde nicht ersetzt (REPLACE hätte ihn abgebrochen), genau einer ist hinter ihm eingeplant.
        assertEquals(WorkInfo.State.SUCCEEDED, workManager.getWorkInfoById(erster.id).get()!!.state)
        assertTrue(g.db.syncDao().outbox().any { it.recordId == "spaet" }, "Änderung wartet auf den nächsten Lauf")
        val neu = auftraege().filter { it.id !in vorher }
        assertEquals(1, neu.size)
        assertEquals(WorkInfo.State.ENQUEUED, neu.single().state)
        assertEquals(TimeUnit.SECONDS.toMillis(5), neu.single().initialDelayMillis)
    }

    @Test
    fun ausloeserWaehrendAuftragWirdNachgeholt() = runBlocking {
        val g = Geraet { null }
        g.speicher.aktivieren("http://localhost:1", "konto", allesHochladen = false)
        g.ausloeser.jetzt().join()
        val laufender = wartend().single()

        g.ausloeser.auftragBeginnt()
        g.ausloeser.jetzt().join()
        assertTrue(g.ausloeser.nachLaufVermerkt)
        assertEquals(listOf(laufender.id), auftraege().map { it.id }, "kein neuer Auftrag während des Laufs")
        assertEquals(WorkInfo.State.ENQUEUED, workManager.getWorkInfoById(laufender.id).get()!!.state, "nicht abgebrochen")

        g.ausloeser.nachLauf(SyncErgebnis.Fertig(gesendet = 0, geholt = 0))
        assertFalse(g.ausloeser.nachLaufVermerkt)
        val neu = auftraege().filter { it.id != laufender.id }
        assertEquals(1, neu.size, "genau ein Nachlauf")
        assertEquals(TimeUnit.SECONDS.toMillis(5), neu.single().initialDelayMillis)
        assertNotEquals(WorkInfo.State.CANCELLED, workManager.getWorkInfoById(laufender.id).get()!!.state)
    }

    @Test
    fun gesperrterLaufOhneAuftragVerliertNichts() = runBlocking {
        val g = Geraet { null }
        g.speicher.aktivieren("http://localhost:1", "konto", allesHochladen = false)
        // Die Sperre hält kein Auftrag (z. B. Ersetzen beim Aktivieren): normal einplanen, der Auftrag wartet auf sie.
        g.speicher.laufSperre.lock()
        try {
            g.ausloeser.jetzt().join()
            assertEquals(1, wartend().size)
            assertFalse(g.ausloeser.nachLaufVermerkt)
        } finally {
            g.speicher.laufSperre.unlock()
        }
    }

    @Test
    fun erneuteAenderungEinesVorgemerktenPlantAuftrag() = runBlocking {
        val g = Geraet { null }
        g.speicher.aktivieren("http://localhost:1", "konto", allesHochladen = false)
        g.zutat("a")
        assertEquals(1, g.db.syncDao().outbox().size)
        g.ausloeser.beimStart()
        warteBis { wartend().isNotEmpty() }
        workManager.cancelUniqueWork(SyncAusloeser.AUFTRAG).result.get()
        assertTrue(wartend().isEmpty())

        // Gleiche Zahl an Einträgen, aber der vorgemerkte Datensatz ändert sich erneut.
        g.db.zutatDao().upsert(ZutatEntity(id = "a", canonicalName = "Zutat a neu", createdAt = 1, updatedAt = 2))
        assertEquals(1, g.db.syncDao().outbox().size)
        warteBis { wartend().isNotEmpty() }
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
    fun schliessenSendetWartendeAenderungSofortUndEinmalig() = runBlocking {
        val g = Geraet { null }
        g.speicher.aktivieren("http://localhost:1", "konto", allesHochladen = false)
        g.ausloeser.beimStart()
        g.zutat("a")
        warteBis { wartend().size == 1 }

        // Die App geht in den Hintergrund: nicht 5 s und auf Netz warten, sondern gleich und ohne Bedingung
        g.ausloeser.appImHintergrund().join()
        val auftrag = auftraege().single { it.state != WorkInfo.State.CANCELLED }
        assertEquals(0, auftrag.initialDelayMillis)
        assertEquals(NetworkType.NOT_REQUIRED, auftrag.constraints.requiredNetworkType)
    }

    @Test
    fun geschlosseneAppPlantNichts() = runBlocking {
        val g = Geraet { null }
        g.speicher.aktivieren("http://localhost:1", "konto", allesHochladen = false)
        g.ausloeser.appImHintergrund().join()
        assertTrue(auftraege().none { it.state == WorkInfo.State.ENQUEUED }, "ohne Änderungen: nichts wartet")

        // Eine beim Start offene Änderung zählte bei offener App als Zuwachs (outboxBeobachten)
        g.zutat("a")
        g.ausloeser.beimStart()
        warteBis { g.ausloeser.outboxStaendeGesehen.get() >= 1 }
        g.ausloeser.jetzt().join()
        assertTrue(wartend().isEmpty(), "App zu: Änderungen und Auslöser planen nichts")

        g.ausloeser.nachLauf(SyncErgebnis.Fertig(gesendet = 1, geholt = 0))
        assertTrue(wartend().isEmpty(), "kein Nachlauf im Hintergrund")
    }

    @Test
    fun geschlosseneAppWiederholtNicht() = runBlocking {
        val offline = Geraet { KtorServerZugang("http://localhost:1", standardHttpClient()) { "token" } }
        offline.speicher.aktivieren("http://localhost:1", "konto", allesHochladen = false)
        offline.ausloeser.appOffen = false
        val auftrag = TestListenableWorkerBuilder<SyncAuftrag>(context).setWorkerFactory(fabrik).build()
        assertEquals(ListenableWorker.Result.success(), auftrag.doWork(), "offline und App zu: erst beim Öffnen wieder")
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
        val einmal = OneTimeWorkRequestBuilder<SyncAuftrag>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(SyncAusloeser.ALTER_EINMALAUFTRAG, ExistingWorkPolicy.REPLACE, einmal).result.get()
        assertEquals(WorkInfo.State.ENQUEUED, workManager.getWorkInfoById(einmal.id).get()!!.state)

        g.ausloeser.beimStart()

        assertEquals("foody-sync-periodic", SyncAusloeser.ALTER_AUFTRAG)
        assertEquals("foody-sync-now", SyncAusloeser.ALTER_EINMALAUFTRAG)
        assertEquals(WorkInfo.State.CANCELLED, workManager.getWorkInfoById(einmal.id).get()!!.state)
        assertEquals("foody-sync-v3", SyncAusloeser.AUFTRAG)
        assertEquals(WorkInfo.State.CANCELLED, workManager.getWorkInfoById(alt.id).get()!!.state)
    }
}

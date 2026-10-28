package de.foody.feature.einkauf

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vorschau und „Liste anlegen“, während eine Abfrage noch läuft: Abfragen und Transaktionen laufen auf eigenen Threads
 * hinter einer Schranke, damit ein Tipp (anderer Zeitraum, Schließen, zweiter Tipp) genau in die laufende Arbeit fällt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class EinkaufNebenlaeufigTest {
    private val schranke = AtomicReference(CountDownLatch(0))
    private val abfragen = Executors.newSingleThreadExecutor()
    private val transaktionen = Executors.newSingleThreadExecutor()
    private lateinit var db: FoodyDatenbank
    private lateinit var u: EinkaufTestUmgebung

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK)
            .setQueryExecutor { auftrag -> abfragen.execute { schranke.get().await(10, TimeUnit.SECONDS); auftrag.run() } }
            .setTransactionExecutor { auftrag -> transaktionen.execute { schranke.get().await(10, TimeUnit.SECONDS); auftrag.run() } }
            .build()
        runBlocking {
            db.einkaufBestand()
            db.planen("s1", MITTWOCH, "c")
            db.planen("s2", MITTWOCH.plusDays(10), "p")
        }
        u = EinkaufTestUmgebung(db)
    }

    @After
    fun tearDown() {
        schranke.get().countDown()
        u.viewModels.schliessen()
        db.close()
        abfragen.shutdownNow()
        transaktionen.shutdownNow()
        // Erst wenn die Room-Threads fertig sind: Sonst setzen sie noch Coroutinen auf Dispatchers.Main fort, während
        // resetMain ihn umstellt („Dispatchers.Main is used concurrently with setting it“)
        abfragen.awaitTermination(5, TimeUnit.SECONDS)
        transaktionen.awaitTermination(5, TimeUnit.SECONDS)
        Dispatchers.resetMain()
    }

    private fun warte(bedingung: () -> Boolean) {
        val bis = System.currentTimeMillis() + 10_000
        while (!bedingung() && System.currentTimeMillis() < bis) Thread.sleep(10)
        assertTrue(bedingung(), "Zeit abgelaufen")
    }

    private fun geoeffnet(): EinkaufViewModel {
        val vm = u.vm().apply { anzeigen(null) }
        vm.blattOeffnen(Blatt.Vorschau)
        warte { vm.vorschau.value?.zeilen != null }
        return vm
    }

    @Test
    fun neuerZeitraumGewinnt() {
        val vm = geoeffnet()
        val zu = CountDownLatch(1)
        schranke.set(zu)
        vm.zeitraum(MITTWOCH, MITTWOCH.plusDays(1))
        vm.zeitraum(MITTWOCH.plusDays(9), MITTWOCH.plusDays(11))
        zu.countDown()
        warte { vm.vorschau.value?.zeilen != null }
        Thread.sleep(200)
        val v = vm.vorschau.value!!
        assertEquals(MITTWOCH.plusDays(9), v.von)
        // Nur die Reispfanne (100 g Zwiebeln) liegt im zweiten Zeitraum
        assertEquals("100 g", v.zeilen!!.single { it.name == "Zwiebeln" }.menge)
    }

    @Test
    fun schliessenWaehrendRechnungBleibtZu() {
        val vm = geoeffnet()
        val zu = CountDownLatch(1)
        schranke.set(zu)
        vm.zeitraum(MITTWOCH, MITTWOCH.plusDays(2))
        vm.blattSchliessen()
        zu.countDown()
        Thread.sleep(500)
        assertNull(vm.vorschau.value)
        assertNull(vm.blatt.value)
    }

    @Test
    fun anlegenDoppeltEineListe() {
        val vm = geoeffnet()
        val zu = CountDownLatch(1)
        schranke.set(zu)
        val ergebnisse = runBlocking(Dispatchers.Default) {
            val zwei = listOf(async { vm.anlegen() }, async { vm.anlegen() })
            Thread.sleep(200)
            zu.countDown()
            zwei.awaitAll()
        }
        assertEquals(1, ergebnisse.filterNotNull().size)
        assertEquals(1, runBlocking { db.einkaufslisteDao().alle() }.size)
    }
}

package de.foody.feature.woche

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * „Leere Tage füllen“, während eine Abfrage noch läuft: Die Datenbank-Abfragen laufen auf einem eigenen Thread hinter
 * einer Schranke, damit ein Tipp (Abwählen, Schließen) genau in die laufende Abfrage fällt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class FuellenNebenlaeufigTest {
    private val schranke = AtomicReference(CountDownLatch(0))
    private val abfragen = Executors.newSingleThreadExecutor()
    private lateinit var db: FoodyDatenbank
    private lateinit var u: WocheTestUmgebung

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK)
            .setQueryExecutor { auftrag -> abfragen.execute { schranke.get().await(10, TimeUnit.SECONDS); auftrag.run() } }
            .setTransactionExecutor { it.run() }
            .build()
        runBlocking {
            db.hauptspeisen()
            // Mehr Rezepte als Tage, damit „↻“ eine freie Alternative findet
            listOf("h6" to "Spinatlasagne", "h7" to "Ofengemüse").forEach { (id, name) ->
                db.rezeptDao().upsert(db.rezeptDao().nachId("h1")!!.copy(id = id, name = name))
            }
        }
        u = WocheTestUmgebung(db)
    }

    @After
    fun tearDown() {
        schranke.get().countDown()
        u.viewModels.schliessen()
        db.close()
        abfragen.shutdownNow()
        Dispatchers.resetMain()
    }

    private fun warte(bedingung: () -> Boolean) {
        val bis = System.currentTimeMillis() + 10_000
        while (!bedingung() && System.currentTimeMillis() < bis) Thread.sleep(10)
        assertTrue(bedingung(), "Zeit abgelaufen")
    }

    private fun geoeffnet(): WocheViewModel {
        val vm = u.vm()
        vm.fuellenOeffnen()
        warte { vm.fuellen.value?.vorschlag?.isNotEmpty() == true }
        return vm
    }

    @Test
    fun tagNeuBehaeltAbwahl() {
        val vm = geoeffnet()
        val tage = assertNotNull(vm.fuellen.value).vorschlag.keys.sorted()
        val vorher = vm.fuellen.value!!.vorschlag
        val zu = CountDownLatch(1)
        schranke.set(zu)
        vm.tagNeu(tage.last())
        vm.abwaehlen(tage.first())
        zu.countDown()
        warte { vm.fuellen.value?.vorschlag != vorher }
        Thread.sleep(200)
        assertEquals(setOf(tage.first()), vm.fuellen.value?.abgewaehlt)
    }

    @Test
    fun schliessenWaehrendTagNeuBleibtZu() {
        val vm = geoeffnet()
        val tag = vm.fuellen.value!!.vorschlag.keys.first()
        val zu = CountDownLatch(1)
        schranke.set(zu)
        vm.tagNeu(tag)
        vm.fuellenSchliessen()
        zu.countDown()
        Thread.sleep(500)
        assertNull(vm.fuellen.value)
    }
}

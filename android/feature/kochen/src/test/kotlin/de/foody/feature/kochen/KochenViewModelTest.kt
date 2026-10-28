package de.foody.feature.kochen

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.SavedStateHandle
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.testing.nachProzessende
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class KochenViewModelTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var u: KochTestUmgebung

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        KochTestUmgebung.leeren()
        db = testDatenbank()
        u = KochTestUmgebung(db)
    }

    @After
    fun tearDown() {
        u.viewModels.schliessen()
        db.close()
        Dispatchers.resetMain()
    }

    private fun da(vm: KochenViewModel) = assertIs<KochZustand.Da>(vm.zustand.value)

    @Test
    fun ueberstehtProzessende() = runTest {
        // Hier geänderte Portionen gelten nach einem Prozessende vor denen aus dem Aufruf (R-W7)
        db.reistopf()
        val h = SavedStateHandle()
        val vor = u.vm(h).apply { starten("r1", 2) }
        vor.portionenSchritt(mehr = true)
        vor.seiteWaehlen(1)
        assertEquals(3, da(vor).portionen)

        val nach = u.vm(h.nachProzessende()).apply { starten("r1", 2) }
        assertEquals(3, da(nach).portionen)
        assertEquals(1, da(nach).seite)
    }

    @Test
    fun fertigUeberstehtProzessende() = runTest {
        db.reistopf()
        val h = SavedStateHandle()
        val vor = u.vm(h).apply { starten("r1", null) }
        assertTrue(vor.fertig())

        val nach = u.vm(h.nachProzessende()).apply { starten("r1", null) }
        assertTrue(nach.fertigOffen.value)
        assertFalse(nach.fertig())
    }

    @Test
    fun seitenMitBereitlegen() = runTest {
        db.reistopf()
        val vm = u.vm().apply { starten("r1", null) }
        val z = da(vm)
        assertTrue(z.hatBereitlegen)
        assertEquals(4, z.seitenzahl)
        assertEquals(2, z.portionen)
        assertEquals(listOf("400 g", "2 Stk."), z.bereit.map { it.menge })
    }

    @Test
    fun ohneZutatenKeinBereitlegen() = runTest {
        db.reistopf()
        db.rezeptZutatDao().loeschenFuerRezept("r1")
        val z = da(u.vm().apply { starten("r1", null) })
        assertEquals(false, z.hatBereitlegen)
        assertEquals(3, z.seitenzahl)
    }

    @Test
    fun ohneSchritteNurBereitlegen() = runTest {
        db.reistopf(schritte = emptyList())
        val z = da(u.vm().apply { starten("r1", null) })
        assertTrue(z.hatBereitlegen)
        assertEquals(1, z.seitenzahl)
    }

    @Test
    fun portionenBegrenzt() = runTest {
        db.reistopf()
        assertEquals(1, da(u.vm().apply { starten("r1", 0) }).portionen)
        assertEquals(99, da(u.vm().apply { starten("r1", 500) }).portionen)
    }

    @Test
    fun portionenSchrittSkaliert() = runTest {
        db.reistopf()
        val vm = u.vm().apply { starten("r1", null) }
        vm.portionenSchritt(mehr = true)
        assertEquals("600 g", da(vm).bereit[0].menge)
    }

    @Test
    fun erneuterStartBehaeltGeaendertePortionen() = runTest {
        // Drehen: die Route ruft starten() mit denselben Argumenten noch einmal auf
        db.reistopf()
        val vm = u.vm().apply { starten("r1", 2) }
        vm.portionenSchritt(mehr = true)
        vm.starten("r1", 2)
        assertEquals(3, da(vm).portionen)
        assertEquals(3, u.sitzungen.lesen("r1")?.portionen)
    }

    @Test
    fun sitzungFortsetzen() = runTest {
        db.reistopf()
        u.vm().apply {
            starten("r1", 3)
            abhaken(0)
            seiteWaehlen(2)
        }
        val z = da(u.vm().apply { starten("r1", null) })
        assertEquals(2, z.seite)
        assertEquals(setOf(0), z.abgehakt)
        assertEquals(3, z.portionen)
    }

    @Test
    fun anderePortionenBehaltenSeite() = runTest {
        db.reistopf()
        u.vm().apply {
            starten("r1", 2)
            seiteWaehlen(2)
        }
        val z = da(u.vm().apply { starten("r1", 4) })
        assertEquals(2, z.seite)
        assertEquals(4, z.portionen)
    }

    @Test
    fun rezeptGeaendertBegrenztSeite() = runTest {
        db.reistopf()
        val vm = u.vm().apply { starten("r1", null) }
        vm.seiteWaehlen(3)
        vm.abhaken(1)
        db.arbeitsschrittDao().loeschen("s2")
        assertEquals(2, da(vm).seite)
        db.rezeptZutatDao().loeschen("l2")
        assertEquals(emptySet(), da(vm).abgehakt)
    }

    @Test
    fun geloeschtNichtGefunden() = runTest {
        db.reistopf()
        val vm = u.vm().apply { starten("r1", null) }
        vm.abhaken(0)
        u.timer.starten("r1", 2, "Reistopf", "20 Minuten", Duration.ofMinutes(20))
        db.rezeptDao().loeschen("r1")
        assertIs<KochZustand.NichtGefunden>(vm.zustand.value)
        assertEquals(1, vm.timer.value.size)
        assertNull(u.sitzungen.lesen("r1"))
    }

    @Test
    fun fertigMarkiertEinmal() = runTest {
        db.reistopf()
        db.planHeute("p1")
        db.planHeute("p2")
        val vm = u.vm().apply { starten("r1", null) }
        val ergebnisse = coroutineScope { listOf(async { vm.fertig() }, async { vm.fertig() }).map { it.await() } }
        assertEquals(1, ergebnisse.count { it })
        assertEquals(1, db.planEintragDao().alle().count { it.cookedAt != null })
        assertTrue(vm.fertigOffen.value)
    }

    @Test
    fun fertigLoeschtSitzung() = runTest {
        db.reistopf()
        val vm = u.vm().apply { starten("r1", null) }
        vm.seiteWaehlen(3)
        vm.fertig()
        // Spätere Änderungen (z. B. das neue Foto) schreiben keine Sitzung mehr
        db.rezeptDao().bildSetzen("r1", "file:///foto.jpg", 9)
        assertNull(u.sitzungen.lesen("r1"))
    }

    @Test
    fun fertigOhnePlanEintrag() = runTest {
        db.reistopf()
        val vm = u.vm().apply { starten("r1", null) }
        assertTrue(vm.fertig())
        assertTrue(vm.fertigOffen.value)
        assertTrue(db.planEintragDao().alle().isEmpty())
    }

    @Test
    fun kamerafotoNachDemVerlassenAufgeraeumt() = runTest {
        // Foto aufnehmen und sofort „Schließen“: die Übernahme läuft weiter, die Kameradatei wird gelöscht
        db.reistopf()
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { u.vm() } })[KochenViewModel::class]
        vm.starten("r1", null)
        val ordner = File(RuntimeEnvironment.getApplication().cacheDir, "kamera").apply { mkdirs() }
        val datei = File(ordner, "test.jpg").apply { writeBytes(ByteArray(16)) }
        vm.fotoUebernehmen(Uri.fromFile(datei), datei)
        store.clear()
        val bis = System.currentTimeMillis() + 5_000
        while (datei.exists() && System.currentTimeMillis() < bis) Thread.sleep(20)
        assertFalse(datei.exists())
    }

    @Test
    fun schliessenLoeschtSitzung() = runTest {
        db.reistopf()
        val vm = u.vm().apply { starten("r1", null) }
        vm.abhaken(0)
        vm.schliessen()
        vm.abhaken(1)
        assertNull(u.sitzungen.lesen("r1"))
    }

    @Test
    fun timerStartenMitRezept() = runTest {
        db.reistopf()
        val vm = u.vm().apply { starten("r1", 3) }
        val t = da(vm).schritte[1].timer.single()
        val id = vm.timerStarten(t)
        val laufend = vm.timer.value.single()
        assertEquals(id, laufend.id)
        assertEquals("Reistopf", laufend.rezeptName)
        assertEquals(3, laufend.portionen)
        vm.timerEntfernen(id)
        assertTrue(vm.timer.value.isEmpty())
    }
}

package de.foody.feature.woche

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.domain.Mahlzeit
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
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class WocheViewModelTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var u: WocheTestUmgebung

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = testDatenbank()
        u = WocheTestUmgebung(db)
    }

    @After
    fun tearDown() {
        u.viewModels.schliessen()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun startetMitAktuellerWoche() = runTest {
        val vm = u.vm()
        assertEquals(MONTAG, vm.woche.value?.montag)
        assertTrue(vm.zeigtHeute.value)
    }

    @Test
    fun blaetternUndHeute() = runTest {
        val vm = u.vm()
        vm.blaettern(1)
        assertEquals(MONTAG.plusWeeks(1), vm.woche.value?.montag)
        assertFalse(vm.zeigtHeute.value)
        vm.blaettern(-2)
        assertEquals(MONTAG.minusWeeks(1), vm.woche.value?.montag)
        vm.heute()
        assertEquals(MONTAG, vm.woche.value?.montag)
    }

    @Test
    fun montagUeberstehtNeuaufbau() = runTest {
        val saved = SavedStateHandle()
        u.vm(saved).blaettern(2)
        assertEquals(MONTAG.plusWeeks(2), u.vm(saved).woche.value?.montag)
    }

    @Test
    fun fuellenNimmtHeuteBeimOeffnen() = runTest {
        db.hauptspeisen()
        val vm = u.vm()
        u.uhr.auf(MITTWOCH.plusDays(2))
        vm.fuellenOeffnen()
        assertEquals((4L..6L).map { MONTAG.plusDays(it) }, vm.fuellen.value?.tage)
    }

    @Test
    fun uebernehmenEinmal() = runTest {
        db.hauptspeisen()
        val vm = u.vm()
        vm.fuellenOeffnen()
        val n = assertNotNull(vm.fuellen.value).vorschlag.size
        val ergebnisse = coroutineScope { listOf(async { vm.uebernehmen() }, async { vm.uebernehmen() }).map { it.await() } }
        assertEquals(listOf(n), ergebnisse.filterNotNull())
        assertEquals(n, db.planEintragDao().alle().size)
        assertNull(vm.fuellen.value)
    }

    @Test
    fun tagNeuAendertNurDiesenTag() = runTest {
        db.hauptspeisen()
        // Die fünf Hauptspeisen füllen Mi–So; frei ist nur ein Rezept ohne bekannten Gang (zweite Stufe)
        db.rezeptDao().upsert(db.rezeptDao().nachId("h1")!!.copy(id = "h6", name = "Ofengemüse", gaenge = ""))
        val vm = u.vm()
        vm.fuellenOeffnen()
        val vorher = assertNotNull(vm.fuellen.value).vorschlag
        val tag = vorher.keys.first()
        vm.tagNeu(tag)
        val nachher = assertNotNull(vm.fuellen.value).vorschlag
        assertEquals("h6", nachher[tag])
        assertEquals(vorher - tag, nachher - tag)
        assertEquals(nachher.values.size, nachher.values.toSet().size, "kein Rezept doppelt")
    }

    @Test
    fun tagNeuOhneAlternativeMeldet() = runTest {
        // Fünf Hauptspeisen auf fünf Tage: für keinen Tag gibt es ein anderes Rezept
        db.hauptspeisen()
        val vm = u.vm()
        vm.fuellenOeffnen()
        val vorher = assertNotNull(vm.fuellen.value).vorschlag
        val tag = vorher.keys.first()
        vm.tagNeu(tag)
        val z = assertNotNull(vm.fuellen.value)
        assertEquals(vorher, z.vorschlag)
        assertEquals("Kein anderes passendes Rezept für ${tagKurz(tag)}", z.meldung)
        vm.abwaehlen(tag)
        assertNull(vm.fuellen.value?.meldung)
    }

    @Test
    fun abwaehlenWirdNichtAngelegt() = runTest {
        db.hauptspeisen()
        val vm = u.vm()
        vm.fuellenOeffnen()
        val tag = assertNotNull(vm.fuellen.value).vorschlag.keys.first()
        vm.abwaehlen(tag)
        val n = vm.uebernehmen()
        assertEquals(assertNotNull(n), db.planEintragDao().alle().size)
        assertFalse(db.planEintragDao().alle().any { it.date == tag })
    }

    @Test
    fun ohnePassendeHinweis() = runTest {
        val vm = u.vm()
        vm.fuellenOeffnen()
        assertEquals("Keine passenden Rezepte – alles schon in den letzten 14 Tagen geplant", vm.fuellen.value?.hinweis)
    }

    @Test
    fun ohneLeereTageHinweis() = runTest {
        db.hauptspeisen()
        val vm = u.vm()
        (2L..6L).forEach { vm.hinzufuegen(MONTAG.plusDays(it), "ABENDESSEN", "h1", 2) }
        vm.fuellenOeffnen(Mahlzeit.ABENDESSEN)
        assertEquals("Alle Tage ab heute haben schon ein Abendessen", vm.fuellen.value?.hinweis)
    }

    @Test
    fun entfernenUndRueckgaengig() = runTest {
        db.hauptspeisen()
        val vm = u.vm()
        vm.hinzufuegen(MITTWOCH, "ABENDESSEN", "h1", 2)
        val id = db.planEintragDao().alle().single().id
        val weg = assertNotNull(vm.entfernen(id))
        assertTrue(db.planEintragDao().alle().isEmpty())
        vm.wiederherstellen(weg)
        assertEquals(id, db.planEintragDao().alle().single().id)
    }
}

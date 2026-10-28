package de.foody.feature.einkauf

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class EinkaufViewModelTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var u: EinkaufTestUmgebung

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = testDatenbank()
        u = EinkaufTestUmgebung(db)
    }

    @After
    fun tearDown() {
        u.viewModels.schliessen()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun folgtAktuellerListe() = runTest {
        db.einkaufslisteDao().upsert(liste("alt", 1))
        val vm = u.vm().apply { anzeigen(null) }
        assertEquals("alt", vm.zustand.value.ansicht?.id)
        db.einkaufslisteDao().upsert(liste("neu", 2))
        assertEquals("neu", vm.zustand.value.ansicht?.id)
    }

    @Test
    fun bestimmteListe() = runTest {
        db.einkaufslisteDao().upsert(liste("alt", 1))
        db.einkaufslisteDao().upsert(liste("neu", 2))
        val vm = u.vm().apply { anzeigen("alt") }
        assertEquals("alt", vm.zustand.value.ansicht?.id)
    }

    @Test
    fun listeFehltGemeldet() = runTest {
        val vm = u.vm().apply { anzeigen("weg") }
        assertTrue(vm.zustand.value.geladen)
        assertTrue(vm.zustand.value.listeFehlt)
        val leer = u.vm().apply { anzeigen(null) }
        assertEquals(false, leer.zustand.value.listeFehlt)
        assertNull(leer.zustand.value.ansicht)
    }

    @Test
    fun vorschauVorbelegtSiebenTage() = runTest {
        db.einkaufBestand()
        db.planen("s1", MITTWOCH, "c")
        val vm = u.vm().apply { anzeigen(null) }
        vm.blattOeffnen(Blatt.Vorschau)
        val v = assertNotNull(vm.vorschau.value)
        assertEquals(MITTWOCH, v.von)
        assertEquals(MITTWOCH.plusDays(6), v.bis)
        assertEquals(listOf("Zwiebeln", "Reis"), v.zeilen?.map { it.name })
    }

    @Test
    fun zeitraumGrenzen() = runTest {
        val vm = u.vm().apply { anzeigen(null) }
        vm.blattOeffnen(Blatt.Vorschau)
        vm.zeitraum(MITTWOCH, MITTWOCH.minusDays(3))
        assertEquals(MITTWOCH, vm.vorschau.value?.bis)
        vm.zeitraum(MITTWOCH, MITTWOCH.plusDays(28))
        assertEquals(MITTWOCH.plusDays(27), vm.vorschau.value?.bis)
    }

    @Test
    fun anlegenZeigtNeueListe() = runTest {
        db.einkaufBestand()
        db.planen("s1", MITTWOCH, "c")
        db.einkaufslisteDao().upsert(liste("alt", 1))
        val vm = u.vm().apply { anzeigen("alt") }
        vm.blattOeffnen(Blatt.Vorschau)
        vm.vorschauUmschalten("z-reis|MASS")
        val id = assertNotNull(vm.anlegen())
        assertEquals(id, vm.zustand.value.ansicht?.id)
        assertEquals(listOf("Zwiebeln"), vm.zustand.value.ansicht?.alle?.map { it.name })
        assertNull(vm.blatt.value)
    }

    @Test
    fun abgleichenUndHinweis() = runTest {
        db.einkaufBestand()
        db.planen("s1", MITTWOCH, "c")
        val vm = u.vm().apply { anzeigen(null) }
        vm.blattOeffnen(Blatt.Vorschau)
        vm.anlegen()
        assertTrue(vm.zustand.value.abgleich.isEmpty())
        db.planen("s1", MITTWOCH, "c", portionen = 4)
        assertEquals(2, vm.zustand.value.abgleich.size)
        vm.blattOeffnen(Blatt.Abgleich)
        vm.abgleichUmschalten("z-reis|MASS")
        assertEquals(1, vm.abgleichen())
        assertTrue(vm.zustand.value.abgleich.isEmpty())
        assertNull(vm.blatt.value)
    }

    @Test
    fun blattUndZeitraumUeberstehenNeuaufbau() = runTest {
        val saved = SavedStateHandle()
        u.vm(saved).apply {
            anzeigen(null)
            blattOeffnen(Blatt.Vorschau)
            zeitraum(MITTWOCH.plusDays(1), MITTWOCH.plusDays(3))
        }
        val neu = u.vm(saved).apply { anzeigen(null) }
        assertEquals(Blatt.Vorschau, neu.blatt.value)
        assertEquals(MITTWOCH.plusDays(1), neu.vorschau.value?.von)
        assertEquals(MITTWOCH.plusDays(3), neu.vorschau.value?.bis)
        u.vm(saved).apply { blattOeffnen(Blatt.Details("a1")) }
        assertEquals(Blatt.Details("a1"), u.vm(saved).blatt.value)
    }

    @Test
    fun routeUeberschreibtNichtNachAnlegen() = runTest {
        // Nach Prozessende ruft die Route anzeigen("alt") erneut auf – die neu angelegte Liste bleibt sichtbar
        db.einkaufBestand()
        db.planen("s1", MITTWOCH, "c")
        db.einkaufslisteDao().upsert(liste("alt", 1))
        val saved = SavedStateHandle()
        val vm = u.vm(saved).apply { anzeigen("alt") }
        vm.blattOeffnen(Blatt.Vorschau)
        val id = assertNotNull(vm.anlegen())
        assertEquals(id, u.vm(saved).apply { anzeigen("alt") }.zustand.value.ansicht?.id)
    }

    @Test
    fun loeschenUndRueckgaengig() = runTest {
        db.einkaufslisteDao().upsert(liste("l", 1))
        db.einkaufsartikelDao().upsert(artikel("a1", "l", "Milch"))
        val vm = u.vm().apply { anzeigen(null) }
        val weg = assertNotNull(vm.loeschen("a1"))
        assertTrue(vm.zustand.value.ansicht!!.alle.isEmpty())
        vm.wiederherstellen(weg)
        assertEquals(listOf("Milch"), vm.zustand.value.ansicht?.alle?.map { it.name })
    }

    @Test
    fun eingebenLeertSuche() = runTest {
        val vm = u.vm().apply { anzeigen(null) }
        vm.suche("Grillkohle")
        assertEquals("Grillkohle", vm.zustand.value.suche)
        vm.eingeben()
        assertEquals("", vm.zustand.value.suche)
        assertEquals(listOf("Grillkohle"), vm.zustand.value.ansicht?.alle?.map { it.name })
    }

    @Test
    fun listeLoeschenFolgtAktueller() = runTest {
        db.einkaufslisteDao().upsert(liste("alt", 1))
        db.einkaufslisteDao().upsert(liste("neu", 2))
        val vm = u.vm().apply { anzeigen("neu") }
        vm.listeLoeschen()
        assertEquals("alt", vm.zustand.value.ansicht?.id)
    }
}

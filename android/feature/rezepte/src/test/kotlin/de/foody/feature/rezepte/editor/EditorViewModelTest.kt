package de.foody.feature.rezepte.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.rezepte.Gespeichert
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.domain.Mahlzeit
import de.foody.feature.rezepte.bestand
import de.foody.feature.rezepte.testDatenbank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class EditorViewModelTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var vm: EditorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = testDatenbank()
        val ablage = FotoAblage(RuntimeEnvironment.getApplication())
        vm = EditorViewModel(RezeptRepository(db, ablage) { 1_000L }, db, ablage)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun neu(name: String = "Bowl", zutaten: String = "200 g Reis") {
        vm.laden(null)
        vm.aendern { it.copy(name = name, zutatenText = zutaten) }
    }

    @Test
    fun zeilenVorschauMitKatalog() = runTest {
        db.bestand()
        neu(zutaten = "200 g Reis\n1 Bund Bärlauch")
        val z = vm.zustand.first().zeilen
        assertEquals(listOf(true, false), z.map { it.imKatalog })
        assertEquals("Bärlauch", z[1].erkannt?.name)
        assertTrue(vm.speichernMoeglich.first())
    }

    @Test
    fun unerkannteZeileSperrt() = runTest {
        neu(zutaten = "200 g")
        assertEquals("Keine Zutat erkannt", vm.zustand.first().zeilen.single().fehler)
        assertFalse(vm.speichernMoeglich.first())
    }

    @Test
    fun zuGrosseMengeSperrt() = runTest {
        neu(zutaten = "1e5 g Mehl")
        assertFalse(vm.speichernMoeglich.first())
        neu(zutaten = "1234567890123456789012345678901 g Mehl")
        assertFalse(vm.speichernMoeglich.first())
    }

    @Test
    fun portionenGrenzen() = runTest {
        neu()
        vm.aendern { it.copy(portionen = "0") }
        assertFalse(vm.speichernMoeglich.first())
        vm.aendern { it.copy(portionen = "100") }
        assertFalse(vm.speichernMoeglich.first())
        vm.aendern { it.copy(portionen = "4") }
        assertTrue(vm.speichernMoeglich.first())
    }

    @Test
    fun rundwegBearbeiten() = runTest {
        db.bestand()
        vm.laden("r1")
        assertEquals("400 g Reis", vm.zustand.first().eingaben.zutatenText)
        assertFalse(vm.geaendert.first())
        assertIs<Gespeichert.Ok>(vm.speichern())
        assertEquals(1, db.rezeptZutatDao().nachRezept("r1").size)
    }

    @Test
    fun doppeltippEinRezept() = runTest {
        neu()
        val ergebnisse = coroutineScope { listOf(async { vm.speichern() }, async { vm.speichern() }).map { it.await() } }
        assertEquals(1, ergebnisse.filterIsInstance<Gespeichert.Ok>().size)
        assertEquals(1, db.rezeptDao().alle().size)
    }

    @Test
    fun geloeschtMeldet() = runTest {
        db.bestand()
        vm.laden("r1")
        db.rezeptDao().loeschen("r1")
        assertEquals(Gespeichert.Geloescht, vm.speichern())
        assertEquals("Rezept wurde inzwischen gelöscht", vm.zustand.first().hinweis)
        assertTrue(db.rezeptDao().alle().isEmpty())
    }

    @Test
    fun automatischeEinordnung() = runTest {
        neu(name = "Porridge")
        vm.mahlzeitUmschalten(Mahlzeit.FRUEHSTUECK)
        assertNotNull(vm.zustand.first().eingaben.mahlzeiten)
        vm.automatisch()
        assertNull(vm.zustand.first().eingaben.mahlzeiten)
        assertNull(vm.zustand.first().eingaben.gaenge)
    }
}

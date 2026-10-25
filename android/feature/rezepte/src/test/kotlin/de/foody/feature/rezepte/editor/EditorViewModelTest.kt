package de.foody.feature.rezepte.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.rezepte.Gespeichert
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
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
import java.math.BigDecimal
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
    fun katalognamenBleibenBeimBearbeiten() = runTest {
        // Katalognamen mit Klammer, Komma oder kleinem Wort: unveränderte Zeilen behalten Zutat und Hinweis
        val namen = listOf("z-roh" to "Reis (roh)", "z-milch" to "Milch 3,5 %", "z-joghurt" to "Joghurt griechisch")
        namen.forEach { (id, name) -> db.zutatDao().upsert(ZutatEntity(id = id, canonicalName = name, createdAt = 1, updatedAt = 1)) }
        db.rezeptDao().upsert(
            RezeptEntity(
                id = "r2", name = "Bowl", defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = null,
                notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
            ),
        )
        namen.forEachIndexed { i, (id, _) ->
            db.rezeptZutatDao().upsert(RezeptZutatEntity("l$i", "r2", id, BigDecimal("100"), MeasureUnit.GRAM, i, null, false))
        }
        repeat(2) { runde ->
            val ablage = FotoAblage(RuntimeEnvironment.getApplication())
            val editor = EditorViewModel(RezeptRepository(db, ablage) { 1_000L }, db, ablage)
            editor.laden("r2")
            editor.aendern { it.copy(schritteText = "Schritt $runde") }
            assertIs<Gespeichert.Ok>(editor.speichern())
        }
        val zeilen = db.rezeptZutatDao().nachRezept("r2")
        assertEquals(listOf("z-roh", "z-milch", "z-joghurt"), zeilen.map { it.ingredientId })
        assertEquals(listOf(null, null, null), zeilen.map { it.preparationNote })
        assertEquals(3, db.zutatDao().alle().size)
    }

    @Test
    fun fotoVomSyncBleibt() = runTest {
        // Der Sync bringt das Foto, während der Editor offen ist; Speichern ohne Fotowahl behält es
        db.bestand()
        vm.laden("r1")
        db.rezeptDao().bildSetzen("r1", "file:///sync/foto.jpg", 5)
        vm.aendern { it.copy(name = "Reispfanne neu") }
        assertIs<Gespeichert.Ok>(vm.speichern())
        assertEquals("file:///sync/foto.jpg", db.rezeptDao().nachId("r1")?.imageUri)
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

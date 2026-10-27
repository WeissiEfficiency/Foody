package de.foody.feature.zutaten

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.scan.ScanAusgang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** „Neue Zutat“ mit Packung (Phase 6e3): anlegen, „Gibt es schon“ → „Öffnen“, „im Katalog“; Weitergabe an den Editor. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class ZutatenListeScanTest {
    private lateinit var u: ZutatenTestUmgebung

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        u = ZutatenTestUmgebung()
    }

    @After
    fun tearDown() {
        u.db.close()
        Dispatchers.resetMain()
    }

    private fun <T> ereignisse(fluss: kotlinx.coroutines.flow.Flow<T>): MutableList<T> {
        val liste = CopyOnWriteArrayList<T>()
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.Default) { fluss.collect { liste += it } }
        return liste
    }

    /** Öffnet den Editor für [id] wie die App (neues ViewModel, laden). */
    private fun editor(id: String): Pair<ZutatEditorViewModel, MutableList<EditorEreignis>> {
        val vm = u.editor()
        val e = ereignisse(vm.ereignisse)
        vm.laden(id)
        warte { vm.zustand.value.geladen }
        return vm to e
    }

    @Test
    fun anlegenMitPackung() {
        val vm = u.liste()
        val e = ereignisse(vm.ereignisse)
        vm.packungFuerNeu(haferflocken())
        assertEquals(haferflocken(), vm.neuPackung.value)
        vm.anlegen("Haferflocken")
        warte { e.isNotEmpty() }
        val id = assertIs<ListeEreignis.Oeffnen>(e.single()).id
        val z = assertNotNull(runBlocking { u.db.zutatDao().nachId(id) })
        assertEquals("Haferflocken", z.canonicalName)
        assertEquals(0, BigDecimal("1502").compareTo(z.energyKj))
        assertEquals(0, BigDecimal("13.5").compareTo(z.protein))
        assertEquals("Open Food Facts", z.nutrientSource)
        assertEquals(TEST_CODE, z.barcode)
        assertNull(vm.neuPackung.value)
    }

    @Test
    fun namenVorfuellen() {
        assertEquals("Kernige Haferflocken", nameAusPackung(haferflocken(name = "  Kernige   Haferflocken ")))
        assertEquals(200, nameAusPackung(haferflocken(name = "x".repeat(250))).length)
        assertEquals("", nameAusPackung(haferflocken(name = null)))
    }

    @Test
    fun gibtEsSchonNimmtPackungMit() {
        u.zutat("h", "Haferflocken")
        val vm = u.liste()
        val e = ereignisse(vm.ereignisse)
        vm.packungFuerNeu(haferflocken())
        vm.anlegen("haferflocken")
        warte { e.isNotEmpty() }
        assertEquals(ListeEreignis.GibtEsSchon("h", "Haferflocken"), e.single())
        vm.vorhandeneOeffnen("h")
        warte { e.size == 2 }
        assertEquals(ListeEreignis.Oeffnen("h"), e.last())
        assertNull(vm.neuPackung.value)

        val (editor, _) = editor("h")
        warte { editor.zustand.value.markiert.isNotEmpty() }
        assertEquals("13,5", editor.zustand.value.entwurf?.eiweiss)
        assertEquals("Open Food Facts", editor.zustand.value.scanQuelle)
        // Nur einmal abgeholt: ein zweiter Editor beginnt ohne Packung
        val (zweiter, _) = editor("h")
        assertEquals(emptySet(), zweiter.zustand.value.markiert)
    }

    @Test
    fun imKatalogOeffnetMitMeldung() {
        u.zutat("h", "Haferflocken") { it.copy(barcode = TEST_CODE) }
        val vm = u.liste()
        val e = ereignisse(vm.ereignisse)
        vm.packungFuerNeu(haferflocken())
        vm.imKatalog(ScanAusgang.ImKatalog("h", "Haferflocken", TEST_CODE))
        warte { e.isNotEmpty() }
        assertEquals(ListeEreignis.Oeffnen("h"), e.single())
        assertNull(vm.neuPackung.value)

        val (editor, ereignisse) = editor("h")
        warte { EditorEreignis.Meldung("Schon im Katalog") in ereignisse }
        assertEquals(emptySet(), editor.zustand.value.markiert)
    }
}

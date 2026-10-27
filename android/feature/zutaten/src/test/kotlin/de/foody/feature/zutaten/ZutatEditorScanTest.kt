package de.foody.feature.zutaten

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.zutaten.ZutatEntwurf
import de.foody.core.data.zutaten.ZutatFeld
import de.foody.core.scan.ScanAusgang
import de.foody.core.scan.ScanTexte
import de.foody.domain.NutrientBasis
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Zutateneditor mit „Packung scannen“ (Phase 6e3): übernehmen, markieren, speichern, „im Katalog“, „Online abrufen“. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class ZutatEditorScanTest {
    private lateinit var u: ZutatenTestUmgebung
    private val ereignisse = CopyOnWriteArrayList<EditorEreignis>()

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

    private fun editor(id: String): ZutatEditorViewModel {
        val vm = u.editor()
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.Default) { vm.ereignisse.collect { ereignisse += it } }
        vm.laden(id)
        warte { vm.zustand.value.geladen }
        return vm
    }

    private fun ZutatEditorViewModel.entwurf(): ZutatEntwurf = assertNotNull(zustand.value.entwurf)

    /** Reis mit 1500 kJ (wie 1502 kJ „359“ kcal) und Eiweiß 7. */
    private fun reis(quelle: String? = null, strichcode: String? = null) = u.zutat("seed-reis", "Reis") {
        it.copy(
            nutrientBasis = NutrientBasis.PER_100_G, energyKj = BigDecimal("1500"), protein = BigDecimal("7"),
            nutrientSource = quelle, barcode = strichcode,
        )
    }

    private fun gespeichert() = runBlocking { assertNotNull(u.db.zutatDao().nachId("seed-reis")) }

    @Test
    fun uebernimmtUndMarkiert() {
        reis()
        val vm = editor("seed-reis")
        vm.packungUebernehmen(haferflocken())
        val e = vm.entwurf()
        assertEquals("359", e.kcal)
        assertEquals("13,5", e.eiweiss)
        assertEquals("58,7", e.kohlenhydrate)
        assertEquals(TEST_CODE, e.strichcode)
        assertEquals("Open Food Facts", e.quelle)
        assertEquals(
            setOf(ZutatFeld.KCAL, ZutatFeld.EIWEISS, ZutatFeld.KOHLENHYDRATE, ZutatFeld.ZUCKER, ZutatFeld.FETT, ZutatFeld.STRICHCODE),
            vm.zustand.value.markiert,
        )
        assertEquals("Open Food Facts", vm.zustand.value.scanQuelle)
    }

    @Test
    fun aendernHebtMarkierungAuf() {
        reis()
        val vm = editor("seed-reis")
        vm.packungUebernehmen(haferflocken())
        vm.aendern(vm.entwurf().copy(eiweiss = "14", dichte = "0,8"))
        assertEquals(false, ZutatFeld.EIWEISS in vm.zustand.value.markiert)
        assertEquals(true, ZutatFeld.KCAL in vm.zustand.value.markiert)
        // Zurück auf den Scanwert markiert nicht wieder
        vm.aendern(vm.entwurf().copy(eiweiss = "13,5"))
        assertEquals(false, ZutatFeld.EIWEISS in vm.zustand.value.markiert)
    }

    @Test
    fun speichernGenaueKj() {
        reis()
        val vm = editor("seed-reis")
        vm.packungUebernehmen(haferflocken(kj = "1502"))
        assertEquals("359", vm.entwurf().kcal)
        vm.speichern()
        warte { EditorEreignis.Fertig in ereignisse }
        assertEquals(0, BigDecimal("1502").compareTo(gespeichert().energyKj))
    }

    @Test
    fun speichernBehaeltScanQuelle() {
        reis(quelle = "Open Food Facts")
        val vm = editor("seed-reis")
        vm.packungUebernehmen(haferflocken())
        vm.aendern(vm.entwurf().copy(eiweiss = "14"))
        vm.speichern()
        warte { EditorEreignis.Fertig in ereignisse }
        val z = gespeichert()
        assertEquals("Open Food Facts", z.nutrientSource)
        assertEquals(0, BigDecimal("14").compareTo(z.protein))
    }

    @Test
    fun imKatalogSelbst() {
        reis(strichcode = TEST_CODE)
        val vm = editor("seed-reis")
        vm.imKatalog(ScanAusgang.ImKatalog("seed-reis", "Reis", TEST_CODE))
        warte { EditorEreignis.Meldung("Diese Packung gehört schon zu dieser Zutat") in ereignisse }
        assertEquals(TEST_CODE, vm.zustand.value.imKatalogSelbst)
    }

    @Test
    fun onlineAbrufenUebernimmt() {
        reis(strichcode = TEST_CODE)
        u.scan.packung = haferflocken()
        val vm = editor("seed-reis")
        vm.imKatalog(ScanAusgang.ImKatalog("seed-reis", "Reis", TEST_CODE))
        vm.onlineAbrufen()
        warte { vm.zustand.value.markiert.isNotEmpty() && !vm.zustand.value.laeuft }
        assertEquals("13,5", vm.entwurf().eiweiss)
        assertEquals(1, u.scan.anfragen.get())
        assertNull(vm.zustand.value.imKatalogSelbst)
    }

    @Test
    fun onlineAbrufenNichtGefundenMeldet() {
        reis(strichcode = TEST_CODE)
        val vm = editor("seed-reis")
        vm.imKatalog(ScanAusgang.ImKatalog("seed-reis", "Reis", TEST_CODE))
        vm.onlineAbrufen()
        warte { EditorEreignis.Meldung(ScanTexte.NICHT_GEFUNDEN) in ereignisse }
        assertEquals(emptySet(), vm.zustand.value.markiert)
    }

    /** Review Focus 5: Online-Suche aus → kein Knopf, und auch ein Aufruf geht nicht hinaus. */
    @Test
    fun onlineAusKeinAbruf() {
        reis(strichcode = TEST_CODE)
        u.scan.packung = haferflocken()
        u.scan.online = false
        val vm = editor("seed-reis")
        vm.imKatalog(ScanAusgang.ImKatalog("seed-reis", "Reis", TEST_CODE))
        warte { EditorEreignis.Meldung("Diese Packung gehört schon zu dieser Zutat") in ereignisse }
        assertNull(vm.zustand.value.imKatalogSelbst)
        vm.onlineAbrufen()
        // Erst danach ausgeschaltet: Der Abruf meldet „ist aus“, ohne zu fragen
        u.scan.online = true
        vm.imKatalog(ScanAusgang.ImKatalog("seed-reis", "Reis", TEST_CODE))
        u.scan.online = false
        vm.onlineAbrufen()
        warte { EditorEreignis.Meldung(ScanTexte.ONLINE_AUS) in ereignisse }
        assertEquals(0, u.scan.anfragen.get())
        assertEquals(emptySet(), vm.zustand.value.markiert)
    }

    @Test
    fun imKatalogAndereZutat() {
        reis()
        u.zutat("z", "Zwiebel") { it.copy(barcode = TEST_CODE) }
        val vm = editor("seed-reis")
        vm.imKatalog(ScanAusgang.ImKatalog("z", "Zwiebel", TEST_CODE))
        warte { ereignisse.isNotEmpty() }
        assertEquals(EditorEreignis.StrichcodeGehoert("z", "Zwiebel"), ereignisse.single())
        assertNull(vm.zustand.value.imKatalogSelbst)
    }
}

package de.foody.feature.zutaten

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.zutaten.ZutatEntwurf
import de.foody.core.data.zutaten.ZutatFeld
import de.foody.domain.NutrientBasis
import de.foody.domain.NutritionResult
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class ZutatEditorViewModelTest {
    private lateinit var u: ZutatenTestUmgebung
    private val ereignisse = CopyOnWriteArrayList<EditorEreignis>()
    private val meldungen = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        u = ZutatenTestUmgebung()
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.Default) { u.meldungen.fluss.collect { meldungen += it } }
    }

    @After
    fun tearDown() {
        u.db.close()
        Dispatchers.resetMain()
    }

    /** Editor für [id], fertig geladen; sammelt die Ereignisse. */
    private fun editor(id: String): ZutatEditorViewModel {
        val vm = u.editor()
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.Default) { vm.ereignisse.collect { ereignisse += it } }
        vm.laden(id)
        warte { vm.zustand.value.geladen }
        return vm
    }

    private fun ZutatEditorViewModel.entwurf(): ZutatEntwurf = assertNotNull(zustand.value.entwurf)

    private fun reis() = u.zutat("seed-reis", "Reis") {
        it.copy(nutrientBasis = NutrientBasis.PER_100_G, energyKj = BigDecimal("1502"), densityGPerMl = BigDecimal("0.85"))
    }

    @Test
    fun laedtEntwurf() {
        reis()
        u.rezept("r1", "Reispfanne", "seed-reis")
        val vm = editor("seed-reis")
        assertEquals("359", vm.entwurf().kcal)
        assertFalse(vm.zustand.value.geaendert)
        warte { vm.zustand.value.verwendetIn == listOf("Reispfanne") }
    }

    @Test
    fun aendernMarkiertGeaendert() {
        reis()
        val vm = editor("seed-reis")
        vm.aendern(vm.entwurf().copy(dichte = "1"))
        assertTrue(vm.zustand.value.geaendert)
    }

    @Test
    fun fehlerErstNachFeldFertig() {
        reis()
        val vm = editor("seed-reis")
        vm.aendern(vm.entwurf().copy(kcal = "5000"))
        assertTrue(vm.zustand.value.fehler.isEmpty())
        vm.feldFertig(ZutatFeld.KCAL)
        assertEquals(mapOf(ZutatFeld.KCAL to "0 bis 1.000"), vm.zustand.value.fehler)
    }

    @Test
    fun speichernZeigtAlleFehler() {
        reis()
        val vm = editor("seed-reis")
        vm.aendern(vm.entwurf().copy(kcal = "5000", dichte = "9"))
        vm.speichern()
        assertEquals(setOf(ZutatFeld.KCAL, ZutatFeld.DICHTE), vm.zustand.value.fehler.keys)
        Thread.sleep(200)
        assertTrue(ereignisse.isEmpty())
    }

    @Test
    fun speichernFertig() {
        reis()
        val vm = editor("seed-reis")
        vm.aendern(vm.entwurf().copy(dichte = "1,1"))
        vm.speichern()
        warte { ereignisse.isNotEmpty() }
        assertEquals(listOf<EditorEreignis>(EditorEreignis.Fertig), ereignisse.toList())
        assertEquals(BigDecimal("1.1"), runBlocking { u.db.zutatDao().nachId("seed-reis") }?.densityGPerMl)
    }

    @Test
    fun nameVergebenEreignis() {
        reis()
        u.zutat("z", "Zwiebeln")
        val vm = editor("z")
        vm.aendern(vm.entwurf().copy(name = "reis"))
        vm.speichern()
        warte { ereignisse.isNotEmpty() }
        assertEquals(EditorEreignis.NameVergeben("seed-reis", "Reis"), ereignisse.single())
    }

    @Test
    fun strichcodeVergebenAmFeld() {
        u.zutat("a", "Saft") { it.copy(barcode = "4001234567890") }
        u.zutat("b", "Milch")
        val vm = editor("b")
        vm.aendern(vm.entwurf().copy(strichcode = "4001234567890"))
        vm.speichern()
        warte { vm.zustand.value.fehler.isNotEmpty() }
        assertEquals(mapOf(ZutatFeld.STRICHCODE to "Gehört schon zu „Saft“"), vm.zustand.value.fehler)
    }

    @Test
    fun loeschenUnbenutzt() {
        u.zutat("z", "Würze")
        val vm = editor("z")
        vm.loeschen()
        warte { ereignisse.isNotEmpty() && meldungen.isNotEmpty() }
        assertEquals(listOf<EditorEreignis>(EditorEreignis.Fertig), ereignisse.toList())
        Thread.sleep(200)
        assertEquals(listOf("Gelöscht"), meldungen.toList())
    }

    @Test
    fun loeschenInVerwendung() {
        u.zutat("z", "Würze")
        u.rezept("r1", "A", "z")
        u.rezept("r2", "B", "z")
        val vm = editor("z")
        vm.loeschen()
        warte { ereignisse.isNotEmpty() }
        assertEquals(EditorEreignis.InVerwendung(2), ereignisse.single())
        assertNotNull(runBlocking { u.db.zutatDao().nachId("z") })
    }

    @Test
    fun zusammenfuehrenMitEntwurf() {
        u.zutat("alt", "Zwiebeln")
        u.zutat("neu", "Zwiebel")
        val vm = editor("alt")
        vm.aendern(vm.entwurf().copy(kcal = "40"))
        vm.zusammenfuehren("neu")
        warte { ereignisse.isNotEmpty() && meldungen.isNotEmpty() }
        assertEquals(listOf<EditorEreignis>(EditorEreignis.Fertig), ereignisse.toList())
        assertEquals(listOf("Zusammengeführt in „Zwiebel“"), meldungen.toList())
        val ziel = runBlocking { u.db.zutatDao().nachId("neu") }
        assertEquals(0, ziel?.energyKj?.compareTo(NutritionResult.kcalToKj(BigDecimal(40))))
    }

    @Test
    fun zieleOhneSichSelbst() {
        u.zutat("alt", "Zwiebeln")
        u.zutat("neu", "Zwiebel")
        val vm = editor("alt")
        warte { vm.zustand.value.ziele.isNotEmpty() }
        assertEquals(listOf("neu"), vm.zustand.value.ziele.map { it.id })
    }

    @Test
    fun zutatVerschwindet() {
        u.zutat("z", "Würze")
        editor("z")
        runBlocking { u.db.zutatDao().loeschen("z") }
        warte { ereignisse.isNotEmpty() && meldungen.isNotEmpty() }
        assertEquals(listOf<EditorEreignis>(EditorEreignis.Fertig), ereignisse.toList())
        assertEquals(listOf("Diese Zutat gibt es nicht mehr"), meldungen.toList())
    }

    @Test
    fun doppeltippSpeichertEinmal() {
        reis()
        val vm = editor("seed-reis")
        vm.aendern(vm.entwurf().copy(dichte = "1"))
        vm.speichern()
        vm.speichern()
        warte { ereignisse.isNotEmpty() && !vm.zustand.value.laeuft }
        Thread.sleep(300)
        assertEquals(listOf<EditorEreignis>(EditorEreignis.Fertig), ereignisse.toList())
        assertNull(meldungen.firstOrNull())
    }

    @Test
    fun quelleVerschwindetBeimZusammenfuehren() {
        u.zutat("alt", "Zwiebeln")
        u.zutat("neu", "Zwiebel")
        val vm = editor("alt")
        runBlocking { u.db.zutatDao().loeschen("alt") }
        vm.zusammenfuehren("neu")
        warte { ereignisse.isNotEmpty() && meldungen.isNotEmpty() && !vm.zustand.value.laeuft }
        assertEquals(listOf<EditorEreignis>(EditorEreignis.Fertig), ereignisse.toList())
        assertEquals(listOf("Diese Zutat gibt es nicht mehr"), meldungen.toList())
        assertNotNull(runBlocking { u.db.zutatDao().nachId("neu") })
    }

    @Test
    fun zielWegBleibtOffen() {
        u.zutat("alt", "Zwiebeln")
        val vm = editor("alt")
        vm.zusammenfuehren("fehlt")
        warte { ereignisse.isNotEmpty() && !vm.zustand.value.laeuft }
        assertEquals(listOf<EditorEreignis>(EditorEreignis.Meldung("Die Zutat gibt es nicht mehr")), ereignisse.toList())
    }

    @Test
    fun rezeptzahlZaehltGleichnamigeRezepteEinzeln() {
        u.zutat("z", "Würze")
        u.rezept("r1", "Curry", "z")
        u.rezept("r2", "Curry", "z")
        val vm = editor("z")
        warte { vm.zustand.value.rezepte == 2 }
        assertEquals(listOf("Curry"), vm.zustand.value.verwendetIn)
    }

    @Test
    fun strichcodeVergebenBeimZusammenfuehren() {
        u.zutat("c", "Saft") { it.copy(barcode = "4001234567890") }
        u.zutat("alt", "Zwiebeln")
        u.zutat("neu", "Zwiebel")
        val vm = editor("alt")
        vm.aendern(vm.entwurf().copy(strichcode = "4001234567890"))
        vm.zusammenfuehren("neu")
        warte { vm.zustand.value.fehler.isNotEmpty() && !vm.zustand.value.laeuft }
        assertEquals(mapOf(ZutatFeld.STRICHCODE to "Gehört schon zu „Saft“"), vm.zustand.value.fehler)
        assertTrue(ereignisse.isEmpty())
    }
}

package de.foody.feature.zutaten

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.zutaten.ZutatFilter
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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class ZutatenListeViewModelTest {
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

    /** Sammelt die Ereignisse (die Arbeit läuft auf anderen Threads). */
    private fun ereignisseVon(vm: ZutatenListeViewModel): MutableList<ListeEreignis> {
        val liste = CopyOnWriteArrayList<ListeEreignis>()
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.Default) { vm.ereignisse.collect { liste += it } }
        return liste
    }

    @Test
    fun filterUndSuche() {
        u.zutat("a", "Reis")
        u.zutat("b", "Zwiebel")
        u.rezept("r1", "Reispfanne", "a")
        val vm = u.liste()
        warte { vm.zustand.value.treffer.size == 2 }
        vm.filterUmschalten(ZutatFilter.UNBENUTZT)
        warte { vm.zustand.value.treffer.map { it.name } == listOf("Zwiebel") }
        vm.filterUmschalten(ZutatFilter.UNBENUTZT)
        vm.suchen("rei")
        warte { vm.zustand.value.treffer.map { it.name } == listOf("Reis") }
        assertEquals("rei", vm.zustand.value.suche)
    }

    @Test
    fun anlegenOeffnet() {
        val vm = u.liste()
        val e = ereignisseVon(vm)
        vm.anlegen("Paprika")
        warte { e.isNotEmpty() }
        val oeffnen = assertIs<ListeEreignis.Oeffnen>(e.single())
        assertEquals("Paprika", runBlocking { u.db.zutatDao().nachId(oeffnen.id) }?.canonicalName)
    }

    @Test
    fun anlegenGibtEsSchon() {
        u.zutat("a", "Reis")
        val vm = u.liste()
        val e = ereignisseVon(vm)
        vm.anlegen("reis")
        warte { e.isNotEmpty() }
        assertEquals(ListeEreignis.GibtEsSchon("a", "Reis"), e.single())
    }

    @Test
    fun vorschauUndAnwenden() {
        u.zutat("a", "Zwiebeln") { it.copy(category = "Obst & Gemüse") }
        u.zutat("b", "zwiebeln") { it.copy(category = "Obst & Gemüse") }
        val vm = u.liste()
        val e = ereignisseVon(vm)
        vm.vorschauLaden()
        warte { vm.vorschau.value?.size == 2 }
        vm.anwenden()
        warte { e.isNotEmpty() }
        assertEquals(ListeEreignis.Meldung("2 Zutaten angepasst"), e.single())
        assertNull(vm.vorschau.value)
    }

    @Test
    fun anwendenDoppeltippEinmal() {
        u.zutat("a", "Zwiebeln") { it.copy(category = "Obst & Gemüse") }
        val vm = u.liste()
        val e = ereignisseVon(vm)
        vm.vorschauLaden()
        warte { vm.vorschau.value?.size == 1 }
        vm.anwenden()
        vm.anwenden()
        warte { e.isNotEmpty() && !vm.zustand.value.laeuft }
        Thread.sleep(200)
        assertEquals(listOf<ListeEreignis>(ListeEreignis.Meldung("1 Zutat angepasst")), e.toList())
    }

    @Test
    fun meldungAusEditorKommtAn() {
        val vm = u.liste()
        val e = ereignisseVon(vm)
        u.meldungen.senden("Gelöscht")
        warte { e.isNotEmpty() }
        assertEquals(ListeEreignis.Meldung("Gelöscht"), e.single())
    }
}

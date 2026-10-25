package de.foody.feature.einstellungen

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.Darstellung
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.sicherung.ImportErgebnis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class EinstellungenViewModelTest {
    private lateinit var u: EinstellungenTestUmgebung

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        u = EinstellungenTestUmgebung()
    }

    @After
    fun tearDown() {
        u.db.close()
        Dispatchers.resetMain()
    }

    /** Sammelt die Meldungen des ViewModels (die Arbeit läuft auf anderen Threads). */
    private fun meldungenVon(vm: EinstellungenViewModel): MutableList<String> {
        val liste = CopyOnWriteArrayList<String>()
        @Suppress("OPT_IN_USAGE")
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Default) { vm.meldungen.collect { liste += it } }
        return liste
    }

    private fun warte(bedingung: () -> Boolean) = runBlocking {
        withContext(Dispatchers.Default) { withTimeout(10_000) { while (!bedingung()) delay(10) } }
    }

    private fun datei(name: String) = File(u.kontext.cacheDir, name).also { it.delete() }

    private fun rezept() = runBlocking {
        u.db.rezeptDao().upsert(
            RezeptEntity(
                id = "r1", name = "Reispfanne", defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = null,
                notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
            ),
        )
    }

    @Test
    fun darstellungSetzen() = runTest {
        val vm = u.vm()
        vm.darstellungSetzen(Darstellung.DUNKEL)
        assertEquals(Darstellung.DUNKEL, u.darstellung.darstellung().first())
        warte { vm.zustand.value.darstellung == Darstellung.DUNKEL }
    }

    @Test
    fun tageszielEingabe() = runTest {
        val vm = u.vm()
        assertTrue(vm.tageszielEingabe("2.000"))
        assertEquals(2000, u.ziel.tagesziel().first())
        assertTrue(vm.tageszielEingabe(" 2100 "))
        assertEquals(2100, u.ziel.tagesziel().first())
        assertFalse(vm.tageszielEingabe("499"))
        assertFalse(vm.tageszielEingabe("abc"))
        assertEquals(2100, u.ziel.tagesziel().first())
        assertTrue(vm.tageszielEingabe(""))
        assertNull(u.ziel.tagesziel().first())
    }

    @Test
    fun dateinameAusUhr() {
        assertEquals("foody-sicherung-2026-10-28.zip", u.vm().dateiname())
    }

    @Test
    fun erstellenMeldet() {
        rezept()
        val vm = u.vm()
        val meldungen = meldungenVon(vm)
        val ziel = datei("sicherung.zip")
        vm.sicherungErstellen(Uri.fromFile(ziel))
        warte { meldungen.isNotEmpty() }
        assertEquals("Sicherung gespeichert", meldungen.single())
        val namen = ZipInputStream(ziel.inputStream()).use { z -> generateSequence { z.nextEntry }.map { it.name }.toList() }
        assertTrue("backup.json" in namen)
    }

    @Test
    fun wiederherstellenV1() {
        val vm = u.vm()
        val meldungen = meldungenVon(vm)
        val quelle = datei("v1.json").apply { writeText(V1_SICHERUNG) }
        vm.wiederherstellen(Uri.fromFile(quelle))
        warte { meldungen.isNotEmpty() }
        assertEquals("1 Rezepte, 1 Zutaten, 0 Fotos wiederhergestellt · Vorrat übersprungen: 2", meldungen.single())
        assertNotNull(runBlocking { u.db.rezeptDao().nachId("r1") })
    }

    @Test
    fun loeschenMeldet() {
        rezept()
        val vm = u.vm()
        val meldungen = meldungenVon(vm)
        vm.allesLoeschen()
        warte { meldungen.isNotEmpty() }
        assertEquals("Alle Daten gelöscht", meldungen.single())
        assertNull(runBlocking { u.db.rezeptDao().nachId("r1") })
    }

    @Test
    fun gesperrtBeiSync() {
        rezept()
        u.verbinden()
        // Die Sicherung prüft den Sync selbst: dafür den echten Sync-Zustand einschalten
        runBlocking {
            u.db.syncDao().upsertZustand(de.foody.core.data.db.SyncZustandEntity(active = true, serverUrl = "http://10.0.2.2:8081"))
        }
        val vm = u.vm()
        warte { vm.zustand.value.gesperrt }
        val meldungen = meldungenVon(vm)
        vm.allesLoeschen()
        warte { meldungen.isNotEmpty() }
        assertEquals("Zuerst unter Konto die Verbindung trennen.", meldungen.single())
        assertNotNull(runBlocking { u.db.rezeptDao().nachId("r1") })
    }

    @Test
    fun zweiteAktionIgnoriert() {
        // Hauptthread angehalten: das Löschen ist gestartet (läuft), aber noch nicht fertig, wenn der zweite Tipp kommt
        val haupt = StandardTestDispatcher()
        Dispatchers.resetMain()
        Dispatchers.setMain(haupt)
        rezept()
        val vm = u.vm()
        val meldungen = meldungenVon(vm)
        val zwei = datei("zwei.zip")
        vm.allesLoeschen()
        assertEquals(Aktion.LOESCHEN, vm.laeuft.value)
        vm.sicherungErstellen(Uri.fromFile(zwei))
        val bis = System.currentTimeMillis() + 10_000
        while (meldungen.isEmpty() && System.currentTimeMillis() < bis) {
            haupt.scheduler.advanceUntilIdle()
            Thread.sleep(10)
        }
        repeat(20) { haupt.scheduler.advanceUntilIdle(); Thread.sleep(10) }
        assertEquals(listOf("Alle Daten gelöscht"), meldungen.toList())
        assertFalse(zwei.exists())
        assertNull(vm.laeuft.value)
    }

    @Test
    fun importMeldungen() {
        assertEquals("12 Rezepte, 230 Zutaten, 5 Fotos wiederhergestellt", importMeldung(ImportErgebnis.Fertig(12, 230, 5, 0)))
        assertEquals("Zuerst unter Konto die Verbindung trennen.", importMeldung(ImportErgebnis.SyncAktiv))
        assertEquals("Die Datei ist beschädigt", importMeldung(ImportErgebnis.Fehler("Die Datei ist beschädigt")))
    }
}

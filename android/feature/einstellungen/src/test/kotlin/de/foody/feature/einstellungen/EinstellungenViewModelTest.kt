package de.foody.feature.einstellungen

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.Darstellung
import de.foody.core.designsystem.token.Farbwelt
import de.foody.core.data.Einstellungsschluessel
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
        u.schliessen()
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
    fun zutatenAnzahl() {
        runBlocking {
            for (n in listOf("Reis", "Salz", "Zucker")) {
                u.db.zutatDao().upsert(de.foody.core.data.db.ZutatEntity(id = n, canonicalName = n, createdAt = 1, updatedAt = 1))
            }
        }
        val vm = u.vm()
        warte { vm.zustand.value.zutaten == 3 }
    }

    @Test
    fun onlineSuche() {
        val vm = u.vm()
        assertTrue(vm.zustand.value.onlineSuche)
        vm.onlineSucheSetzen(false)
        assertFalse(u.scan.onlineSuche.value)
        val prefs = u.kontext.getSharedPreferences(Einstellungsschluessel.PREFS, android.content.Context.MODE_PRIVATE)
        assertFalse(prefs.getBoolean(Einstellungsschluessel.ONLINE_SUCHE, true))
        warte { !vm.zustand.value.onlineSuche }
        vm.onlineSucheSetzen(true)
        warte { vm.zustand.value.onlineSuche }
    }

    @Test
    fun onlineSucheAusSchonImErstenZustand() {
        u.scan.setzen(false)
        val stumm = object : KontoZugriff by u.konto {
            override val zustand = kotlinx.coroutines.flow.MutableSharedFlow<VerbindungsZustand>()
        }
        assertFalse(u.vm(stumm).zustand.value.onlineSuche)
    }

    @Test
    fun darstellungSetzen() = runTest {
        val vm = u.vm()
        vm.darstellungSetzen(Darstellung.DUNKEL)
        assertEquals(Darstellung.DUNKEL, u.darstellung.darstellung().first())
        warte { vm.zustand.value.darstellung == Darstellung.DUNKEL }
    }

    @Test
    fun farbweltStandardUndSetzen() = runTest {
        val vm = u.vm()
        assertEquals(Farbwelt.MANGO, vm.zustand.value.farbwelt)
        vm.farbweltSetzen(Farbwelt.LAGUNE)
        assertEquals("LAGUNE", u.darstellung.farbweltAktuell())
        warte { vm.zustand.value.farbwelt == Farbwelt.LAGUNE }
    }

    @Test
    fun tageszielEingabe() = runTest {
        val vm = u.vm()
        assertTrue(vm.tageszielEingabe("2.000"))
        assertTrue(vm.tageszielEingabe(" 2100 "))
        assertFalse(vm.tageszielEingabe("499"))
        assertFalse(vm.tageszielEingabe("abc"))
        assertTrue(vm.tageszielEingabe(""))
        // Prüfen speichert nichts: Zwischenstände beim Tippen („500“ auf dem Weg zu „5000“) landen nicht im Ziel (E3)
        assertNull(u.ziel.tagesziel().first())
    }

    @Test
    fun tageszielSpeichern() = runTest {
        val vm = u.vm()
        assertTrue(vm.tageszielSpeichern("2.000"))
        assertEquals(2000, u.ziel.tagesziel().first())
        assertFalse(vm.tageszielSpeichern("499"))
        assertEquals(2000, u.ziel.tagesziel().first())
        assertTrue(vm.tageszielSpeichern(""))
        assertNull(u.ziel.tagesziel().first())
    }

    @Test
    fun startwerteSofort() {
        // Der Kontostand kommt (noch) nicht: Darstellung und Tagesziel stehen trotzdem schon im ersten Zustand
        u.darstellung.setzen(Darstellung.DUNKEL)
        u.ziel.setzen(2000)
        val stumm = object : KontoZugriff by u.konto {
            override val zustand = kotlinx.coroutines.flow.MutableSharedFlow<VerbindungsZustand>()
        }
        val z = u.vm(stumm).zustand.value
        assertEquals(Darstellung.DUNKEL, z.darstellung)
        assertEquals(2000, z.tagesziel)
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
    fun gescheiterteSicherungHinterlaesstKeineDatei() {
        // Die Datei ist schon angelegt, dann scheitert das Schreiben: keine halbe ZIP, die wie eine Sicherung aussieht
        val vm = u.vm()
        val meldungen = meldungenVon(vm)
        val ziel = datei("kaputt.zip").apply { writeBytes(byteArrayOf()) }
        u.db.close()
        vm.sicherungErstellen(Uri.fromFile(ziel))
        warte { meldungen.isNotEmpty() }
        assertTrue(meldungen.single().startsWith("Sicherung fehlgeschlagen"), meldungen.single())
        assertFalse(ziel.exists())
    }

    @Test
    fun wiederherstellenV1() {
        val vm = u.vm()
        val meldungen = meldungenVon(vm)
        val quelle = datei("v1.json").apply { writeText(V1_SICHERUNG) }
        vm.wiederherstellen(Uri.fromFile(quelle))
        warte { meldungen.isNotEmpty() }
        assertEquals("1 Rezept, 1 Zutat, 0 Fotos wiederhergestellt · Vorrat übersprungen: 2", meldungen.single())
        assertNotNull(runBlocking { u.db.rezeptDao().nachId("r1") })
    }

    @Test
    fun unlesbareDateiKeinAbsturz() {
        // Etwa eine Cloud-Datei, die offline nicht verfügbar ist: Meldung statt Absturz, danach wieder bereit
        val vm = u.vm()
        val meldungen = meldungenVon(vm)
        vm.wiederherstellen(Uri.fromFile(File(u.kontext.cacheDir, "gibt-es-nicht.zip").also { it.delete() }))
        warte { meldungen.isNotEmpty() }
        assertEquals("Die Datei lässt sich nicht öffnen", meldungen.single())
        warte { vm.laeuft.value == null }
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
        assertEquals("1 Rezept, 1 Zutat, 1 Foto wiederhergestellt", importMeldung(ImportErgebnis.Fertig(1, 1, 1, 0)))
        assertEquals("115 Rezepte, 249 Zutaten, 0 Fotos wiederhergestellt", importMeldung(ImportErgebnis.Fertig(115, 249, 0, 0)))
        assertEquals("Zuerst unter Konto die Verbindung trennen.", importMeldung(ImportErgebnis.SyncAktiv))
        assertEquals("Die Datei ist beschädigt", importMeldung(ImportErgebnis.Fehler("Die Datei ist beschädigt")))
    }
}

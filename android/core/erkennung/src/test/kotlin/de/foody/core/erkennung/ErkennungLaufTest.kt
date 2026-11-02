package de.foody.core.erkennung

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.testing.nachProzessende
import de.foody.domain.FotoVorschlag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ablauf hinter dem Foto-Knopf: Vorschlag, Meldungen, Abbrechen und Prozessende (Phase 3 ohne Oberfläche). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class ErkennungLaufTest {
    @get:Rule val tmp = TemporaryFolder()

    private val vorschlag = FotoVorschlag("Leberkässemmel", 480, null, null, null)
    private val vorschlaege = mutableListOf<FotoVorschlag>()
    private val scopes = mutableListOf<CoroutineScope>()

    /** Eigener Scope je Lauf, damit eine hängende Erkennung `runTest` nicht aufhält. */
    private fun TestScope.lauf(h: SavedStateHandle, erkenner: MahlzeitErkenner) =
        ErkennungLauf(erkenner, CoroutineScope(StandardTestDispatcher(testScheduler)).also { scopes += it }, h) { vorschlaege += it }

    @After fun aufraeumen() = scopes.forEach { it.cancel() }

    @Test fun vorschlagGehtWeiterUndDasKamerafotoWirdGeloescht() = runTest {
        val foto = tmp.newFile("kamera.jpg")
        val l = lauf(SavedStateHandle()) { ErkennungStatus.Erkannt(vorschlag, 20_000) }
        l.erkennen("file://${foto.path}", loeschen = foto.path)
        assertTrue(l.zustand.value.laeuft)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(vorschlag), vorschlaege)
        assertFalse(l.zustand.value.laeuft)
        assertNull(l.zustand.value.meldung)
        assertFalse(foto.exists())
    }

    @Test fun ohneVorschlagEineMeldung() = runTest {
        for (status in listOf(ErkennungStatus.KeinModell, ErkennungStatus.ZuWenigSpeicher, ErkennungStatus.Zeitlimit, ErkennungStatus.Unlesbar, ErkennungStatus.Fehler)) {
            val l = lauf(SavedStateHandle()) { status }
            l.erkennen("content://foto")
            testScheduler.advanceUntilIdle()
            assertEquals(ErkennungTexte.meldung(status), l.zustand.value.meldung)
            assertFalse(l.zustand.value.laeuft)
        }
        assertTrue(vorschlaege.isEmpty())
    }

    @Test fun neueErkennungLoeschtDieAlteMeldung() = runTest {
        var status: ErkennungStatus = ErkennungStatus.Unlesbar
        val l = lauf(SavedStateHandle()) { status }
        l.erkennen("content://foto")
        testScheduler.advanceUntilIdle()
        status = ErkennungStatus.Erkannt(vorschlag, 1)
        l.erkennen("content://foto2")
        assertNull(l.zustand.value.meldung)
    }

    @Test fun abbrechenOhneMeldungUndOhneVorschlag() = runTest {
        val foto = tmp.newFile("kamera.jpg")
        val l = lauf(SavedStateHandle()) { awaitCancellation() }
        l.erkennen("file://${foto.path}", loeschen = foto.path)
        testScheduler.advanceUntilIdle()
        l.abbrechen()
        testScheduler.advanceUntilIdle()
        assertFalse(l.zustand.value.laeuft)
        assertNull(l.zustand.value.meldung)
        assertTrue(vorschlaege.isEmpty())
        assertFalse(foto.exists())
    }

    @Test fun waehrendEinerErkennungKeineZweite() = runTest {
        var aufrufe = 0
        val l = lauf(SavedStateHandle()) {
            aufrufe++
            awaitCancellation()
        }
        l.erkennen("content://a")
        testScheduler.advanceUntilIdle()
        l.erkennen("content://b")
        testScheduler.advanceUntilIdle()
        assertEquals(1, aufrufe)
    }

    @Test fun prozessendeMittenInDerErkennungStartetSieNeu() = runTest {
        val h = SavedStateHandle()
        lauf(h) { awaitCancellation() }.erkennen("content://foto", loeschen = "/cache/erkennung/kamera.jpg")
        testScheduler.advanceUntilIdle()

        val uris = mutableListOf<String>()
        val nach = lauf(h.nachProzessende()) {
            uris += it
            ErkennungStatus.Erkannt(vorschlag, 1)
        }
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("content://foto"), uris)
        assertEquals(listOf(vorschlag), vorschlaege)
        assertFalse(nach.zustand.value.laeuft)
    }

    @Test fun kamerafotoUndMeldungUeberstehenProzessende() = runTest {
        val h = SavedStateHandle()
        val vor = lauf(h) { ErkennungStatus.Unlesbar }
        vor.erkennen("content://foto")
        testScheduler.advanceUntilIdle()
        vor.kameraGestartet("/cache/erkennung/kamera.jpg")

        val nach = lauf(h.nachProzessende()) { error("nicht neu gestartet") }
        testScheduler.advanceUntilIdle()
        assertEquals("/cache/erkennung/kamera.jpg", nach.zustand.value.fotoPfad)
        assertEquals(ErkennungTexte.UNLESBAR, nach.zustand.value.meldung)
        assertEquals("/cache/erkennung/kamera.jpg", nach.kameraZurueck())
        assertNull(nach.zustand.value.fotoPfad)
    }
}

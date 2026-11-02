package de.foody.core.erkennung

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.domain.FotoPortion
import de.foody.domain.FotoVorschlag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Gedächtnis eigener Korrekturen und Protokoll für die Abnahme (Phase 6, Regel 29). Robolectric wegen `org.json`. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class FotoGedaechtnisTest {
    @get:Rule val tmp = TemporaryFolder()

    private var zeit = 1L
    private val ordner by lazy { tmp.newFolder("fotoerkennung") }

    private fun gedaechtnis() = FotoGedaechtnis(ordner, Dispatchers.Unconfined) { zeit++ }

    private val modell = FotoVorschlag("Leberkässemmel", 480, BigDecimal("18"), null, null)
    private val korrigiert = FotoVorschlag("Leberkässemmel mit Senf", 520, BigDecimal("19.5"), BigDecimal("41"), BigDecimal("28"))

    @Test fun gemerktUnterDemModellnamenUndNachNeustartDa() = runTest {
        gedaechtnis().merken(modell, ausGedaechtnis = false, FotoPortion.NORMAL, korrigiert)
        assertEquals(korrigiert, gedaechtnis().nachschlagen("  LEBERKÄSSEMMEL "), "neu gelesen, Name bereinigt verglichen")
        assertNull(gedaechtnis().nachschlagen("Leberkässemmel mit Senf"), "Schlüssel ist der Modellname, nicht der gespeicherte")
        assertNull(gedaechtnis().nachschlagen(""))
    }

    @Test fun spaeteresSpeichernErsetzt() = runTest {
        val g = gedaechtnis()
        g.merken(modell, false, FotoPortion.NORMAL, korrigiert)
        val wieder = korrigiert.copy(kcal = 600)
        g.merken(modell, true, FotoPortion.GROSS, wieder)
        assertEquals(wieder, g.nachschlagen("Leberkässemmel"))
    }

    @Test fun hoechstensDreihundertDerAeltesteFaelltWeg() = runTest {
        val g = gedaechtnis()
        repeat(FotoGedaechtnis.MAX_EINTRAEGE + 1) { g.merken(modell.copy(name = "Gericht $it"), false, FotoPortion.NORMAL, korrigiert) }
        assertNull(g.nachschlagen("Gericht 0"))
        assertEquals(korrigiert, g.nachschlagen("Gericht 1"))
        assertEquals(korrigiert, gedaechtnis().nachschlagen("Gericht ${FotoGedaechtnis.MAX_EINTRAEGE}"))
    }

    @Test fun kaputteDateiZaehltAlsLeer() = runTest {
        File(ordner, "gedaechtnis.json").writeText("{kaputt")
        val g = gedaechtnis()
        assertNull(g.nachschlagen("Leberkässemmel"))
        g.merken(modell, false, FotoPortion.NORMAL, korrigiert)
        assertEquals(korrigiert, gedaechtnis().nachschlagen("Leberkässemmel"), "danach wieder lesbar")
    }

    @Test fun protokollZeigtVorschlagUndGespeichertes() = runTest {
        gedaechtnis().merken(modell, ausGedaechtnis = true, FotoPortion.KLEIN, korrigiert)
        val zeile = JSONObject(File(ordner, "protokoll.jsonl").readLines().single())
        assertEquals(480, zeile.getJSONObject("vorschlag").getInt("kcal"))
        assertEquals(520, zeile.getJSONObject("gespeichert").getInt("kcal"))
        assertEquals("19.5", zeile.getJSONObject("gespeichert").getString("eiweiss"))
        assertEquals("KLEIN", zeile.getString("portion"))
        assertEquals(true, zeile.getBoolean("ausGedaechtnis"))
    }

    @Test fun ohneModellnamenNurProtokoll() = runTest {
        gedaechtnis().merken(modell.copy(name = " "), false, FotoPortion.NORMAL, korrigiert)
        assertEquals(false, File(ordner, "gedaechtnis.json").exists())
        assertEquals(1, File(ordner, "protokoll.jsonl").readLines().size)
    }
}

package de.foody.core.designsystem.stil.grauen

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrauenBuchTest {
    @Test
    fun aktuellesBlattVoll() {
        assertEquals(1f, blattDeckkraft(0f))
    }

    @Test
    fun gehendesBlattVerschwindetKurzVorDerKante() {
        assertEquals(1f, blattDeckkraft(0.5f))
        assertEquals(0f, blattDeckkraft(0.95f))
        assertEquals(0f, blattDeckkraft(1f))
    }

    @Test
    fun kommendesBlattTauchtAusDemSchattenAuf() {
        val fern = blattDeckkraft(-0.8f)
        val nah = blattDeckkraft(-0.2f)
        assertTrue(fern in 0f..nah, "nah $nah, fern $fern")
        assertTrue(nah < 1f)
    }

    /** Auf dem Handy schien die vorbereitete nächste Seite halb unter einer kürzeren Seite durch. */
    @Test
    fun nachbarSeiteInRuheUnsichtbar() {
        assertEquals(0f, blattDeckkraft(-1f))
        assertEquals(0f, blattDeckkraft(-2f))
        assertEquals(0f, blattDeckkraft(2f))
    }

    /**
     * Auf dem Handy ragte das „S“ unten aus dem Rahmen: Die Zeichenbox ist nur so hoch wie die (feste, kleinere)
     * Zeile. Das Feld umschließt jetzt den Buchstaben von der Versalhöhe bis unter die Grundlinie.
     */
    @Test
    fun initialenFeldUmschliesstDenBuchstaben() {
        val groesse = 84f
        val grundlinie = 60f
        // Zeichenbox wie auf dem Handy: so hoch wie die Zeile (62 px), samt Abstand dahinter
        val zeichen = Rect(0f, 8f, 50f + 0.35f * groesse, 70f)
        val feld = initialenFeld(zeichen, grundlinie, groesse)
        assertTrue(feld.top < grundlinie - 0.72f * groesse, "oben ${feld.top}")
        assertTrue(feld.bottom > grundlinie + 0.05f * groesse, "unten ${feld.bottom}")
        assertTrue(feld.left < 0f && feld.right > 50f, "seitlich ${feld.left}..${feld.right}")
        assertEquals(feld.width, feld.height)
    }
}

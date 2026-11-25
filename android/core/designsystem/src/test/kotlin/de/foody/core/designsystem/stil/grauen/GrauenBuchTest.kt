package de.foody.core.designsystem.stil.grauen

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
}

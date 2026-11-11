package de.foody.core.designsystem.stil

import de.foody.core.designsystem.token.Flaechenart
import de.foody.core.designsystem.token.FoodyFormen
import de.foody.core.designsystem.token.FoodyTypografie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StilTest {
    @Test
    fun ausNameMitStandard() {
        assertEquals(FoodyStil.SAGE, FoodyStil.ausName("SAGE"))
        assertEquals(FoodyStil.MILCHGLAS, FoodyStil.ausName(null))
        assertEquals(FoodyStil.MILCHGLAS, FoodyStil.ausName("ELFENBEIN"))
        assertEquals(StilVariante.RUHIG, StilVariante.ausName("RUHIG"))
        assertEquals(StilVariante.IMMERSIV, StilVariante.ausName(null))
    }

    @Test
    fun akzentUnbekanntIstDerErste() {
        assertEquals("SILBER", FoodyStil.SAGE.akzent("SILBER")?.name)
        assertEquals("GOLD", FoodyStil.SAGE.akzent(null)?.name)
        // Akzent eines anderen Stils passt nicht: Standard des Stils
        assertEquals("AMBER", FoodyStil.KREUZER.akzent("GOLD")?.name)
        assertNull(FoodyStil.MILCHGLAS.akzent("GOLD"))
    }

    @Test
    fun jederStilHatVierAkzente() {
        FoodyStil.entries.filter { it != FoodyStil.MILCHGLAS }.forEach { assertEquals(4, it.akzente.size, it.name) }
    }

    @Test
    fun festerGrundJeStil() {
        for (v in StilVariante.entries) {
            for (modus in listOf(false, true)) {
                assertTrue(stilWerte(FoodyStil.SAGE, v, dunkel = modus).dunkel)
                assertTrue(stilWerte(FoodyStil.KREUZER, v, dunkel = modus).dunkel)
                assertFalse(stilWerte(FoodyStil.ANIME, v, dunkel = modus).dunkel)
                assertFalse(stilWerte(FoodyStil.EDO, v, dunkel = modus).dunkel)
                assertFalse(stilWerte(FoodyStil.BAYRISCH, v, dunkel = modus).dunkel)
                assertEquals(modus, stilWerte(FoodyStil.MILCHGLAS, v, dunkel = modus).dunkel)
                assertEquals(FoodyStil.GRAUEN.dunkel(v, modus), stilWerte(FoodyStil.GRAUEN, v, dunkel = modus).dunkel)
            }
        }
        assertTrue(FoodyStil.GRAUEN.dunkel(StilVariante.IMMERSIV, false))
        assertFalse(FoodyStil.GRAUEN.dunkel(StilVariante.RUHIG, true))
    }

    @Test
    fun milchglasWieBisher() {
        val s = stilWerte(FoodyStil.MILCHGLAS)
        assertEquals(FoodyFormen.Rund, s.formen)
        assertEquals(FoodyTypografie, s.typografie)
        assertEquals(Flaechenart.GLAS, s.flaeche)
        assertEquals(Hintergrundmuster.KEINS, s.muster)
    }

    @Test
    fun stileDeckendUndRuhigOhneMuster() {
        FoodyStil.entries.filter { it != FoodyStil.MILCHGLAS }.forEach { stil ->
            assertEquals(Flaechenart.DECKEND, stilWerte(stil).flaeche)
            assertEquals(Hintergrundmuster.KEINS, stilWerte(stil, StilVariante.RUHIG).muster)
            assertNull(stilWerte(stil, StilVariante.RUHIG).schatten)
        }
    }

    @Test
    fun angleichenLaesstPassendeFarbeStehen() {
        // Gold hält auf dem dunklen Grund der Sage AA schon so, wie der Entwurf es vorsieht
        val gold = FoodyStil.SAGE.akzente.first().farbe
        assertEquals(gold, stilWerte(FoodyStil.SAGE).farben.primaer)
    }
}

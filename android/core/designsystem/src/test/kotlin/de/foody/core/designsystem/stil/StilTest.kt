package de.foody.core.designsystem.stil

import androidx.compose.ui.graphics.Color
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
        // Das Grauen hat keine Wahl mehr (ADR 0022, eigener Test)
        FoodyStil.entries.filter { it != FoodyStil.MILCHGLAS && it != FoodyStil.GRAUEN }.forEach { assertEquals(4, it.akzente.size, it.name) }
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
        // Ominös ist das grüne Grauen, dunkel wie Immersiv (ADR 0022)
        assertTrue(FoodyStil.GRAUEN.dunkel(StilVariante.RUHIG, false))
    }

    /** ADR 0022: Beim Grauen sind beide Varianten inszeniert; Ruhig ist „Ominös“, Immersiv das „Necronomicon“. */
    @Test
    fun grauenHatZweiImmersiveGesichter() {
        val ominoes = stilWerte(FoodyStil.GRAUEN, StilVariante.RUHIG)
        val buch = stilWerte(FoodyStil.GRAUEN, StilVariante.IMMERSIV)
        assertTrue(ominoes.inszeniert)
        assertTrue(ominoes.immersiv(FoodyStil.GRAUEN))
        assertEquals(Hintergrundmuster.STERNE, ominoes.muster)
        assertFalse(ominoes.grauenBuch)
        assertTrue(buch.inszeniert)
        assertTrue(buch.grauenBuch)
        assertEquals("Ominös", FoodyStil.GRAUEN.variantenName(StilVariante.RUHIG))
        assertEquals("Necronomicon", FoodyStil.GRAUEN.variantenName(StilVariante.IMMERSIV))
        // Die anderen Stile behalten Immersiv und Ruhig
        for (stil in FoodyStil.entries.filter { it != FoodyStil.GRAUEN && it != FoodyStil.MILCHGLAS }) {
            assertFalse(stilWerte(stil, StilVariante.RUHIG).inszeniert, stil.name)
            assertFalse(stilWerte(stil, StilVariante.IMMERSIV).grauenBuch, stil.name)
            assertEquals("Ruhig", stil.variantenName(StilVariante.RUHIG))
            assertEquals("Immersiv", stil.variantenName(StilVariante.IMMERSIV))
        }
    }

    /** ADR 0022: Beim Grauen gibt es keine Farbe zur Wahl; ein früher gespeicherter Akzent ergibt Glut. */
    @Test
    fun grauenOhneFarbwahl() {
        assertEquals(listOf("GLUT"), FoodyStil.GRAUEN.akzente.map { it.name })
        assertEquals("GLUT", FoodyStil.GRAUEN.akzent("ROSE")?.name)
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
            // Ruhig ohne Muster, außer beim Grauen: Dort ist Ruhig das inszenierte „Ominös“ (ADR 0022)
            if (stil != FoodyStil.GRAUEN) assertEquals(Hintergrundmuster.KEINS, stilWerte(stil, StilVariante.RUHIG).muster)
            assertNull(stilWerte(stil, StilVariante.RUHIG).schatten)
        }
    }

    @Test
    fun flaechenNurBeiAnimeDeckendWeiss() {
        for (stil in FoodyStil.entries) {
            for (variante in StilVariante.entries) {
                for (dunkel in listOf(false, true)) {
                    val erwartet = if (stil == FoodyStil.ANIME) Color.White else null
                    assertEquals(erwartet, stilWerte(stil, variante, dunkel = dunkel).deckend, "$stil $variante dunkel=$dunkel")
                }
            }
        }
    }

    @Test
    fun angleichenLaesstPassendeFarbeStehen() {
        // Gold hält auf dem dunklen Grund der Sage AA schon so, wie der Entwurf es vorsieht
        val gold = FoodyStil.SAGE.akzente.first().farbe
        assertEquals(gold, stilWerte(FoodyStil.SAGE).farben.primaer)
    }
}

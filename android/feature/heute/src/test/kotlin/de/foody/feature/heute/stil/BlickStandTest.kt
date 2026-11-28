package de.foody.feature.heute.stil

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Der Verlauf des Blicks ins Auge (Grauen, Übergang „Heute“ → Tagebuch). */
class BlickStandTest {
    @Test
    fun anfangWieDasRuhendeAuge() {
        assertEquals(Blick(lid = 1f, pupille = 18f, zoom = 1f, dunkel = 0f), blickStand(0f))
    }

    @Test
    fun endeAufgerissenNahUndDunkel() {
        assertEquals(Blick(lid = 1.3f, pupille = 48f, zoom = 16f, dunkel = 1f), blickStand(1f))
    }

    @Test
    fun erstAufreissenDannStuerzenDannDunkel() {
        val schritte = (0..100).map { blickStand(it / 100f) }
        schritte.zipWithNext().forEach { (a, b) ->
            assertTrue(b.lid >= a.lid && b.pupille >= a.pupille && b.zoom >= a.zoom && b.dunkel >= a.dunkel, "$a → $b")
        }
        // Offen, bevor es heranstürzt; dunkel erst, wenn es nah ist
        assertEquals(1.3f, blickStand(0.35f).lid, 0.001f)
        assertTrue(blickStand(0.3f).zoom < 1.01f)
        assertEquals(0f, blickStand(0.65f).dunkel)
        assertTrue(blickStand(0.65f).zoom > 2f)
    }
}

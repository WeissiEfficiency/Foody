package de.foody.feature.rezepte.liste.stil

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Der Verlauf des Bands aus dem Regal (Grauen, Übergang Bibliothek → Rezept). */
class RegalStandTest {
    @Test
    fun anfangImRegal() {
        assertEquals(RegalStand(zug = 0f, heran = 0f, deckel = 0f), regalStand(0f))
    }

    @Test
    fun endeHerangeholtUndAufgeschlagen() {
        assertEquals(RegalStand(zug = 1f, heran = 1f, deckel = 1f), regalStand(1f))
    }

    @Test
    fun erstZiehenDannHeranholenDannAufschlagen() {
        val schritte = (0..100).map { regalStand(it / 100f) }
        schritte.zipWithNext().forEach { (a, b) ->
            assertTrue(b.zug >= a.zug && b.heran >= a.heran && b.deckel >= a.deckel, "$a → $b")
        }
        // Gezogen, bevor er sich heranholt; der Deckel geht erst auf, wenn er fast den Schirm füllt
        assertEquals(0f, regalStand(0.2f).heran)
        assertTrue(regalStand(0.2f).zug > 0.8f)
        assertEquals(0f, regalStand(0.68f).deckel)
        assertTrue(regalStand(0.68f).heran > 0.95f)
    }
}

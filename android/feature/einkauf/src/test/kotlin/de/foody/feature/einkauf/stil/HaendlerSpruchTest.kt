package de.foody.feature.einkauf.stil

import kotlin.test.Test
import kotlin.test.assertEquals

class HaendlerSpruchTest {
    @Test
    fun zahlenAlsWortBisZwoelf() {
        assertEquals("Ihr habt alles. Geht in Frieden.", haendlerSpruch(0))
        assertEquals("Ein Ding fehlt Euch noch.", haendlerSpruch(1))
        assertEquals("Neun Dinge fehlen Euch noch.", haendlerSpruch(9))
        assertEquals("Zwölf Dinge fehlen Euch noch.", haendlerSpruch(12))
        assertEquals("30 Dinge fehlen Euch noch.", haendlerSpruch(30))
    }
}

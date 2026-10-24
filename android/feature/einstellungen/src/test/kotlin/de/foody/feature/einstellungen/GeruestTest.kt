package de.foody.feature.einstellungen

import kotlin.test.Test
import kotlin.test.assertEquals

class GeruestTest {
    @Test
    fun modulBaut() {
        assertEquals("de.foody.feature.einstellungen", Einstellungen.PAKET)
    }
}

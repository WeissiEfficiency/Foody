package de.foody.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class GeruestTest {
    @Test
    fun modulBaut() {
        assertEquals("de.foody.core.designsystem", Designsystem.PAKET)
    }
}

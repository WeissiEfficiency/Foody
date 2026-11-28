package de.foody.core.designsystem.komponenten

import kotlin.test.Test
import kotlin.test.assertEquals

class RoemischVollTest {
    @Test
    fun seitenzahlenDesJahres() {
        assertEquals("I", roemischVoll(1))
        assertEquals("XL", roemischVoll(40))
        assertEquals("XCIX", roemischVoll(99))
        assertEquals("CCXCVII", roemischVoll(297))
        assertEquals("CCCXXIX", roemischVoll(329))
        assertEquals("CCCLXVI", roemischVoll(366))
    }

    @Test
    fun ausserhalbArabisch() {
        assertEquals("0", roemischVoll(0))
        assertEquals("4000", roemischVoll(4000))
    }
}

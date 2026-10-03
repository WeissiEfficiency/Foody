package de.foody.app.data.repo

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Zahlen aus Sicherungen: normale Werte durch, extreme Exponenten und Längen abgelehnt. */
class DecimalLimitsTest {
    @Test fun ordinaryAmountsPass() {
        assertEquals(BigDecimal("1.5"), decimal("1.5"))
        assertEquals(BigDecimal("2500"), decimal("2500"))
        assertEquals(BigDecimal("0.0333333333"), decimal("0.0333333333"))
    }

    @Test fun hugeExponentsAndLengthsAreRejected() {
        assertFailsWith<IllegalArgumentException> { decimal("1E999999999") }
        assertFailsWith<IllegalArgumentException> { decimal("1E-999999999") }
        assertFailsWith<IllegalArgumentException> { decimal("1".repeat(41)) }
        assertFailsWith<NumberFormatException> { decimal("viel") }
    }
}

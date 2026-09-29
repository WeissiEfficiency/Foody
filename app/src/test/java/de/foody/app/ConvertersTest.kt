package de.foody.app

import de.foody.app.data.db.Converters
import de.foody.app.ui.common.parseDecimal
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConvertersTest {
    private val c = Converters()

    @Test fun bigDecimalRoundTripIsLossless() {
        val v = BigDecimal("0.333333")
        assertEquals(v, c.toBigDecimal(c.fromBigDecimal(v)))
    }

    @Test fun dateRoundTrip() {
        val d = LocalDate.of(2026, 10, 25) // Winterzeit-Umstellung
        assertEquals(d, c.toDate(c.fromDate(d)))
    }

    @Test fun parsesGermanDecimals() {
        assertEquals(BigDecimal("1.5"), parseDecimal("1,5"))
        assertEquals(BigDecimal("1.5"), parseDecimal(" 1.5 "))
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal(""))
    }
}

package de.foody.app

import de.foody.app.ui.common.compactRange
import de.foody.app.ui.common.parseNichtNegativ
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormatTest {
    @Test fun sameMonthRangeIsShort() {
        assertEquals("3.–9. Okt.", compactRange(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 9)))
        assertEquals("3. Okt.", compactRange(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3)))
    }

    @Test fun crossMonthRangeNamesBothMonths() {
        val text = compactRange(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4))
        assertTrue(text.startsWith("28. Sep"), text)
        assertTrue(text.endsWith(" – 4. Okt."), text)
    }

    /** Nährwerte und Mengen fürs Tagebuch: nur, was der Sync-Server auch annimmt. */
    @Test fun nichtNegativeZahlen() {
        assertEquals(0, BigDecimal("1.5").compareTo(parseNichtNegativ("1,5")))
        assertEquals(0, BigDecimal.ZERO.compareTo(parseNichtNegativ("0")))
        assertNull(parseNichtNegativ("-5"))
        assertNull(parseNichtNegativ("1e50"))
        assertNull(parseNichtNegativ("0,000000000000000000001"), "mehr als 20 Nachkommastellen")
        assertNull(parseNichtNegativ(""))
    }
}

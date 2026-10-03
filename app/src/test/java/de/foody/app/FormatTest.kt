package de.foody.app

import de.foody.app.ui.common.compactRange
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
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
}

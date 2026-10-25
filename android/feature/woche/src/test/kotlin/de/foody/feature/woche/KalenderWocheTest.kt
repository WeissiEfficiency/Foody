package de.foody.feature.woche

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class KalenderWocheTest {
    @Test
    fun titelKw44() {
        assertEquals("KW 44 · 26.10.–1.11.", wochenTitel(LocalDate.of(2026, 10, 26)))
        assertEquals(LocalDate.of(2026, 10, 26), montagVon(LocalDate.of(2026, 11, 1)))
        assertEquals(LocalDate.of(2026, 10, 26), montagVon(LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun jahreswechsel() {
        val montag = LocalDate.of(2026, 12, 28)
        assertEquals("KW 53 · 28.12.–3.1.", wochenTitel(montag))
        assertEquals(montag, montagVon(LocalDate.of(2027, 1, 1)))
        assertEquals("KW 1 · 4.1.–10.1.", wochenTitel(montag.plusWeeks(1)))
        assertEquals("KW 52 · 21.12.–27.12.", wochenTitel(montag.minusWeeks(1)))
    }
}

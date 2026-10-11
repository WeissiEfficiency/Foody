package de.foody.domain

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StepTimerParserTest {
    private fun minutes(text: String) = StepTimerParser.find(text).map { it.duration.toMinutes() }

    @Test fun findsMinutesAndHours() {
        assertEquals(listOf(20L), minutes("Alles 20 Minuten köcheln lassen."))
        assertEquals(listOf(30L), minutes("ca. 30 Min. im Ofen backen"))
        assertEquals(listOf(60L), minutes("Dann 1 Stunde schmoren."))
        assertEquals(listOf(90L), minutes("1,5 Std. gehen lassen"))
        assertEquals(listOf(5L, 45L), minutes("5 Minuten anbraten, dann 45 Minuten garen."))
    }

    @Test fun rangesUseTheLowerBoundSoNothingBurns() {
        assertEquals(listOf(10L), minutes("10 - 15 Minuten backen"))
        assertEquals(listOf(4L), minutes("Jede Waffel 4–5 Minuten backen"))
    }

    @Test fun combinedHoursAndMinutesAreOneTimer() {
        assertEquals(listOf(90L), minutes("1 Stunde 30 Minuten im Ofen"))
    }

    @Test fun keepsTheMatchedTextAsLabel() {
        assertEquals("20 Minuten", StepTimerParser.find("Alles 20 Minuten köcheln").single().label)
    }

    @Test fun ignoresTextWithoutDurationsAndSeconds() {
        assertTrue(StepTimerParser.find("Den Teig 2 cm dick ausrollen, 3 Eier unterrühren.").isEmpty())
        assertTrue(StepTimerParser.find("5 Minzblätter und 2 Stdn.-Reste").isEmpty())
        assertTrue(StepTimerParser.find("30 Sekunden mixen").map { it.duration } == listOf(Duration.ofSeconds(30)))
    }
}

package de.foody.feature.kochen.timer

import kotlin.test.Test
import kotlin.test.assertEquals

class TimerZeitplanTest {
    private fun t(id: String, ende: Long, gemeldet: Boolean = false) = LaufenderTimer(id, "r1", 2, "Reistopf", id, ende, gemeldet)

    @Test
    fun naechstesEnde() {
        assertEquals(300L, zeitplan(listOf(t("a", 500), t("b", 300), t("c", 50)), 100).naechstesEnde)
    }

    @Test
    fun nurUngemeldeteAbgelaufen() {
        val plan = zeitplan(listOf(t("a", 50), t("b", 80, gemeldet = true), t("c", 100), t("d", 200)), 100)
        assertEquals(listOf("a", "c"), plan.neuAbgelaufen.map { it.id })
        assertEquals(200L, plan.naechstesEnde)
    }

    @Test
    fun leer() {
        assertEquals(Zeitplan(emptyList(), null), zeitplan(emptyList(), 100))
    }
}

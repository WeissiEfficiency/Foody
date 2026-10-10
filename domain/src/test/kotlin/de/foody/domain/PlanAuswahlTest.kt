package de.foody.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlanAuswahlTest {
    private val curry = "curry" to RezeptEinordnung.einordnen("Gemüsecurry", emptyList(), null, null)
    private val tiramisu = "tiramisu" to RezeptEinordnung.einordnen("Tiramisu", emptyList(), null, null)
    private val spaetzle = "spaetzle" to RezeptEinordnung.einordnen("Käsespätzle", emptyList(), null, null)
    private val alle = listOf(curry, tiramisu, spaetzle)

    private fun f(m: Mahlzeit, g: Set<Gang> = emptySet(), a: Boolean = false) =
        PlanAuswahl.filtern(alle, { it.second }, m, g, a).map { it.first }

    @Test fun strengNachMahlzeit() = assertEquals(listOf("curry", "spaetzle"), f(Mahlzeit.ABENDESSEN))

    @Test fun gangFilterSchliesstUneingeordneteAus() = assertEquals(listOf("curry"), f(Mahlzeit.ABENDESSEN, setOf(Gang.HAUPTSPEISE)))

    @Test fun alleHebtFilterAuf() =
        assertEquals(listOf("curry", "tiramisu", "spaetzle"), f(Mahlzeit.FRUEHSTUECK, setOf(Gang.NACHSPEISE), a = true))

    @Test fun unsichtbareAuswahlWirdAufgehoben() {
        assertNull(PlanAuswahl.auswahlBehalten("tiramisu", listOf(curry)) { it.first })
        assertEquals("curry", PlanAuswahl.auswahlBehalten("curry", listOf(curry)) { it.first })
    }
}

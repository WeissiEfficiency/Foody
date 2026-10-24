package de.foody.feature.heute

import de.foody.core.data.heute.GeplanteMahlzeit
import de.foody.domain.Mahlzeit
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeuteLogikTest {
    private val heute = LocalDate.of(2026, 10, 24)

    private fun geplant(id: String, slot: String, datum: LocalDate = heute, gekocht: Boolean = false) = GeplanteMahlzeit(
        planEintragId = id, datum = datum, slot = slot, rezeptId = "r-$id", titel = id, portionen = 2,
        kcalJePortion = null, zeitMinuten = null, bild = null, gekocht = gekocht,
    )

    private fun um(stunde: Int, minute: Int = 0, tag: LocalDate = heute) = LocalDateTime.of(tag, LocalTime.of(stunde, minute))

    private val tag = listOf(
        geplant("abend", "ABENDESSEN"),
        geplant("frueh", "FRUEHSTUECK"),
        geplant("mittag", "MITTAGESSEN"),
    )

    @Test
    fun vorZehnFruehstueck() {
        assertEquals("frueh", naechsteMahlzeit(tag, um(9, 59))?.mahlzeit?.planEintragId)
    }

    @Test
    fun genauZehnIstFruehstueckVorbei() {
        assertEquals("mittag", naechsteMahlzeit(tag, um(10))?.mahlzeit?.planEintragId)
    }

    @Test
    fun gekochtWirdUebersprungen() {
        val plan = listOf(geplant("frueh", "FRUEHSTUECK", gekocht = true), geplant("abend", "ABENDESSEN"))
        assertEquals("abend", naechsteMahlzeit(plan, um(8))?.mahlzeit?.planEintragId)
    }

    @Test
    fun nachAbendessenMorgen() {
        val plan = tag + listOf(geplant("morgen-abend", "ABENDESSEN", heute.plusDays(1)), geplant("morgen-frueh", "FRUEHSTUECK", heute.plusDays(1)))
        val n = naechsteMahlzeit(plan, um(23, 59))
        assertEquals("morgen-frueh", n?.mahlzeit?.planEintragId)
        assertTrue(n!!.morgen)
    }

    @Test
    fun mitternachtZeigtHeute() {
        // Um 00:00 ist der neue Tag „heute“: dessen Frühstück, nicht mehr „morgen“
        val plan = listOf(geplant("neu-frueh", "FRUEHSTUECK", heute.plusDays(1)))
        val n = naechsteMahlzeit(plan, um(0, 0, heute.plusDays(1)))
        assertEquals("neu-frueh", n?.mahlzeit?.planEintragId)
        assertFalse(n!!.morgen)
    }

    @Test
    fun gestrigerEintragZaehltNicht() {
        assertNull(naechsteMahlzeit(listOf(geplant("gestern", "ABENDESSEN", heute.minusDays(1))), um(12)))
    }

    @Test
    fun unbekannterSlotBisMitternacht() {
        assertEquals(LocalTime.of(23, 59), fensterEnde("Brunch"))
        assertEquals("brunch", naechsteMahlzeit(listOf(geplant("brunch", "Brunch")), um(22))?.mahlzeit?.planEintragId)
        assertEquals(LocalTime.of(14, 30), fensterEnde("Mittagessen"))
    }

    @Test
    fun nichtsGeplantNull() {
        assertNull(naechsteMahlzeit(emptyList(), um(12)))
    }

    @Test
    fun mahlzeitNachUhrzeitGrenzen() {
        assertEquals(Mahlzeit.FRUEHSTUECK, mahlzeitNachUhrzeit(LocalTime.of(0, 0)))
        assertEquals(Mahlzeit.FRUEHSTUECK, mahlzeitNachUhrzeit(LocalTime.of(9, 59)))
        assertEquals(Mahlzeit.MITTAGESSEN, mahlzeitNachUhrzeit(LocalTime.of(10, 0)))
        assertEquals(Mahlzeit.SNACK, mahlzeitNachUhrzeit(LocalTime.of(14, 30)))
        assertEquals(Mahlzeit.ABENDESSEN, mahlzeitNachUhrzeit(LocalTime.of(17, 0)))
        assertEquals(Mahlzeit.ABENDESSEN, mahlzeitNachUhrzeit(LocalTime.of(23, 59)))
    }
}

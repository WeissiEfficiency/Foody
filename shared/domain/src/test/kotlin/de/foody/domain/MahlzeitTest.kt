package de.foody.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MahlzeitTest {
    @Test fun ausTextErkenntNamenUndDeutscheBezeichnung() {
        assertEquals(Mahlzeit.FRUEHSTUECK, Mahlzeit.ausText("Frühstück"))
        assertEquals(Mahlzeit.FRUEHSTUECK, Mahlzeit.ausText("FRÜHSTÜCK"))
        assertEquals(Mahlzeit.FRUEHSTUECK, Mahlzeit.ausText("FRUEHSTUECK"))
        assertEquals(Mahlzeit.ABENDESSEN, Mahlzeit.ausText(" abendessen "))
        assertEquals(Mahlzeit.MITTAGESSEN, Mahlzeit.ausText("Mittagessen"))
        assertEquals(Mahlzeit.SNACK, Mahlzeit.ausText("snack"))
        assertNull(Mahlzeit.ausText("Brunch"))
    }

    @Test fun mengeAusUnterscheidetNullUndLeer() {
        assertNull(Mahlzeit.mengeAus(null))
        assertEquals(emptySet(), Mahlzeit.mengeAus(""))
        assertEquals(setOf(Mahlzeit.ABENDESSEN), Mahlzeit.mengeAus("XYZ,ABENDESSEN"))
        assertEquals(setOf(Gang.VORSPEISE, Gang.HAUPTSPEISE), Gang.mengeAus("HAUPTSPEISE, VORSPEISE"))
    }

    @Test fun alsTextIstStabil() {
        assertNull(alsText<Mahlzeit>(null))
        assertEquals("", alsText(emptySet<Mahlzeit>()))
        assertEquals("MITTAGESSEN,ABENDESSEN", alsText(setOf(Mahlzeit.ABENDESSEN, Mahlzeit.MITTAGESSEN)))
    }

    @Test fun reihenfolgeImTag() {
        val sortiert = listOf("ABENDESSEN", "Brunch", "SNACK", "FRUEHSTUECK", "MITTAGESSEN").sortedBy(Mahlzeit::reihenfolge)
        assertEquals(listOf("FRUEHSTUECK", "MITTAGESSEN", "SNACK", "ABENDESSEN", "Brunch"), sortiert)
    }
}

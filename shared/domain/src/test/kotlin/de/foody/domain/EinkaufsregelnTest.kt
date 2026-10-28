package de.foody.domain

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EinkaufsregelnTest {
    private fun g(n: Int) = Quantity.of(BigDecimal(n), MeasureUnit.GRAM)
    private fun stk(n: Int) = Quantity.of(BigDecimal(n), MeasureUnit.PIECE)
    private val heute = LocalDate.of(2026, 10, 12)

    private val reis = Ingredient("reis", "Reis")
    private val salz = Ingredient("salz", "Salz")
    private val wasser = Ingredient("wasser", "Leitungswasser")
    private val zutaten = listOf(reis, salz, wasser).associateBy { it.id }

    private fun zeile(id: String, zutat: String, menge: Int, einheit: MeasureUnit = MeasureUnit.GRAM, optional: Boolean = false) =
        RecipeIngredient(id, zutat, BigDecimal(menge), einheit, optional)

    private val rezept = Recipe("r1", "Reispfanne", 2, listOf(
        zeile("l1", "reis", 200), zeile("l2", "salz", 0), zeile("l3", "wasser", 500, MeasureUnit.MILLILITER),
    ))

    @Test fun abgehaktBleibtBeiWenigerOderGleich() {
        assertTrue(EinkaufsAbgleich.bleibtAbgehakt(true, g(500), g(400)))
        assertTrue(EinkaufsAbgleich.bleibtAbgehakt(true, g(500), g(500)))
        assertFalse(EinkaufsAbgleich.bleibtAbgehakt(false, g(500), g(400)), "offen bleibt offen")
    }

    @Test fun abgehaktKommtZurueckBeiMehrOderAndererDimension() {
        assertFalse(EinkaufsAbgleich.bleibtAbgehakt(true, g(500), g(600)))
        assertFalse(EinkaufsAbgleich.bleibtAbgehakt(true, g(500), stk(1)))
    }

    @Test fun entfaelltLoeschtNurOffene() {
        assertTrue(EinkaufsAbgleich.entfaelltLoeschen(false))
        assertFalse(EinkaufsAbgleich.entfaelltLoeschen(true))
    }

    @Test fun bedarfOhneWasserUndOhneNachBedarf() {
        val slot = MealSlot("s1", heute, "ABENDESSEN", "r1", 2)
        val bedarf = EinkaufsBedarf.berechnen(DateRange.ofDays(heute, 7), listOf(slot), mapOf("r1" to rezept), zutaten)
        assertEquals(listOf("reis"), bedarf.map { it.ingredientId })
        assertEquals(0, bedarf.single().toBuy.baseAmount.compareTo(BigDecimal(200)))
    }

    @Test fun bedarfMitAusschluss() {
        val slot = MealSlot("s1", heute, "ABENDESSEN", "r1", 2)
        assertTrue(EinkaufsBedarf.berechnen(DateRange.ofDays(heute, 7), listOf(slot), mapOf("r1" to rezept), zutaten, setOf("reis")).isEmpty())
    }

    @Test fun rezeptAufListeAddiertInBasiseinheit() {
        val bedarf = RezeptAufListe.bedarf(rezept, 2, zutaten, heute)
        val bestand = listOf(RezeptAufListe.Bestand("e1", "reis", Quantity.of(BigDecimal("0.5"), MeasureUnit.KILOGRAM), manuell = true, abgehakt = false))
        val aenderung = RezeptAufListe.zusammenfuehren(bedarf, bestand).single() as RezeptAufListe.Aenderung.Erhoehen
        assertEquals("e1", aenderung.id)
        assertEquals(0, aenderung.neu.baseAmount.compareTo(BigDecimal(700)))
    }

    @Test fun rezeptAufListeNichtAufAbgehaktOderGeneriert() {
        val bedarf = RezeptAufListe.bedarf(rezept, 4, zutaten, heute)
        val bestand = listOf(
            RezeptAufListe.Bestand("e1", "reis", g(100), manuell = true, abgehakt = true),
            RezeptAufListe.Bestand("e2", "reis", g(100), manuell = false, abgehakt = false),
        )
        val neu = RezeptAufListe.zusammenfuehren(bedarf, bestand).single() as RezeptAufListe.Aenderung.Neu
        assertEquals(0, neu.bedarf.toBuy.baseAmount.compareTo(BigDecimal(400)), "skaliert auf 4 Portionen")
    }

    @Test fun eingabeBekannterArtikelOffenWirdAbgehakt() {
        val aktion = EinkaufEingabe.entscheiden("milch", listOf(EinkaufEingabe.Eintrag("e1", "Milch", abgehakt = false)))
        assertEquals(EinkaufEingabe.Aktion.Abhaken("e1"), aktion)
    }

    @Test fun eingabeGekaufterArtikelKommtZurueck() {
        val aktion = EinkaufEingabe.entscheiden("Milch", listOf(EinkaufEingabe.Eintrag("e1", "Milch", abgehakt = true)))
        assertEquals(EinkaufEingabe.Aktion.Zurueck("e1"), aktion)
    }

    @Test fun eingabeNeuMitAbteilung() {
        assertEquals(EinkaufEingabe.Aktion.Neu("Milch", ShoppingCatalog.sectionFor(null, "Milch")), EinkaufEingabe.entscheiden(" milch ", emptyList()))
        assertEquals(EinkaufEingabe.Aktion.Neu("Grillkohle XL", ShoppingCatalog.OWN_ITEMS), EinkaufEingabe.entscheiden("Grillkohle XL", emptyList()))
        assertNull(EinkaufEingabe.entscheiden("  ", emptyList()))
    }
}

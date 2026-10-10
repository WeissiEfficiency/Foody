package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Nährwerttabelle aus erkanntem Text: Zeilen über die Höhe, Stichwort plus erste Zahl mit Einheit. */
class NaehrwertScanTest {
    private var y = 0

    /** Eine Tabellenzeile als ein Element (einspaltig erkannt). */
    private fun zeile(text: String): OcrElement { y += 40; return OcrElement(text, 10, y, y + 30) }

    /** Bezeichnung und Wert als getrennte Elemente gleicher Höhe (zweispaltig erkannt). */
    private fun zweispaltig(bezeichnung: String, wert: String, versatz: Int = 0): List<OcrElement> {
        y += 40
        return listOf(OcrElement(bezeichnung, 10, y, y + 30), OcrElement(wert, 400, y + versatz, y + 30 + versatz))
    }

    private fun Map<Nutrient, BigDecimal>.wert(n: Nutrient) = this[n]?.stripTrailingZeros()?.toPlainString()

    @Test fun einspaltigeTabelle() {
        val e = NaehrwertScan.auswerten(
            listOf(
                zeile("Nährwerte je 100 g"),
                zeile("Brennwert 1.234 kJ / 295 kcal"),
                zeile("Fett 3,5 g"),
                zeile("davon gesättigte Fettsäuren 2,1 g"),
                zeile("Kohlenhydrate 45 g"),
                zeile("davon Zucker 12 g"),
                zeile("Ballaststoffe 3 g"),
                zeile("Eiweiß 8,0 g"),
                zeile("Salz 0,02 g"),
            ),
        )
        assertEquals(NutrientBasis.PER_100_G, e.basis)
        assertEquals("1234", e.werte.wert(Nutrient.ENERGY_KJ))
        assertEquals("3.5", e.werte.wert(Nutrient.FAT_G))
        assertEquals("45", e.werte.wert(Nutrient.CARBS_G))
        assertEquals("12", e.werte.wert(Nutrient.SUGAR_G))
        assertEquals("3", e.werte.wert(Nutrient.FIBER_G))
        assertEquals("8", e.werte.wert(Nutrient.PROTEIN_G))
        assertEquals("0.02", e.werte.wert(Nutrient.SALT_G))
    }

    @Test fun gesaettigteVorFettVerwechseltNicht() {
        val e = NaehrwertScan.auswerten(listOf(zeile("davon gesättigte Fettsäuren 2,1 g"), zeile("Fett 3,5 g")))
        assertEquals("3.5", e.werte.wert(Nutrient.FAT_G))
    }

    @Test fun zweispaltigeTabelleUeberDieHoehe() {
        val e = NaehrwertScan.auswerten(
            zweispaltig("Energie", "1500 kJ", versatz = 3) +
                zweispaltig("Fett", "2,0 g", versatz = -4) +
                zweispaltig("Kohlenhydrate", "60 g") +
                zweispaltig("Eiweiß", "11 g", versatz = 2),
        )
        assertEquals("1500", e.werte.wert(Nutrient.ENERGY_KJ))
        assertEquals("2", e.werte.wert(Nutrient.FAT_G))
        assertEquals("60", e.werte.wert(Nutrient.CARBS_G))
        assertEquals("11", e.werte.wert(Nutrient.PROTEIN_G))
    }

    @Test fun nurKcalWirdUmgerechnet() =
        assertEquals("1046", NaehrwertScan.auswerten(listOf(zeile("Energie 250 kcal"))).werte.wert(Nutrient.ENERGY_KJ))

    @Test fun kleinerAlsUndSpuren() {
        val e = NaehrwertScan.auswerten(listOf(zeile("Salz <0,5 g"), zeile("Zucker Spuren"), zeile("Kohlenhydrate 1 g")))
        assertEquals("0.5", e.werte.wert(Nutrient.SALT_G))
        assertEquals("0", e.werte.wert(Nutrient.SUGAR_G))
    }

    @Test fun englisch() {
        val e = NaehrwertScan.auswerten(listOf(zeile("Energy 1500 kJ"), zeile("Protein 10 g"), zeile("Fat 2 g")))
        assertEquals("1500", e.werte.wert(Nutrient.ENERGY_KJ))
        assertEquals("10", e.werte.wert(Nutrient.PROTEIN_G))
        assertEquals("2", e.werte.wert(Nutrient.FAT_G))
    }

    @Test fun basisMilliliter() =
        assertEquals(NutrientBasis.PER_100_ML, NaehrwertScan.auswerten(listOf(zeile("Nährwerte je 100 ml"), zeile("Fett 1,5 g"))).basis)

    @Test fun unplausiblesWirdVerworfen() {
        val e = NaehrwertScan.auswerten(listOf(zeile("Fett 350 g"), zeile("Kohlenhydrate 40 g"), zeile("davon Zucker 50 g"), zeile("Energie 9000 kJ")))
        assertFalse(Nutrient.FAT_G in e.werte)
        assertFalse(Nutrient.SUGAR_G in e.werte, "Zucker > Kohlenhydrate")
        assertFalse(Nutrient.ENERGY_KJ in e.werte)
        assertEquals("40", e.werte.wert(Nutrient.CARBS_G))
    }

    @Test fun leereEingabe() {
        val e = NaehrwertScan.auswerten(emptyList())
        assertTrue(e.werte.isEmpty())
        assertEquals(NutrientBasis.PER_100_G, e.basis)
    }
}

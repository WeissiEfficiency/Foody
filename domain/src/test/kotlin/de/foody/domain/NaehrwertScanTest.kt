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
        assertEquals<NutrientBasis?>(null, e.basis)
    }

    /** Ohne „100 g“/„100 ml“ im Bild bleibt die Basis offen – ein Getränk würde sonst still auf Gramm gesetzt. */
    @Test fun basisOhneAngabeBleibtOffen() {
        val e = NaehrwertScan.auswerten(listOf(zeile("Fett 1,5 g"), zeile("Kohlenhydrate 4,8 g")))
        assertEquals<NutrientBasis?>(null, e.basis)
        assertEquals("1.5", e.werte.wert(Nutrient.FAT_G))
    }

    /** Echte ML-Kit-Ausgabe eines leicht schiefen Fotos (Emulator): „ß“ → „s“ und „g“ → „9“ sind typische Lesefehler. */
    @Test fun echteTexterkennungMitLesefehlern() {
        val e = NaehrwertScan.auswerten(
            listOf(
                OcrElement("Nährwerte", 54, 66, 104), OcrElement("Brennwert", 56, 142, 176), OcrElement("Fett", 58, 218, 252),
                OcrElement("davon gesättigte Fettsäuren", 59, 282, 334), OcrElement("Kohlenhydrate", 62, 368, 406),
                OcrElement("davon Zucker", 63, 440, 477), OcrElement("Ballaststoffe", 66, 514, 553), OcrElement("Eiweis", 68, 595, 627),
                OcrElement("Salz", 69, 669, 701), OcrElement("je 100 g", 620, 52, 94), OcrElement("1.234 kJ / 295 kcal", 627, 121, 162),
                OcrElement("3,5 g", 626, 203, 243), OcrElement("2,1 g", 628, 282, 318), OcrElement("45 g", 630, 356, 394),
                OcrElement("12 9", 633, 428, 467), OcrElement("3,0 g", 634, 503, 544), OcrElement("8,0 g", 638, 582, 618),
                OcrElement("0,02 g", 637, 653, 692),
            ),
        )
        assertEquals("1234", e.werte.wert(Nutrient.ENERGY_KJ))
        assertEquals("3.5", e.werte.wert(Nutrient.FAT_G))
        assertEquals("45", e.werte.wert(Nutrient.CARBS_G))
        assertEquals("12", e.werte.wert(Nutrient.SUGAR_G), "„12 9“ ist „12 g“")
        assertEquals("3", e.werte.wert(Nutrient.FIBER_G))
        assertEquals("8", e.werte.wert(Nutrient.PROTEIN_G), "„Eiweis“ ist Eiweiß")
        assertEquals("0.02", e.werte.wert(Nutrient.SALT_G))
    }

    @Test fun neunAlsGrammNurAmZeilenende() {
        // „9“ direkt hinter der Zahl am Ende gilt als „g“; eine echte Zahl 9 mit Einheit bleibt, wie sie ist
        assertEquals("9", NaehrwertScan.auswerten(listOf(zeile("Fett 9 g"))).werte.wert(Nutrient.FAT_G))
        assertEquals("0.5", NaehrwertScan.auswerten(listOf(zeile("Salz 0,5 9"))).werte.wert(Nutrient.SALT_G))
    }

    /**
     * Stärker schiefes Foto: Die Wertspalte liegt um fast eine Zeilenhöhe höher als ihre Bezeichnung. Mit dem
     * Neigungswinkel der Zeilen (ML Kit `Line.angle`) werden die Höhen entzerrt, sonst rutschen Werte eine Zeile weiter.
     */
    @Test fun schiefesFotoMitWinkel() {
        val winkel = -3.8f // Grad; rechts höher als links
        val hoehe = 30
        val abstand = 40
        val versatz = (kotlin.math.tan(Math.toRadians(winkel.toDouble())) * 390).toInt() // ≈ -26 px bei 390 px Abstand
        val zeilen = listOf("Energie" to "1500 kJ", "Fett" to "3,5 g", "Kohlenhydrate" to "45 g", "Eiweiß" to "8 g", "Salz" to "1 g")
        val elemente = zeilen.flatMapIndexed { i, (l, v) ->
            val y = 100 + i * abstand
            listOf(OcrElement(l, 10, y, y + hoehe, winkel), OcrElement(v, 400, y + versatz, y + versatz + hoehe, winkel))
        }
        val e = NaehrwertScan.auswerten(elemente)
        assertEquals("1500", e.werte.wert(Nutrient.ENERGY_KJ))
        assertEquals("3.5", e.werte.wert(Nutrient.FAT_G))
        assertEquals("45", e.werte.wert(Nutrient.CARBS_G))
        assertEquals("8", e.werte.wert(Nutrient.PROTEIN_G))
        assertEquals("1", e.werte.wert(Nutrient.SALT_G))
    }

    @Test fun neunAlsGrammNimmtErsteSpalte() =
        assertEquals("12", NaehrwertScan.auswerten(listOf(zeile("Zucker 12 9 3 9"), zeile("Kohlenhydrate 40 9 10 9"))).werte.wert(Nutrient.SUGAR_G))

    /** Englische Packung: „1,046 kJ“ ist Tausendertrennung (Energie hat nie drei Nachkommastellen), „3,5 g“ bleibt Dezimal. */
    @Test fun englischeTausendertrennungBeiEnergie() {
        val e = NaehrwertScan.auswerten(listOf(zeile("Energy 1,046 kJ / 250 kcal"), zeile("Fat 3,5 g")))
        assertEquals("1046", e.werte.wert(Nutrient.ENERGY_KJ))
        assertEquals("3.5", e.werte.wert(Nutrient.FAT_G))
    }
}

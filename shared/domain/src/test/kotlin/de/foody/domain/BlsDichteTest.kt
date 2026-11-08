package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlsDichteTest {
    /** Zeile wie im Auszug: kJ, Eiweiß, Kohlenhydrate, Zucker, Fett, Ballaststoffe, Salz; leer = unbekannt. */
    private fun l(code: String, name: String, vararg werte: String): BlsLebensmittel {
        val n = listOf(
            Nutrient.ENERGY_KJ, Nutrient.PROTEIN_G, Nutrient.CARBS_G, Nutrient.SUGAR_G, Nutrient.FAT_G, Nutrient.FIBER_G, Nutrient.SALT_G,
        )
        return BlsLebensmittel(code, name, n.zip(werte).filter { it.second.isNotEmpty() }.associate { it.first to BigDecimal(it.second) })
    }

    private fun assertNahe(erwartet: String, dichte: BigDecimal?, toleranz: String = "0.02") {
        requireNotNull(dichte) { "keine Dichte, erwartet $erwartet" }
        assertTrue((dichte - BigDecimal(erwartet)).abs() <= BigDecimal(toleranz), "Dichte $dichte, erwartet etwa $erwartet")
    }

    // Echte Zeilen aus bls_4_0.tsv; Vergleichswerte: übliche Dichten (g/ml) bei Raumtemperatur
    private val wasser = l("N110000", "Trinkwasser", "0", "0", "0", "0", "0", "0", "0.006")
    private val vollmilch = l("M111300", "Vollmilch frisch, 3,5 % Fett, pasteurisiert", "259", "3.55", "4.03", "4.03", "3.49", "0", "0.087")
    private val sahne = l("M173800", "Schlagsahne mind. 30 % Fett", "1270", "2.312", "3.27", "3.27", "31.7", "0", "0.085")
    private val olivenoel = l("Q120000", "Olivenöl", "3696", "0", "0", "0", "99.9", "0", "0.004")
    private val orangensaft = l("F603600", "Orangensaft", "173", "0.75", "8.5", "8.5", "", "0.31", "0.005")
    private val cola = l("N330000", "Colagetränk koffeinhaltig", "175", "0", "10.3", "10.3", "0", "0", "0.002")
    private val kondensmilch = l("M181400", "Kondensmilch mind. 8 % Fett, gezuckert", "1356", "7.91", "52.9", "52.9", "8.7", "0", "0.28")
    private val export = l("P162000", "Export hell", "174", "0.485", "2.238", "0.172", "", "0", "0.004")
    private val rum = l("P741000", "Rum 37,5/40 % vol", "887", "0", "0", "0", "0", "0", "0.002")

    @Test fun fluessigesBekommtEineDichteAusSeinenBestandteilen() {
        assertEquals(BigDecimal("1.00"), BlsDichte.gProMl(wasser))
        assertNahe("1.03", BlsDichte.gProMl(vollmilch))
        assertNahe("0.99", BlsDichte.gProMl(sahne))
        assertNahe("0.92", BlsDichte.gProMl(olivenoel))
        assertNahe("1.04", BlsDichte.gProMl(orangensaft))
        assertNahe("1.04", BlsDichte.gProMl(cola))
        assertNahe("1.30", BlsDichte.gProMl(kondensmilch), toleranz = "0.03")
    }

    @Test fun alkoholAusDerRestenergieMachtGetraenkeLeichter() {
        // Bier gut 1,00; Rum mit 37,5–40 % vol etwa 0,95 – ohne Alkohol käme Wasser heraus
        assertNahe("1.01", BlsDichte.gProMl(export))
        assertNahe("0.95", BlsDichte.gProMl(rum))
    }

    @Test fun festesUndPulverBleibenOhneDichte() {
        assertNull(BlsDichte.gProMl(l("B101000", "Vollkornbrot", "885", "7.16", "38", "0.817", "1.01", "10.04", "1.198")))
        assertNull(BlsDichte.gProMl(l("N420900", "Instantkaffeepulver", "624")))
        assertNull(BlsDichte.gProMl(l("T513000", "Makrele im eigenen Saft", "800")))
        assertNull(BlsDichte.gProMl(l("B107300", "Vollkornbrot mit Buttermilch", "893")))
        assertNull(BlsDichte.gProMl(l("D720200", "Sahne-Windbeutel (Brandmasse)", "1100")))
        assertNull(BlsDichte.gProMl(l("R922000", "Joghurtsahne für Gebäck/Torten", "700")))
        assertNull(BlsDichte.gProMl(l("M400500", "Schnittkäse mind. 40 % Fett i. Tr.", "1320")))
        assertNull(BlsDichte.gProMl(l("N503900", "Cappuccino Instantpulver, gesüßt", "1695")))
        assertNull(BlsDichte.gProMl(l("R810000", "Bouillon/Brühe/Suppe (Brühwürfel, Pulver)", "800")))
        assertNull(BlsDichte.gProMl(l("M884000", "Magermilchpulver", "1500")))
    }

    @Test fun fluessigNachGruppeOderNachDemWortVorneImNamen() {
        // Gruppe N (Getränke) und P (alkoholische Getränke) ganz; sonst zählt das Lebensmittel vor „mit“, „aus“, „,“ …
        assertTrue(BlsDichte.istFluessig(l("N630000", "Schwarztee (Getränk)", "0")))
        assertTrue(BlsDichte.istFluessig(l("P722000", "Gin", "1019")))
        assertTrue(BlsDichte.istFluessig(l("M113300", "H-Vollmilch 3,5 % Fett, ultrahocherhitzt", "264")))
        assertTrue(BlsDichte.istFluessig(l("M172500", "Sauerrahm/Saure Sahne, mind. 10 % Fett", "500")))
        assertTrue(BlsDichte.istFluessig(l("H841100", "Sojadrink ungesüßt", "170")))
        assertTrue(BlsDichte.istFluessig(l("X416243", "Gemüsebrühe", "20")))
        assertTrue(BlsDichte.istFluessig(l("X444343", "Tomatensuppe gebunden", "182")))
        assertTrue(BlsDichte.istFluessig(l("R125000", "Balsamicoessig", "400")))
        assertTrue(BlsDichte.istFluessig(l("S151100", "Ahornsirup", "1021")))
        // Aus Pulver angerührt ist es wieder flüssig
        assertTrue(BlsDichte.istFluessig(l("N420100", "Kaffee (Getränk) aus Instantpulver", "7")))
        assertTrue(BlsDichte.istFluessig(l("K850532", "Pilzcremesuppe aus Instantpulver (mit Wasser zubereitet)", "200")))
    }
}

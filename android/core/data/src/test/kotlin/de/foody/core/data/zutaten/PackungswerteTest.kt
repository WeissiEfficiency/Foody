package de.foody.core.data.zutaten

import de.foody.core.data.db.ZutatEntity
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Werte einer gescannten Packung und ihre Übernahme in den Editor-Entwurf. */
class PackungswerteTest {
    private val leer = ZutatEntwurf.aus(ZutatEntity(id = "z", canonicalName = "Reis", createdAt = 1, updatedAt = 1))

    private fun packung(
        werte: Map<Nutrient, BigDecimal>,
        basis: NutrientBasis? = NutrientBasis.PER_100_G,
        strichcode: String? = null,
    ) = Packungswerte(name = "Reis", basis = basis, werte = werte, strichcode = strichcode, quelle = "Open Food Facts")

    @Test
    fun kcalAusKj() {
        val p = packung(mapOf(Nutrient.ENERGY_KJ to BigDecimal("1502")))
        assertEquals(359, p.kcal)
        assertEquals("359", p.kcalText)
        val ohne = packung(mapOf(Nutrient.PROTEIN_G to BigDecimal("7")))
        assertNull(ohne.kcal)
        assertEquals("", ohne.kcalText)
    }

    @Test
    fun kjFuerNachEnergieRegel() {
        val p = packung(mapOf(Nutrient.ENERGY_KJ to BigDecimal("1502")))
        // kcal-Text unverändert → genauer Wert der Packung, sonst umgerechnet (360 × 4,184)
        assertEquals(BigDecimal("1502"), p.kjFuer("359"))
        assertEquals(0, BigDecimal("1506.24").compareTo(p.kjFuer("360")))
        assertNull(p.kjFuer(""))
        assertNull(p.kjFuer("abc"))
        // Leerzeichen um den Text ändern den Wert nicht
        assertEquals(BigDecimal("1502"), p.kjFuer(" 359 "))
    }

    @Test
    fun feldTextUngerundet() {
        val p = packung(mapOf(Nutrient.FAT_G to BigDecimal("7.250")))
        assertEquals("7,25", p.feldText(Nutrient.FAT_G))
        assertEquals("", p.feldText(Nutrient.SALT_G))
    }

    @Test
    fun mitPackungUebernimmtWerte() {
        val p = packung(mapOf(Nutrient.ENERGY_KJ to BigDecimal("1502"), Nutrient.PROTEIN_G to BigDecimal("7.5"), Nutrient.SALT_G to BigDecimal("0.01")))
        val (e, markiert) = leer.mitPackung(p)
        assertEquals("359", e.kcal)
        assertEquals("7,5", e.eiweiss)
        assertEquals("0,01", e.salz)
        assertEquals("", e.fett)
        assertEquals("Open Food Facts", e.quelle)
        assertEquals(NutrientBasis.PER_100_G, e.basis)
        assertEquals(setOf(ZutatFeld.KCAL, ZutatFeld.EIWEISS, ZutatFeld.SALZ), markiert)
    }

    @Test
    fun mitPackungAlleMakros() {
        val p = packung(
            mapOf(
                Nutrient.CARBS_G to BigDecimal("70"), Nutrient.SUGAR_G to BigDecimal("1"), Nutrient.FAT_G to BigDecimal("2"),
                Nutrient.FIBER_G to BigDecimal("3"),
            ),
        )
        val (e, markiert) = leer.mitPackung(p)
        assertEquals(listOf("70", "1", "2", "3"), listOf(e.kohlenhydrate, e.zucker, e.fett, e.ballaststoffe))
        assertEquals(setOf(ZutatFeld.KOHLENHYDRATE, ZutatFeld.ZUCKER, ZutatFeld.FETT, ZutatFeld.BALLASTSTOFFE), markiert)
    }

    @Test
    fun mitPackungOhneBasisBehaeltBasis() {
        val ml = leer.copy(basis = NutrientBasis.PER_100_ML)
        val (e, _) = ml.mitPackung(packung(mapOf(Nutrient.ENERGY_KJ to BigDecimal("200")), basis = null))
        assertEquals(NutrientBasis.PER_100_ML, e.basis)
        val (mitBasis, _) = ml.mitPackung(packung(mapOf(Nutrient.ENERGY_KJ to BigDecimal("200"))))
        assertEquals(NutrientBasis.PER_100_G, mitBasis.basis)
    }

    @Test
    fun strichcodeNurWennLeer() {
        val p = packung(mapOf(Nutrient.ENERGY_KJ to BigDecimal("200")), strichcode = "4001234567890")
        val (e, markiert) = leer.mitPackung(p)
        assertEquals("4001234567890", e.strichcode)
        assertEquals(setOf(ZutatFeld.KCAL, ZutatFeld.STRICHCODE), markiert)
        val (gesetzt, m2) = leer.copy(strichcode = "40000000").mitPackung(p)
        assertEquals("40000000", gesetzt.strichcode)
        assertEquals(setOf(ZutatFeld.KCAL), m2)
    }

    @Test
    fun fehlendeWerteBleiben() {
        val (e, _) = leer.copy(fett = "3", kcal = "100").mitPackung(packung(mapOf(Nutrient.PROTEIN_G to BigDecimal("1"))))
        assertEquals("3", e.fett)
        assertEquals("100", e.kcal)
        assertEquals("1", e.eiweiss)
    }

    @Test
    fun mitPackungswertenGleicheBasisBehaeltFehlende() {
        val z = ZutatEntity(id = "z", canonicalName = "Milch", nutrientBasis = NutrientBasis.PER_100_ML, fat = BigDecimal("3.5"), createdAt = 1, updatedAt = 1)
        val neu = z.mitPackungswerten(packung(mapOf(Nutrient.ENERGY_KJ to BigDecimal("270")), basis = NutrientBasis.PER_100_ML))
        assertEquals(BigDecimal("3.5"), neu.fat)
        assertEquals(BigDecimal("270"), neu.energyKj)
    }

    @Test
    fun mitPackungswertenNeueBasisVerwirftAlteWerte() {
        // Fett je 100 ml passt nicht zu Werten je 100 g
        val z = ZutatEntity(id = "z", canonicalName = "Milch", nutrientBasis = NutrientBasis.PER_100_ML, fat = BigDecimal("3.5"), createdAt = 1, updatedAt = 1)
        val neu = z.mitPackungswerten(packung(mapOf(Nutrient.ENERGY_KJ to BigDecimal("270"))))
        assertEquals(NutrientBasis.PER_100_G, neu.nutrientBasis)
        assertNull(neu.fat)
        assertEquals(BigDecimal("270"), neu.energyKj)
        // Foto ohne erkannte Basis: Die bisherige bleibt und mit ihr die Werte
        val foto = z.mitPackungswerten(packung(mapOf(Nutrient.ENERGY_KJ to BigDecimal("270")), basis = null))
        assertEquals(NutrientBasis.PER_100_ML, foto.nutrientBasis)
        assertEquals(BigDecimal("3.5"), foto.fat)
    }
}

package de.foody.core.data.bls

import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlsNachschlagTest {
    /** Der eingecheckte Auszug (Arbeitsverzeichnis der Tests ist der Modulordner). */
    private val asset = File("src/main/assets/${BlsNachschlag.ASSET}")

    @Test
    fun liestDenEchtenAuszug() = runTest {
        val alle = blsAuszugLesen(asset.readLines(Charsets.UTF_8).asSequence())
        assertTrue(alle.size > 7000, "gut 7.000 Lebensmittel, nicht ${alle.size}")
        assertEquals(alle.size, alle.map { it.code }.toSet().size, "Codes eindeutig")
        val breze = alle.single { it.code == "B723000" }
        assertEquals("Laugengebäck mit Hagelsalz", breze.name)
        assertEquals(0, BigDecimal("1191").compareTo(breze.werte[Nutrient.ENERGY_KJ]))
        assertEquals(0, BigDecimal("2.81").compareTo(breze.werte[Nutrient.SALT_G]))
    }

    @Test
    fun suchtImEchtenAuszug() = runTest {
        val bls = BlsNachschlag({ asset.readLines(Charsets.UTF_8).asSequence() }, EmptyCoroutineContext)
        assertEquals("Breze", bls.suchen("Breze").first().vorschlagName)
        assertEquals("Leberkäse", bls.suchen("Leberkas").first().vorschlagName)
        assertEquals("Fleischpflanzerl", bls.suchen("Fleischpflanzerl").first().vorschlagName)
        assertEquals("Weizenbrötchen", bls.suchen("Semmel").first().vorschlagName)
        val leberkassemmel = bls.suchen("Leberkassemmel").map { it.vorschlagName }
        assertTrue("Leberkäse" in leberkassemmel && "Weizenbrötchen" in leberkassemmel, leberkassemmel.toString())
        assertTrue(bls.suchen("").isEmpty())
    }

    @Test
    fun leereWerteSindUnbekanntUndKommentareFallenWeg() {
        val zeilen = sequenceOf("# Kopf", "code\tname\tkj\teiweiss\tkohlenhydrate\tzucker\tfett\tballaststoffe\tsalz", "X1\tTest\t100\t\t5\t\t\t\t")
        val l = blsAuszugLesen(zeilen).single()
        assertEquals(0, BigDecimal("100").compareTo(l.werte[Nutrient.ENERGY_KJ]))
        assertNull(l.werte[Nutrient.PROTEIN_G])
        assertEquals(setOf(Nutrient.ENERGY_KJ, Nutrient.CARBS_G), l.werte.keys)
    }

    @Test
    fun trefferAlsPackungMitQuelle() = runTest {
        val bls = BlsNachschlag({ sequenceOf("B723000\tLaugengebäck mit Hagelsalz\t1191\t9.132\t50\t1.4\t4.113\t3.746\t2.81") }, EmptyCoroutineContext)
        val p = bls.suchen("Brezn").single().alsPackung()
        assertEquals("Breze", p.name)
        assertEquals(NutrientBasis.PER_100_G, p.basis)
        assertEquals("BLS 4.0 (MRI): Laugengebäck mit Hagelsalz", p.quelle)
        assertNull(p.strichcode)
        assertEquals(285, p.kcal)
    }
}

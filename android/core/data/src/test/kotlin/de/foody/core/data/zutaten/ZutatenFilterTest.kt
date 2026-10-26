package de.foody.core.data.zutaten

import de.foody.domain.IngredientCatalog
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Suche und Filterchips des Zutatenkatalogs. */
class ZutatenFilterTest {
    private fun kurz(name: String, kcal: Int? = 100, rezepte: Int = 1, strichcode: String? = null, eigen: Boolean = false) =
        ZutatKurz(id = name, name = name, kategorie = null, kcal = kcal, rezepte = rezepte, strichcode = strichcode, eigen = eigen)

    private val reis = kurz("Reis")
    private val wuerze = kurz("Meine Würze", kcal = null, rezepte = 0, eigen = true)
    private val saft = kurz("Saft", strichcode = "4001234567890", eigen = true)
    private val salz = kurz("Salz", rezepte = 0)
    private val alle = listOf(reis, wuerze, saft, salz)

    private fun namen(l: List<ZutatKurz>) = l.map { it.name }

    @Test
    fun ohneFilterAlle() = assertEquals(alle, filtern(alle, "", emptySet()))

    @Test
    fun ohneNaehrwerte() = assertEquals(listOf("Meine Würze"), namen(filtern(alle, "", setOf(ZutatFilter.OHNE_NAEHRWERTE))))

    @Test
    fun eigene() = assertEquals(listOf("Meine Würze", "Saft"), namen(filtern(alle, "", setOf(ZutatFilter.EIGENE))))

    @Test
    fun unbenutzt() = assertEquals(listOf("Meine Würze", "Salz"), namen(filtern(alle, "", setOf(ZutatFilter.UNBENUTZT))))

    @Test
    fun mitStrichcode() = assertEquals(listOf("Saft"), namen(filtern(alle, "", setOf(ZutatFilter.MIT_STRICHCODE))))

    @Test
    fun filterZusammen() =
        assertEquals(listOf("Meine Würze"), namen(filtern(alle, "", setOf(ZutatFilter.EIGENE, ZutatFilter.UNBENUTZT))))

    @Test
    fun sucheOhneGrossKlein() = assertEquals(listOf("Zwiebel"), namen(filtern(listOf(kurz("Zwiebel"), reis), "zwieb", emptySet())))

    @Test
    fun sucheMitLeerzeichen() = assertEquals(listOf("Reis"), namen(filtern(alle, " reis ", emptySet())))

    @Test
    fun sucheUeberKatalog() {
        val c = IngredientCatalog.canonicalName("Zwiebeln")
        assertNotEquals("Zwiebeln", c)
        assertEquals(listOf(c), namen(filtern(listOf(kurz(c), reis), "Zwiebeln", emptySet())))
    }
}

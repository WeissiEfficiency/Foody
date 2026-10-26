package de.foody.core.data.zutaten

import de.foody.core.data.db.ZutatEntity
import de.foody.domain.NutrientBasis
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Ergänzungsregel beim Zusammenführen (wie v1 `fillFrom`): Nährwerte als Block, sonst Einzelfelder nur wenn leer. */
class ZutatErgaenzenTest {
    private fun zutat(id: String) = ZutatEntity(id = id, canonicalName = "Zutat $id", createdAt = 1, updatedAt = 1)

    private val quelle = zutat("q").copy(
        nutrientBasis = NutrientBasis.PER_100_ML, energyKj = BigDecimal("100"), protein = BigDecimal("1"),
        carbs = BigDecimal("2"), fat = BigDecimal("3"), fiber = BigDecimal("4"), sugar = BigDecimal("5"),
        salt = BigDecimal("6"), nutrientSource = "Packung",
        category = "Kühlregal", densityGPerMl = BigDecimal("1.03"), pieceWeightG = BigDecimal("50"), barcode = "4001234567890",
    )

    @Test
    fun naehrwerteAlsBlockNurOhneBasis() {
        val ohne = zutat("z").ergaenztUm(quelle)
        assertEquals(NutrientBasis.PER_100_ML, ohne.nutrientBasis)
        assertEquals(
            listOf("100", "1", "2", "3", "4", "5", "6"),
            listOf(ohne.energyKj, ohne.protein, ohne.carbs, ohne.fat, ohne.fiber, ohne.sugar, ohne.salt).map { it.toString() },
        )
        assertEquals("Packung", ohne.nutrientSource)

        val mit = zutat("z").copy(nutrientBasis = NutrientBasis.PER_100_G, energyKj = BigDecimal("500")).ergaenztUm(quelle)
        assertEquals(NutrientBasis.PER_100_G, mit.nutrientBasis)
        assertEquals(BigDecimal("500"), mit.energyKj)
        assertNull(mit.protein)
    }

    @Test
    fun einzelfelderNurWennLeer() {
        val leer = zutat("z").ergaenztUm(quelle)
        assertEquals("Kühlregal", leer.category)
        assertEquals(BigDecimal("1.03"), leer.densityGPerMl)
        assertEquals(BigDecimal("50"), leer.pieceWeightG)
        assertEquals("4001234567890", leer.barcode)

        val gesetzt = zutat("z").copy(
            category = "Trockenwaren", densityGPerMl = BigDecimal("0.8"), pieceWeightG = BigDecimal("10"), barcode = "12345678",
        ).ergaenztUm(quelle)
        assertEquals("Trockenwaren", gesetzt.category)
        assertEquals(BigDecimal("0.8"), gesetzt.densityGPerMl)
        assertEquals(BigDecimal("10"), gesetzt.pieceWeightG)
        assertEquals("12345678", gesetzt.barcode)
    }
}

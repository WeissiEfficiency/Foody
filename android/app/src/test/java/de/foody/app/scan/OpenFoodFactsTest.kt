package de.foody.app.scan

import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Antworten von `world.openfoodfacts.org/api/v2/product/{code}.json` (gekürzt auf die angefragten Felder). */
class OpenFoodFactsTest {
    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
    private fun Packung.wert(n: Nutrient) = werte[n]?.stripTrailingZeros()?.toPlainString()

    @Test fun vollstaendigeAntwort() {
        val p = OpenFoodFacts.auswerten(
            json(
                """{"code":"4006040002031","status":1,"product":{"product_name":"Natural Yoghurt","product_name_de":"Naturjoghurt 3,5 %",
                "nutrition_data_per":"100g","nutriments":{"energy-kj_100g":276,"energy-kcal_100g":66,"fat_100g":3.5,
                "carbohydrates_100g":4.6,"sugars_100g":4.6,"fiber_100g":0,"proteins_100g":4.1,"salt_100g":0.13}}}""",
            ),
            "4006040002031",
        )!!
        assertEquals("Naturjoghurt 3,5 %", p.name)
        assertEquals(NutrientBasis.PER_100_G, p.basis)
        assertEquals("276", p.wert(Nutrient.ENERGY_KJ))
        assertEquals("3.5", p.wert(Nutrient.FAT_G))
        assertEquals("4.6", p.wert(Nutrient.SUGAR_G))
        assertEquals("4.1", p.wert(Nutrient.PROTEIN_G))
        assertEquals("0.13", p.wert(Nutrient.SALT_G))
        assertEquals("4006040002031", p.strichcode)
        assertEquals(OpenFoodFacts.QUELLE, p.quelle)
    }

    @Test fun nurKcalWirdUmgerechnet() {
        val p = OpenFoodFacts.auswerten(
            json("""{"status":1,"product":{"product_name":"Saft","nutrition_data_per":"100ml","nutriments":{"energy-kcal_100g":45}}}"""),
            "123",
        )!!
        assertEquals("188", p.wert(Nutrient.ENERGY_KJ))
        assertEquals(NutrientBasis.PER_100_ML, p.basis)
        assertEquals("Saft", p.name)
    }

    @Test fun ohneEnergieGiltAlsNichtGefunden() =
        assertNull(OpenFoodFacts.auswerten(json("""{"status":1,"product":{"product_name":"X","nutriments":{"fat_100g":3}}}"""), "1"))

    @Test fun unbekanntesProdukt() =
        assertNull(OpenFoodFacts.auswerten(json("""{"status":0,"status_verbose":"product not found"}"""), "1"))

    @Test fun unplausiblesWirdVerworfen() {
        val p = OpenFoodFacts.auswerten(
            json("""{"status":1,"product":{"product_name":"","nutriments":{"energy-kj_100g":1000,"fat_100g":250,"carbohydrates_100g":10,"sugars_100g":20}}}"""),
            "1",
        )!!
        assertFalse(Nutrient.FAT_G in p.werte)
        assertFalse(Nutrient.SUGAR_G in p.werte)
        assertNull(p.name, "leerer Name zählt nicht")
    }
}

package de.foody.app.scan

import de.foody.domain.NaehrwertScan
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode

/** Nährwerte einer Packung je 100 g bzw. 100 ml – aus Open Food Facts oder vom Foto; nur ein Vorschlag. */
data class Packung(
    val name: String?,
    /** `null`: auf dem Foto nicht erkennbar – die bisherige Basis bleibt. */
    val basis: NutrientBasis?,
    val werte: Map<Nutrient, BigDecimal>,
    val strichcode: String?,
    val quelle: String,
)

/** Wertet Antworten der Produktdatenbank Open Food Facts aus (`/api/v2/product/{code}.json`). */
object OpenFoodFacts {
    const val QUELLE = "Open Food Facts"

    private val felder = mapOf(
        Nutrient.FAT_G to "fat_100g",
        Nutrient.CARBS_G to "carbohydrates_100g",
        Nutrient.SUGAR_G to "sugars_100g",
        Nutrient.FIBER_G to "fiber_100g",
        Nutrient.PROTEIN_G to "proteins_100g",
        Nutrient.SALT_G to "salt_100g",
    )

    /** `null`, wenn das Produkt unbekannt ist oder keinen Energiewert hat (dann hilft nur das Foto). */
    fun auswerten(json: JsonObject, strichcode: String): Packung? {
        if (json["status"]?.jsonPrimitive?.intOrNull != 1) return null
        val produkt = json["product"]?.jsonObject ?: return null
        val n = produkt["nutriments"]?.jsonObject ?: return null
        fun zahl(key: String) = n[key]?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()
        // energy_100g liefert Open Food Facts in kJ
        val kj = zahl("energy-kj_100g")
            ?: zahl("energy-kcal_100g")?.multiply(BigDecimal("4.184"))?.setScale(0, RoundingMode.HALF_UP)
            ?: zahl("energy_100g")
        val roh = buildMap {
            kj?.let { put(Nutrient.ENERGY_KJ, it) }
            felder.forEach { (nutrient, key) -> zahl(key)?.let { put(nutrient, it) } }
        }
        val werte = NaehrwertScan.plausibel(roh)
        if (Nutrient.ENERGY_KJ !in werte) return null
        fun text(key: String) = produkt[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val basis = if (text("nutrition_data_per") == "100ml") NutrientBasis.PER_100_ML else NutrientBasis.PER_100_G
        return Packung(text("product_name_de") ?: text("product_name"), basis, werte, strichcode, QUELLE)
    }
}

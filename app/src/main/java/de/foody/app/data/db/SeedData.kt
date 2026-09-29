package de.foody.app.data.db

import de.foody.domain.NutrientBasis
import java.math.BigDecimal

/**
 * Kleiner kuratierter Startdatensatz generischer Lebensmittel.
 * Werte sind gerundete Näherungswerte je 100 g/100 ml; Quelle als „Näherung“ gekennzeichnet.
 * Vor einer Store-Veröffentlichung gegen eine lizenzierte Quelle prüfen.
 */
object SeedData {
    private const val SOURCE = "Näherungswert (Startdaten)"

    private data class Row(
        val id: String, val name: String, val category: String,
        val kj: String?, val p: String?, val c: String?, val f: String?, val fib: String?, val sug: String?, val salt: String?,
        val basis: NutrientBasis = NutrientBasis.PER_100_G, val density: String? = null, val piece: String? = null,
    )

    private val rows = listOf(
        Row("seed-reis", "Reis (roh)", "Trockenwaren", "1500", "7", "78", "0.6", "1.3", "0.1", "0"),
        Row("seed-nudeln", "Nudeln (roh)", "Trockenwaren", "1500", "12", "72", "1.5", "3", "3", "0"),
        Row("seed-mehl", "Weizenmehl", "Trockenwaren", "1450", "10", "72", "1", "4", "0.7", "0"),
        Row("seed-zucker", "Zucker", "Trockenwaren", "1700", "0", "100", "0", "0", "100", "0"),
        Row("seed-milch", "Milch 3,5 %", "Kühlregal", "270", "3.4", "4.8", "3.5", "0", "4.8", "0.1", NutrientBasis.PER_100_ML, density = "1.03"),
        Row("seed-butter", "Butter", "Kühlregal", "3050", "0.7", "0.6", "83", "0", "0.6", "0"),
        Row("seed-ei", "Ei", "Kühlregal", "580", "13", "0.7", "10", "0", "0.7", "0.3", piece = "60"),
        Row("seed-zwiebel", "Zwiebel", "Obst & Gemüse", "170", "1.2", "8", "0.1", "1.8", "4", "0", piece = "80"),
        Row("seed-knoblauch", "Knoblauch", "Obst & Gemüse", "600", "6", "28", "0.1", "2", "1", "0", piece = "4"),
        Row("seed-tomate", "Tomate", "Obst & Gemüse", "80", "0.9", "3", "0.2", "1.2", "2.6", "0", piece = "100"),
        Row("seed-dosentomate", "Dosentomaten", "Konserven", "90", "1.2", "3.5", "0.2", "1", "3", "0.1"),
        Row("seed-tomatenmark", "Tomatenmark", "Konserven", "350", "4.5", "14", "0.5", "4", "12", "0.2"),
        Row("seed-kartoffel", "Kartoffel", "Obst & Gemüse", "320", "2", "16", "0.1", "2", "0.8", "0", piece = "150"),
        Row("seed-karotte", "Karotte", "Obst & Gemüse", "150", "0.9", "7", "0.2", "3", "4.7", "0.1", piece = "80"),
        Row("seed-paprika", "Paprika", "Obst & Gemüse", "120", "1", "5", "0.3", "2", "4", "0", piece = "150"),
        Row("seed-haehnchen", "Hähnchenbrust", "Fleisch & Fisch", "450", "23", "0", "1.5", "0", "0", "0.2"),
        Row("seed-hackfleisch", "Rinderhack", "Fleisch & Fisch", "1000", "20", "0", "17", "0", "0", "0.2"),
        Row("seed-olivenoel", "Olivenöl", "Öle & Gewürze", "3700", "0", "0", "100", "0", "0", "0", NutrientBasis.PER_100_ML, density = "0.91"),
        Row("seed-salz", "Salz", "Öle & Gewürze", "0", "0", "0", "0", "0", "0", "100"),
        Row("seed-pfeffer", "Pfeffer", "Öle & Gewürze", null, null, null, null, null, null, null),
        Row("seed-kokosmilch", "Kokosmilch", "Konserven", "800", "2", "3", "19", "0", "2", "0", NutrientBasis.PER_100_ML, density = "1.0"),
        Row("seed-currypaste", "Currypaste", "Öle & Gewürze", null, null, null, null, null, null, null),
        Row("seed-kaese", "Gouda", "Kühlregal", "1500", "25", "0", "29", "0", "0", "2"),
    )

    fun ingredients(now: Long): List<IngredientEntity> = rows.map { r ->
        val hasNutrients = r.kj != null
        IngredientEntity(
            id = r.id,
            canonicalName = r.name,
            category = r.category,
            densityGPerMl = r.density?.let(::BigDecimal),
            pieceWeightG = r.piece?.let(::BigDecimal),
            nutrientBasis = if (hasNutrients) r.basis else null,
            energyKj = r.kj?.let(::BigDecimal),
            protein = r.p?.let(::BigDecimal),
            carbs = r.c?.let(::BigDecimal),
            fat = r.f?.let(::BigDecimal),
            fiber = r.fib?.let(::BigDecimal),
            sugar = r.sug?.let(::BigDecimal),
            salt = r.salt?.let(::BigDecimal),
            nutrientSource = if (hasNutrients) SOURCE else null,
            createdAt = now,
            updatedAt = now,
        )
    }
}

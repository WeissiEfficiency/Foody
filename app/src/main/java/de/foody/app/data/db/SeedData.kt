package de.foody.app.data.db

import de.foody.domain.NutrientBasis
import java.math.BigDecimal

/**
 * Kleiner kuratierter Startdatensatz generischer Lebensmittel.
 * Werte sind gerundete Näherungswerte je 100 g/100 ml; Quelle als „Näherung“ gekennzeichnet.
 * Vor einer Store-Veröffentlichung gegen eine lizenzierte Quelle prüfen.
 */
object SeedData {
    const val SOURCE = "Näherungswert (Startdaten)"

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
        // v3: 3.700 kJ gelten je 100 g (nicht je 100 ml) – Basis korrigiert, die Dichte rechnet ml um
        Row("seed-olivenoel", "Olivenöl", "Öle & Gewürze", "3700", "0", "0", "100", "0", "0", "0", density = "0.91"),
        Row("seed-salz", "Salz", "Öle & Gewürze", "0", "0", "0", "0", "0", "0", "100"),
        // v4: Werte ergänzt (schwarzer Pfeffer); 1 TL gemahlen ≈ 2,3 g
        Row("seed-pfeffer", "Pfeffer", "Öle & Gewürze", "1150", "10", "39", "3.3", "25", "0.6", "0", density = "0.46"),
        Row("seed-kokosmilch", "Kokosmilch", "Konserven", "800", "2", "3", "19", "0", "2", "0", NutrientBasis.PER_100_ML, density = "1.0"),
        Row("seed-currypaste", "Currypaste", "Öle & Gewürze", null, null, null, null, null, null, null),
        Row("seed-kaese", "Gouda", "Kühlregal", "1500", "25", "0", "29", "0", "0", "2"),
        // Version 2: häufige Zutaten aus importierten Rezepten (Zielnamen von IngredientCatalog)
        Row("seed-pflanzenoel", "Pflanzenöl", "Öle & Gewürze", "3700", "0", "0", "100", "0", "0", "0", density = "0.92"),
        Row("seed-wasser", "Wasser", "Getränke", "0", "0", "0", "0", "0", "0", "0", NutrientBasis.PER_100_ML, density = "1.0"),
        Row("seed-gemuesebruehe", "Gemüsebrühe", "Öle & Gewürze", "25", "0.2", "0.6", "0.2", "0", "0.3", "0.9", NutrientBasis.PER_100_ML, density = "1.0"),
        // v4: Stück = Becher (200 g)
        Row("seed-sahne", "Sahne", "Kühlregal", "1210", "2.4", "3.2", "30", "0", "3.2", "0.1", density = "1.0", piece = "200"),
        Row("seed-hack-gemischt", "Hackfleisch, gemischt", "Fleisch & Fisch", "1090", "18", "0", "21", "0", "0", "0.2"),
        Row("seed-eigelb", "Eigelb", "Kühlregal", "1450", "16", "0.3", "32", "0", "0.3", "0.1", piece = "18"),
        // v4: Stück = Bund (≈ 30 g)
        Row("seed-petersilie", "Petersilie", "Obst & Gemüse", "210", "4", "6", "0.4", "4", "1", "0.1", piece = "30"),
        Row("seed-senf", "Senf", "Öle & Gewürze", "370", "6", "5", "4.5", "2", "3", "2.7", density = "1.1"),
        Row("seed-vanillezucker", "Vanillezucker", "Trockenwaren", "1680", "0", "99", "0", "0", "99", "0", piece = "8"),
        Row("seed-paprikapulver", "Paprikapulver", "Öle & Gewürze", "1200", "14", "19", "13", "35", "10", "0.1", density = "0.45"),
        // Version 4: häufigste Zutaten der importierten Rezepte ohne Nährwerte. Stückgewicht = übliche Einheit
        // im Rezept (Kräuter: Zweig, Milchprodukte: Becher, Hefe: Würfel, Lauch: Stange, Mozzarella: Kugel).
        Row("seed-lorbeer", "Lorbeerblatt", "Öle & Gewürze", "1480", "7.6", "49", "8.4", "26", "0", "0", piece = "0.2"),
        Row("seed-backpulver", "Backpulver", "Backzutaten", "400", "0", "23", "0", "0", "0", "25", density = "0.9"),
        Row("seed-rotwein", "Rotwein", "Getränke", "340", "0.1", "2.6", "0", "0", "0.6", "0", NutrientBasis.PER_100_ML, density = "0.99"),
        Row("seed-weisswein", "Weißwein", "Getränke", "300", "0.1", "2.6", "0", "0", "1", "0", NutrientBasis.PER_100_ML, density = "0.99"),
        Row("seed-hefe", "Hefe", "Kühlregal", "365", "12", "4", "1", "7", "0", "0.1", piece = "42"),
        Row("seed-trockenhefe", "Trockenhefe", "Backzutaten", "1415", "40", "14", "7.6", "27", "0", "0.1", density = "0.6"),
        Row("seed-sojasauce", "Sojasauce", "Öle & Gewürze", "240", "8", "6", "0", "0", "1", "14", NutrientBasis.PER_100_ML, density = "1.15"),
        Row("seed-margarine", "Margarine", "Kühlregal", "2970", "0.2", "0.4", "80", "0", "0.4", "0.5"),
        Row("seed-champignons", "Champignons", "Obst & Gemüse", "90", "3.1", "0.6", "0.3", "2", "0.3", "0", piece = "20"),
        Row("seed-chili", "Chilischote", "Obst & Gemüse", "175", "1.9", "7", "0.4", "1.5", "5", "0", piece = "12"),
        Row("seed-schalotte", "Schalotte", "Obst & Gemüse", "310", "2.5", "14", "0.1", "3.2", "8", "0", piece = "25"),
        Row("seed-thymian", "Thymian", "Obst & Gemüse", "440", "5.6", "10", "1.7", "14", "1.7", "0", piece = "1"),
        Row("seed-rosmarin", "Rosmarin", "Obst & Gemüse", "500", "3.3", "6.4", "5.9", "14", "0", "0.1", piece = "1.5"),
        Row("seed-lauch", "Lauch", "Obst & Gemüse", "122", "2.2", "3.3", "0.3", "2.3", "3", "0", piece = "200"),
        Row("seed-sesamoel", "Sesamöl", "Öle & Gewürze", "3700", "0", "0", "100", "0", "0", "0", density = "0.92"),
        Row("seed-sellerie", "Knollensellerie", "Obst & Gemüse", "110", "1.5", "2.3", "0.3", "4.2", "2", "0.2", piece = "600"),
        Row("seed-puderzucker", "Puderzucker", "Backzutaten", "1700", "0", "100", "0", "0", "100", "0", density = "0.56"),
        Row("seed-schmand", "Schmand", "Kühlregal", "995", "2.7", "3.5", "24", "0", "3.5", "0.1", density = "1.0", piece = "200"),
        Row("seed-piment", "Pimentkörner", "Öle & Gewürze", "1080", "6", "28", "8.7", "22", "0", "0.2", piece = "0.1"),
        Row("seed-bruehpulver", "Gemüsebrühepulver", "Öle & Gewürze", "930", "8", "25", "10", "0", "10", "50", density = "0.8"),
        Row("seed-cremefraiche", "Crème fraîche", "Kühlregal", "1195", "2.4", "2.6", "30", "0", "2.6", "0.1", density = "1.0", piece = "200"),
        Row("seed-honig", "Honig", "Backzutaten", "1365", "0.4", "80", "0", "0", "80", "0", density = "1.4"),
        Row("seed-staerke", "Speisestärke", "Backzutaten", "1480", "0.4", "86", "0.1", "1", "0", "0", density = "0.65"),
        Row("seed-ingwer", "Ingwer", "Obst & Gemüse", "345", "1.8", "15.8", "0.8", "2", "1.7", "0", piece = "30"),
        Row("seed-fruehlingszwiebel", "Frühlingszwiebel", "Obst & Gemüse", "139", "1.8", "4.7", "0.2", "2.6", "2.3", "0", piece = "15"),
        Row("seed-chilipulver", "Chilipulver", "Öle & Gewürze", "1270", "13", "15", "14", "35", "7", "2.5", density = "0.5"),
        Row("seed-quark", "Quark", "Kühlregal", "285", "12", "4", "0.3", "0", "4", "0.1", density = "1.05", piece = "250"),
        Row("seed-parmesan", "Parmesan", "Kühlregal", "1620", "32", "0", "29", "0", "0", "1.6"),
        Row("seed-puddingpulver", "Vanillepuddingpulver", "Backzutaten", "1450", "0.3", "85", "0.1", "0", "0", "0.4"),
        Row("seed-joghurt", "Joghurt", "Kühlregal", "270", "3.8", "4.4", "3.5", "0", "4.4", "0.1", density = "1.03", piece = "150"),
        Row("seed-currypulver", "Currypulver", "Öle & Gewürze", "1445", "14", "25", "14", "33", "3", "0.1", density = "0.5"),
        Row("seed-mozzarella", "Mozzarella", "Kühlregal", "1060", "18", "1", "20", "0", "1", "0.5", piece = "125"),
        Row("seed-butterschmalz", "Butterschmalz", "Öle & Gewürze", "3680", "0.3", "0", "99.5", "0", "0", "0", density = "0.91"),
        Row("seed-rindfleisch", "Rindfleisch", "Fleisch & Fisch", "580", "21", "0", "6", "0", "0", "0.1"),
        Row("seed-schweinebraten", "Schweinebraten", "Fleisch & Fisch", "860", "18", "0", "15", "0", "0", "0.2"),
        Row("seed-schweinefilet", "Schweinefilet", "Fleisch & Fisch", "450", "22", "0", "2", "0", "0", "0.1"),
        Row("seed-kochschinken", "Kochschinken", "Fleisch & Fisch", "490", "19", "1", "4", "0", "1", "2.3"),
        Row("seed-eiweiss", "Eiweiß", "Kühlregal", "205", "11", "0.7", "0.2", "0", "0.7", "0.4", piece = "33"),
        Row("seed-mascarpone", "Mascarpone", "Kühlregal", "1765", "4.6", "3.6", "44", "0", "3.6", "0.1", piece = "250"),
        Row("seed-schnittlauch", "Schnittlauch", "Obst & Gemüse", "160", "3.6", "1.6", "0.7", "6", "1.6", "0", piece = "25"),
        Row("seed-pinienkerne", "Pinienkerne", "Trockenwaren", "2850", "14", "4", "68", "4", "3.6", "0"),
        Row("seed-balsamico", "Balsamico", "Öle & Gewürze", "290", "0.5", "17", "0", "0", "15", "0", NutrientBasis.PER_100_ML, density = "1.06"),
        Row("seed-semmelbroesel", "Semmelbrösel", "Trockenwaren", "1535", "12", "72", "2", "4", "4", "1.2", density = "0.45"),
        Row("seed-broetchen", "Brötchen", "Brot & Backwaren", "1150", "9", "53", "2", "3", "2", "1.2", piece = "60"),
        Row("seed-brokkoli", "Brokkoli", "Obst & Gemüse", "136", "3", "2.7", "0.4", "3", "1.7", "0", piece = "400"),
        Row("seed-hokkaido", "Hokkaidokürbis", "Obst & Gemüse", "200", "1.7", "8.8", "0.5", "2.3", "4", "0", piece = "1200"),
    )

    /** Bei Erweiterung erhöhen: neue Zeilen werden dann auch in bestehende Installationen übernommen. */
    const val VERSION = 4

    /** Kanonische Namen aller Startzutaten (für Tests und Abgleich). */
    val names: List<String> get() = rows.map { it.name }

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

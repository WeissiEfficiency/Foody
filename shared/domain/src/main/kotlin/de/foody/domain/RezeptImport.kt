package de.foody.domain

import java.math.BigDecimal

/**
 * Rechnung beim Import eines Markdown-Rezepts – gemeinsam für App und Server, damit beide dasselbe Rezept ergeben.
 * Das Auflösen der Zutatennamen (Datenbank der App bzw. Sync-Datensätze des Servers) macht der Aufrufer.
 */
object RezeptImport {
    private const val FLEISCH = "Fleisch & Fisch"

    data class Zeile(val zutatId: String, val menge: BigDecimal, val einheit: MeasureUnit, val hinweis: String?, val optional: Boolean)

    data class Ergebnis(val portionen: Int, val geschaetzt: Boolean, val zeilen: List<Zeile>)

    /** [zutaten]`[i]` ist die aufgelöste Zutat zu `rezept.ingredients[i]`. */
    fun vorbereiten(rezept: ImportedRecipe, zutaten: List<Ingredient>, vorgabePortionen: Int): Ergebnis {
        require(zutaten.size == rezept.ingredients.size) { "Zu jeder Zeile gehört genau eine Zutat" }
        val paare = rezept.ingredients.zip(zutaten)
        // Gewicht der festen Pflichtzutaten (ohne Flüssigkeiten) für die Portionen-Schätzung
        val festeGramm = paare.filter { (ing, _) -> !ing.optional && (ing.amount?.signum() ?: 0) > 0 }.sumOf { (ing, z) ->
            val q = Quantity.of(ing.amount!!, ing.unit ?: MeasureUnit.PIECE)
            if (q.dimension == Dimension.VOLUME) 0.0
            else UnitConverter.convert(q, Dimension.MASS, z.conversion)?.baseAmount?.toDouble() ?: 0.0
        }
        val kcal = NutritionCalculator.calculate(
            Recipe("import", rezept.name, 1, paare.mapIndexed { i, (ing, z) ->
                RecipeIngredient("l$i", z.id, ing.amount ?: BigDecimal.ZERO, ing.unit ?: MeasureUnit.PIECE, ing.optional)
            }),
            paare.associate { (_, z) -> z.id to z },
        ).totals[Nutrient.ENERGY_KJ]?.let(NutritionResult::kjToKcal)?.toDouble() ?: 0.0
        // Fleisch oder Fisch in Stück (größte Anzahl), z. B. „4 Rindersteaks“ → 4 Portionen
        val fleischStuecke = paare.filter { (ing, z) ->
            z.category == FLEISCH && (ing.unit ?: MeasureUnit.PIECE) == MeasureUnit.PIECE && ing.amount != null
        }.maxOfOrNull { (ing, _) -> ing.amount!!.toInt() }
        val schaetzung = ServingsEstimator.estimate(rezept.name, festeGramm, kcal, fleischStuecke)
        val zeilen = paare.map { (ing, z) ->
            Zeile(
                zutatId = z.id,
                // Ohne Zahl → 0 = „nach Bedarf“
                menge = ing.amount ?: BigDecimal.ZERO,
                einheit = ing.unit ?: MeasureUnit.PIECE,
                hinweis = listOfNotNull(ing.group, ing.note).joinToString(" · ").ifBlank { null },
                optional = ing.optional,
            )
        }
        return Ergebnis(schaetzung ?: vorgabePortionen, schaetzung != null, zeilen)
    }

    /** Notiz am importierten Rezept (Texte wie in den String-Ressourcen der App). */
    fun hinweis(quelle: String?, portionen: Int, geschaetzt: Boolean): String = listOfNotNull(
        quelle?.let { "Quelle: $it" },
        if (geschaetzt) "Portionenzahl war in der Datei nicht angegeben – aus den Mengen auf $portionen geschätzt, bitte prüfen."
        else "Portionenzahl war in der Datei nicht angegeben – auf $portionen gesetzt, bitte prüfen.",
    ).joinToString("\n")
}

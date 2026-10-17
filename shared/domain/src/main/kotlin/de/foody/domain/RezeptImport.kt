package de.foody.domain

import java.math.BigDecimal

/**
 * Rechnung beim Import eines Markdown-Rezepts – gemeinsam für App und Server, damit beide dasselbe Rezept ergeben.
 * Das Auflösen der Zutatennamen (Datenbank der App bzw. Sync-Datensätze des Servers) macht der Aufrufer.
 */
object RezeptImport {
    private const val FLEISCH = "Fleisch & Fisch"

    data class Zeile(val zutatId: String, val menge: BigDecimal, val einheit: MeasureUnit, val hinweis: String?, val optional: Boolean)

    /** [ausDatei]: Portionen stammen aus der Datei; [geschaetzt]: aus den Mengen geschätzt; beides false = Vorgabe. */
    data class Ergebnis(val portionen: Int, val geschaetzt: Boolean, val zeilen: List<Zeile>, val ausDatei: Boolean = false)

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
        // Die Portionenzahl der Datei gilt vor jeder Schätzung – die Schätzung lag oft um das Zwei- bis Fünffache
        // daneben, und mit ihr jede kcal-Angabe je Portion. Ausnahme: „1 Portion“ bei Backwaren meint den ganzen
        // Kuchen oder das ganze Blech; gegessen und eingetragen wird ein Stück, also schätzen.
        val ausDatei = rezept.servings?.takeIf { it > 1 || ServingsEstimator.kindOf(rezept.name) != ServingsEstimator.Kind.BAKED }
        val schaetzung = if (ausDatei != null) null else ServingsEstimator.estimate(rezept.name, festeGramm, kcal, fleischStuecke)
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
        // Backware, aber nicht schätzbar: dann doch die Angabe der Datei statt der Vorgabe
        val portionen = ausDatei ?: schaetzung ?: rezept.servings
        return Ergebnis(portionen ?: vorgabePortionen, schaetzung != null, zeilen, ausDatei = schaetzung == null && portionen != null)
    }

    /** Notiz am importierten Rezept (Texte wie in den String-Ressourcen der App). */
    fun hinweis(quelle: String?, portionen: Int, geschaetzt: Boolean, ausDatei: Boolean = false): String = listOfNotNull(
        quelle?.let { "Quelle: $it" },
        if (ausDatei) null
        else if (geschaetzt) schaetzHinweis(portionen)
        else "Portionenzahl war in der Datei nicht angegeben – auf $portionen gesetzt, bitte prüfen.",
    ).joinToString("\n")

    private fun schaetzHinweis(portionen: Int) =
        "Portionenzahl war in der Datei nicht angegeben – aus den Mengen auf $portionen geschätzt, bitte prüfen."

    data class Nachtrag(val portionen: Int, val notiz: String?)

    /**
     * Für Rezepte, die vor Regel 27 importiert wurden: Portionen aus der Datei statt der alten Schätzung. Nur wenn die
     * Notiz den Schätzhinweis noch genau für [aktuellePortionen] enthält – hat der Nutzer die Portionen geändert oder den
     * Hinweis gelöscht, bleibt alles, wie es ist (null). Die Mengen der Zeilen ändern sich nicht, sie gehören zur Datei.
     * Gleiche Regel wie [vorbereiten]: „Portionen: 1“ bei Backwaren wird weiter geschätzt, also nichts nachzutragen.
     */
    fun portionenNachtragen(alteNotiz: String?, aktuellePortionen: Int, datei: ImportedRecipe): Nachtrag? {
        val hinweis = schaetzHinweis(aktuellePortionen)
        val zeilen = alteNotiz?.lines() ?: return null
        if (zeilen.none { it.trim() == hinweis }) return null
        val portionen = datei.servings?.takeIf { it > 1 || ServingsEstimator.kindOf(datei.name) != ServingsEstimator.Kind.BAKED }
            ?: return null
        val notiz = zeilen.filterNot { it.trim() == hinweis }.joinToString("\n").trim().ifEmpty { null }
        return Nachtrag(portionen, notiz)
    }
}

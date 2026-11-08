package de.foody.domain

import java.math.BigDecimal

/**
 * Rechnung beim Import eines Markdown-Rezepts – gemeinsam für App und Server, damit beide dasselbe Rezept ergeben.
 * Das Auflösen der Zutatennamen (Datenbank der App bzw. Sync-Datensätze des Servers) macht der Aufrufer.
 */
object RezeptImport {
    private const val FLEISCH = "Fleisch & Fisch"

    data class Zeile(val zutatId: String, val menge: BigDecimal, val einheit: MeasureUnit, val hinweis: String?, val optional: Boolean)

    /**
     * [ausDatei]: Portionen stammen aus der Datei; [geschaetzt]: aus den Mengen geschätzt; [ausText]: Stückzahl aus dem
     * Rezepttext („ergibt 24 Stück“); alle false = Vorgabe.
     */
    data class Ergebnis(
        val portionen: Int, val geschaetzt: Boolean, val zeilen: List<Zeile>, val ausDatei: Boolean = false, val ausText: Boolean = false,
    )

    /** Woher die Portionenzahl kommt. */
    private enum class Herkunft { DATEI, TEXT, GESCHAETZT, VORGABE }

    /**
     * Ab dieser Energie meint „Portionen: 1“ das ganze Gericht und wird geschätzt: So viel isst niemand auf einmal
     * (Prüfung der Chefkoch-Sammlung 2026-11-08: Schaschlik-Topf, Nussecken, Mini-Quiches mit „1 Portion“).
     */
    private const val GRENZE_EINE_PORTION_KCAL = 1500.0

    /** Gewicht der festen Pflichtzutaten, Energie und größte Stückzahl Fleisch/Fisch – Grundlage der Schätzung. */
    private data class Kennzahlen(val festeGramm: Double, val kcal: Double, val fleischStuecke: Int?)

    private fun kennzahlen(rezept: Recipe, katalog: Map<String, Ingredient>): Kennzahlen {
        val zeilen = rezept.ingredients.filter { !it.optional && it.amount.signum() > 0 }
        // Gewicht ohne Flüssigkeiten: Brühe oder Bier verkochen zur Soße
        val festeGramm = zeilen.sumOf { z ->
            val q = Quantity.of(z.amount, z.unit)
            val umrechnung = katalog[z.ingredientId]?.conversion ?: ConversionInfo()
            if (q.dimension == Dimension.VOLUME) 0.0 else UnitConverter.convert(q, Dimension.MASS, umrechnung)?.baseAmount?.toDouble() ?: 0.0
        }
        val kcal = NutritionCalculator.calculate(rezept.copy(defaultServings = 1), katalog)
            .totals[Nutrient.ENERGY_KJ]?.let(NutritionResult::kjToKcal)?.toDouble() ?: 0.0
        // Fleisch oder Fisch in Stück (größte Anzahl), z. B. „4 Rindersteaks“ → 4 Portionen
        val fleischStuecke = zeilen.filter { z -> katalog[z.ingredientId]?.category == FLEISCH && z.unit == MeasureUnit.PIECE }
            .maxOfOrNull { it.amount.toInt() }
        return Kennzahlen(festeGramm, kcal, fleischStuecke)
    }

    /** Ob „Portionen: [servings]“ aus der Datei gilt: mehr als eine immer, eine nur für ein Gericht in Portionsgröße. */
    private fun dateiGilt(name: String, servings: Int, kcal: Double?): Boolean =
        servings > 1 || (ServingsEstimator.kindOf(name) != ServingsEstimator.Kind.BAKED && (kcal ?: 0.0) <= GRENZE_EINE_PORTION_KCAL)

    /**
     * Die Portionenzahl der Datei gilt vor jeder Schätzung – die Schätzung lag oft um das Zwei- bis Fünffache daneben,
     * und mit ihr jede kcal-Angabe je Portion. Nennt die Datei keine oder nur eine Portion, zählt zuerst eine Stückzahl
     * im Text („ergibt 24 Stück“). „1 Portion“ bei Backwaren meint den ganzen Kuchen oder das ganze Blech, über
     * [GRENZE_EINE_PORTION_KCAL] das ganze Gericht: dann schätzen. Nicht schätzbar: doch die Angabe der Datei.
     */
    private fun portionenWaehlen(datei: ImportedRecipe, k: Kennzahlen): Pair<Int?, Herkunft> {
        val servings = datei.servings
        if (servings != null && dateiGilt(datei.name, servings, k.kcal)) return servings to Herkunft.DATEI
        val backware = ServingsEstimator.kindOf(datei.name) == ServingsEstimator.Kind.BAKED
        stueckAusText(datei.steps, backware)?.let { return it to Herkunft.TEXT }
        ServingsEstimator.estimate(datei.name, k.festeGramm, k.kcal, k.fleischStuecke)?.let { return it to Herkunft.GESCHAETZT }
        return servings to (if (servings != null) Herkunft.DATEI else Herkunft.VORGABE)
    }

    private const val STUECK =
        "(\\d{1,3})(?:\\s*(?:[-–]|bis)\\s*\\d{1,3})?\\s+(?:gleich\\s+große\\s+|gleichgroße\\s+|kleine\\s+)?(?:Stück|Stücke|Stücken)\\b"
    private val ergibt = Regex("\\b(?:ergibt|ergeben|für|reicht\\s+für)\\s+(?:ca\\.\\s*|etwa\\s+|rund\\s+)?$STUECK", RegexOption.IGNORE_CASE)
    private val abstechen = Regex("\\b$STUECK\\s+(?:abstechen|ausstechen|formen|portionieren|abwiegen|abteilen)", RegexOption.IGNORE_CASE)
    private val schneiden = Regex("\\bin\\s+$STUECK\\s+(?:schneiden|teilen|aufteilen)", RegexOption.IGNORE_CASE)

    /**
     * Stückzahl aus den Arbeitsschritten: „Ergibt 12 - 14 Stück“ (die untere Zahl), „24 Stücke abstechen“, bei
     * [backware] auch „in 12 Stücke schneiden“ (sonst wird oft Fleisch oder Gemüse zerteilt). Nur 2 bis 48.
     */
    fun stueckAusText(schritte: List<String>, backware: Boolean): Int? {
        val muster = if (backware) listOf(ergibt, abstechen, schneiden) else listOf(ergibt, abstechen)
        return schritte.firstNotNullOfOrNull { schritt ->
            muster.firstNotNullOfOrNull { it.find(schritt)?.groupValues?.get(1)?.toIntOrNull() }?.takeIf { it in 2..48 }
        }
    }

    /** [zutaten]`[i]` ist die aufgelöste Zutat zu `rezept.ingredients[i]`. */
    fun vorbereiten(datei: ImportedRecipe, zutaten: List<Ingredient>, vorgabePortionen: Int): Ergebnis {
        require(zutaten.size == datei.ingredients.size) { "Zu jeder Zeile gehört genau eine Zutat" }
        val rezept = datei.copy(ingredients = datei.ingredients.zip(zutaten) { ing, z -> gewichtAusHinweis(ing, z) })
        val paare = rezept.ingredients.zip(zutaten)
        val alsRezept = Recipe("import", rezept.name, 1, paare.mapIndexed { i, (ing, z) ->
            RecipeIngredient("l$i", z.id, ing.amount ?: BigDecimal.ZERO, ing.unit ?: MeasureUnit.PIECE, ing.optional)
        })
        val (portionen, herkunft) = portionenWaehlen(rezept, kennzahlen(alsRezept, paare.associate { (_, z) -> z.id to z }))
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
        return Ergebnis(
            portionen ?: vorgabePortionen, herkunft == Herkunft.GESCHAETZT, zeilen,
            ausDatei = herkunft == Herkunft.DATEI, ausText = herkunft == Herkunft.TEXT,
        )
    }

    private val gewichtImHinweis = Regex(
        """(?:\b(à|je|pro)\s+)?(?:ca\.\s*)?(${GermanAmounts.NUMBER})(?:\s*(?:[-–]|bis)\s*(?:${GermanAmounts.NUMBER}))?\s*(kg|g|ml|l)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Mengenwörter, deren Gewichtsangabe je Stück gilt („2 Gläser, à 200 g“ wie „1 Glas, ca. 200 ml“). */
    private val behaelter = setOf(
        "glas", "gläser", "becher", "tube", "tube/n", "flasche", "flasche/n", "dose", "dose/n", "packung", "beutel", "tüte",
        "päckchen", "schale", "schälchen", "netz", "rolle", "kugel", "bund", "stange",
    )

    /**
     * „1 Pck. Eierspätzle, 400 g“, „1 Becher Schmelzkäse, ca. 200 g“: Lässt sich eine Stück-, Packungs- oder Dosenangabe
     * mit den Daten der Zutat nicht in Gramm umrechnen, gilt das Gewicht (oder Volumen) aus dem Hinweis – je Behälter
     * oder bei „à“/„je“, sonst für die ganze Zeile. Bei einer Spanne die untere Grenze, bei mehreren Angaben nichts.
     * Die ursprüngliche Angabe bleibt vorne im Hinweis.
     */
    internal fun gewichtAusHinweis(ing: ImportedIngredient, zutat: Ingredient): ImportedIngredient {
        val menge = ing.amount?.takeIf { it.signum() > 0 } ?: return ing
        val einheit = ing.unit ?: MeasureUnit.PIECE
        val hinweis = ing.note ?: return ing
        if (!einheit.dimension.countable) return ing
        if (UnitConverter.convert(Quantity.of(menge, einheit), Dimension.MASS, zutat.conversion) != null) return ing
        val treffer = gewichtImHinweis.findAll(hinweis).toList()
        if (treffer.map { it.groupValues[2] + it.groupValues[3].lowercase() }.distinct().size != 1) return ing
        val t = treffer.first()
        val gewicht = GermanAmounts.parseNumber(t.groupValues[2]) ?: return ing
        val gewichtEinheit = GermanAmounts.unitOf(t.groupValues[3]) ?: return ing
        // „Glas, ca. 200 ml“, „kl. Dose/n, 480 g“: der erste Teil des Hinweises nennt den Behälter
        val imBehaelter = hinweis.substringBefore(',').lowercase().split(' ').any { it in behaelter }
        val jeStueck = t.groupValues[1].isNotEmpty() || einheit != MeasureUnit.PIECE || imBehaelter
        val zahl = menge.stripTrailingZeros().toPlainString().replace('.', ',')
        val vorne = when {
            einheit != MeasureUnit.PIECE -> "$zahl ${einheit.symbol}, "
            imBehaelter -> "$zahl "
            else -> "$zahl Stk., "
        }
        return ing.copy(amount = if (jeStueck) menge * gewicht else gewicht, unit = gewichtEinheit, note = vorne + hinweis)
    }

    /** Notiz am importierten Rezept (Texte wie in den String-Ressourcen der App). */
    fun hinweis(quelle: String?, portionen: Int, geschaetzt: Boolean, ausDatei: Boolean = false, ausText: Boolean = false): String =
        listOfNotNull(
            quelle?.let { "Quelle: $it" },
            if (ausDatei) {
                null
            } else if (ausText) {
                textHinweis(portionen)
            } else if (geschaetzt) {
                schaetzHinweis(portionen)
            } else {
                "Portionenzahl war in der Datei nicht angegeben – auf $portionen gesetzt, bitte prüfen."
            },
        ).joinToString("\n")

    private fun schaetzHinweis(portionen: Int) =
        "Portionenzahl war in der Datei nicht angegeben – aus den Mengen auf $portionen geschätzt, bitte prüfen."

    private fun textHinweis(portionen: Int) = "Portionenzahl aus dem Rezepttext: ergibt $portionen Stück, bitte prüfen."

    data class Nachtrag(val portionen: Int, val notiz: String?)

    /**
     * Für Rezepte, die vor Regel 27 importiert wurden: Portionen aus der Datei statt der alten Schätzung. Nur wenn die
     * Notiz den Schätzhinweis noch genau für [aktuellePortionen] enthält – hat der Nutzer die Portionen geändert oder den
     * Hinweis gelöscht, bleibt alles, wie es ist (null). Die Mengen der Zeilen ändern sich nicht, sie gehören zur Datei.
     * Gleiche Regel wie [vorbereiten]: „Portionen: 1“ bei Backwaren oder über [GRENZE_EINE_PORTION_KCAL] ([kcalGesamt],
     * ohne Angabe nicht geprüft) wird weiter geschätzt, also nichts nachzutragen.
     */
    fun portionenNachtragen(alteNotiz: String?, aktuellePortionen: Int, datei: ImportedRecipe, kcalGesamt: Double? = null): Nachtrag? {
        val hinweis = schaetzHinweis(aktuellePortionen)
        val zeilen = alteNotiz?.lines() ?: return null
        if (zeilen.none { it.trim() == hinweis }) return null
        val portionen = datei.servings?.takeIf { dateiGilt(datei.name, it, kcalGesamt) } ?: return null
        val notiz = zeilen.filterNot { it.trim() == hinweis }.joinToString("\n").trim().ifEmpty { null }
        return Nachtrag(portionen, notiz)
    }

    /**
     * Für Rezepte, die mit „Portionen: 1“ aus der Datei importiert wurden, bevor Stückzahl im Text und
     * [GRENZE_EINE_PORTION_KCAL] galten: Portionen wie beim heutigen Import ([vorbereiten]), gerechnet mit den Zeilen von
     * [rezept] und dem [katalog]. Nur solange das Rezept noch unverändert eine Portion hat, die Datei eine nennt und die
     * Notiz noch keinen Hinweis zu den Portionen trägt; sonst und wenn es bei einer bleibt: null. Die Mengen bleiben.
     */
    fun einePortionNachtragen(
        alteNotiz: String?, aktuellePortionen: Int, datei: ImportedRecipe, rezept: Recipe, katalog: Map<String, Ingredient>,
    ): Nachtrag? {
        if (aktuellePortionen != 1 || datei.servings != 1) return null
        if (alteNotiz?.lines()?.any { it.trim().startsWith("Portionenzahl") } == true) return null
        val (portionen, herkunft) = portionenWaehlen(datei, kennzahlen(rezept, katalog))
        if (portionen == null || portionen == 1) return null
        val neu = if (herkunft == Herkunft.TEXT) textHinweis(portionen) else schaetzHinweis(portionen)
        return Nachtrag(portionen, listOfNotNull(alteNotiz?.trim()?.ifEmpty { null }, neu).joinToString("\n"))
    }
}

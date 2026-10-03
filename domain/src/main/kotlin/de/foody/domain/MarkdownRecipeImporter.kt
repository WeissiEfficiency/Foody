package de.foody.domain

import java.math.BigDecimal

/**
 * Ergebnis eines Imports. Mengen ohne Zahl (z. B. „Salz und Pfeffer“) haben [amount] = null.
 */
data class ImportedRecipe(
    val name: String,
    val sourceUrl: String?,
    val ingredients: List<ImportedIngredient>,
    val steps: List<String>,
)

data class ImportedIngredient(
    val name: String,
    val amount: BigDecimal?,
    val unit: MeasureUnit?,
    /** Zusatzangaben aus Menge und Name, z. B. „große“, „Zehe/n“, „geschälte à ca. 400 g“. */
    val note: String?,
    /** Zwischenüberschrift, z. B. „Für den Teig“. */
    val group: String?,
    /** „optional“ im Namen oder „evtl.“ als Mengenangabe. */
    val optional: Boolean = false,
)

/**
 * Liest Rezepte im Markdown-Format, wie es Web-Clipper aus Rezeptseiten erzeugen:
 * `# Titel`, `_Quelle: url_`, `## Zutaten` mit abwechselnden Mengen-/Namenszeilen
 * und optionalen `###`-Gruppen, `## Zubereitung` mit Absätzen.
 * Portionen sind in diesem Format nicht enthalten.
 */
object MarkdownRecipeImporter {

    private val numberRegex = Regex("""^(${GermanAmounts.NUMBER})\s*(.*)$""")
    private val vagueAmounts = setOf("n. b.", "n.b.", "evtl.", "etwas", "nach belieben", "etwas mehr", "prise", "1 prise")

    fun parse(markdown: String): ImportedRecipe {
        val lines = markdown.lines().map { it.trim() }
        val title = lines.firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.let(::cleanTitle).orEmpty()
        val source = lines.firstOrNull { it.startsWith("_Quelle:") }
            ?.removePrefix("_Quelle:")?.trim()?.trimEnd('_')?.trim()

        val ingredientLines = section(lines, "## Zutaten")
        val stepLines = section(lines, "## Zubereitung")

        return ImportedRecipe(
            name = title,
            sourceUrl = source,
            ingredients = parseIngredients(ingredientLines),
            steps = stepLines.filter { it.isNotBlank() && !it.startsWith("- ") && !it.startsWith("#") },
        )
    }

    /** Zeilen nach [header] bis zur nächsten `## `-Überschrift. */
    private fun section(lines: List<String>, header: String): List<String> {
        val start = lines.indexOfFirst { it.equals(header, ignoreCase = true) }
        if (start < 0) return emptyList()
        val rest = lines.drop(start + 1)
        val end = rest.indexOfFirst { it.startsWith("## ") }.let { if (it < 0) rest.size else it }
        return rest.take(end)
    }

    private fun cleanTitle(t: String): String =
        // „Titel von Autor“ → „Titel“ (Autor steht am Ende, genau ein Wort)
        t.replace(Regex("""\s+von\s+\S+$"""), "").trim()

    /**
     * Mengenzeile: Zahl, bekannte vage Menge – oder ein kleingeschriebenes Einzelwort („viel“, „reichlich“),
     * auf das eine großgeschriebene Zeile folgt. Zutatennamen sind Substantive und beginnen groß.
     */
    private fun isAmountLine(line: String, next: String?): Boolean =
        numberRegex.matches(line) || line.lowercase() in vagueAmounts ||
            (line.first().isLowerCase() && ' ' !in line && next?.firstOrNull()?.isUpperCase() == true)

    private fun parseIngredients(lines: List<String>): List<ImportedIngredient> {
        val out = mutableListOf<ImportedIngredient>()
        var group: String? = null
        var pendingAmount: String? = null
        val content = lines.filter { it.isNotBlank() }
        for ((i, line) in content.withIndex()) {
            if (line.startsWith("###")) {
                group = line.trimStart('#').trim().trimEnd(':').trim()
                pendingAmount = null
                continue
            }
            if (pendingAmount == null && isAmountLine(line, content.getOrNull(i + 1))) {
                pendingAmount = line
                continue
            }
            out += buildIngredient(pendingAmount, line, group)
            pendingAmount = null
        }
        return out
    }

    private fun buildIngredient(amountText: String?, nameText: String, group: String?): ImportedIngredient {
        var amount: BigDecimal? = null
        var unit: MeasureUnit? = null
        val notes = mutableListOf<String>()

        if (amountText != null) {
            val m = numberRegex.matchEntire(amountText)
            if (m != null) {
                amount = GermanAmounts.parseNumber(m.groupValues[1])
                val rest = m.groupValues[2].trim()
                val firstToken = rest.substringBefore(' ').substringBefore(',').lowercase()
                val mapped = GermanAmounts.unitOf(firstToken)
                if (mapped != null) {
                    unit = mapped
                    rest.removePrefix(rest.substringBefore(' ').substringBefore(',')).trim(',', ' ')
                        .takeIf { it.isNotEmpty() }?.let(notes::add)
                } else {
                    // „große“, „Zehe/n“, „Bund“, „Glas“ … → Stück, Originaltext als Hinweis
                    unit = MeasureUnit.PIECE
                    if (rest.isNotEmpty()) notes += rest
                }
            } else {
                notes += amountText
            }
        }

        val (name, nameNotes) = splitName(nameText)
        notes += nameNotes
        val note = notes.joinToString(", ").ifBlank { null }
        // „optional“ im Namen oder „evtl.“ als Mengenangabe; „evtl.“ in Freitext-Hinweisen zählt nicht.
        val optional = Regex("""\boptional\b""", RegexOption.IGNORE_CASE).containsMatchIn(nameText) ||
            amountText?.trim()?.lowercase() == "evtl."
        return ImportedIngredient(name, amount, unit, note, group, optional)
    }

    /**
     * „Tomaten, geschälte à ca. 400 g“ → „Tomaten“ + [„geschälte à ca. 400 g“];
     * „Paprikaschote(n) rote“ → „Paprikaschote“ + [„rote“]; „Salz und Pfeffer“ bleibt zusammen.
     * Der Name besteht aus den führenden großgeschriebenen Wörtern (Substantive).
     */
    private fun splitName(text: String): Pair<String, List<String>> {
        val base = text.substringBefore(',')
            .replace(Regex("""\((n|e|er|en|s|nen)\)"""), "")
            .replace(Regex("""/(n|e|en)\b"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val afterComma = text.substringAfter(',', "").trim()
        val tokens = base.split(' ').filter { it.isNotEmpty() }
        var end = if (tokens.isEmpty()) 0 else 1
        while (end < tokens.size) {
            val t = tokens[end]
            end += when {
                t.first().isUpperCase() -> 1
                t == "und" && end + 1 < tokens.size && tokens[end + 1].first().isUpperCase() -> 2
                else -> break
            }
        }
        val name = tokens.take(end).joinToString(" ").ifEmpty { base }
        val notes = listOfNotNull(
            tokens.drop(end).joinToString(" ").ifBlank { null },
            afterComma.ifBlank { null },
        )
        return name to notes
    }
}

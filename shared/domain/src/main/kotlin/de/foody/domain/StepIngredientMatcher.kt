package de.foody.domain

import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/** Eine im Arbeitsschritt erwähnte Zutat; [amount]/[unit] nur, wenn die Menge direkt davor steht („0,25 l Bier“). */
data class StepMention(val ingredientId: String, val amount: BigDecimal?, val unit: MeasureUnit?)

/**
 * Findet die Zutaten, die in einem Arbeitsschritt erwähnt werden – für die Mengen-Erinnerung im Kochmodus.
 * Vergleich ohne Groß-/Kleinschreibung über den Wortanfang, damit „Kartoffel“ auch „Kartoffeln“ trifft;
 * zusätzlich über die Synonyme aus [IngredientCatalog] („Weizenmehl“ ↔ „Mehl“).
 * Zusammengesetzte Namen („Salz und Pfeffer“) treffen, sobald ein Teil erwähnt wird.
 */
object StepIngredientMatcher {
    /** Kürzere Begriffe („Öl“, „Ei“) treffen nur als ganzes Wort, sonst fände „Öl“ auch „Kölsch“. */
    private const val PREFIX_MIN_LENGTH = 3

    private val unitAlternation = GermanAmounts.unitWords.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    /**
     * Menge direkt vor der Zutat: Zahl, optional Einheit, dann höchstens zwei kleingeschriebene Wörter
     * (Adjektive wie „geriebenen“). Großgeschriebene Wörter dazwischen gehören zu einer anderen Zutat.
     */
    private val amountBefore = Regex(
        """(?<![\p{L}\d])(${GermanAmounts.NUMBER})\s*(?:(?i:($unitAlternation))(?!\p{L}))?\s+(?:\p{Ll}[\p{L}-]*\s+){0,2}$""",
    )

    /** Liefert die IDs aus [names] (ID → Name) in Reihenfolge der Map, die in [step] vorkommen. */
    fun match(step: String, names: Map<String, String>): List<String> = mentions(step, names).map { it.ingredientId }

    fun mentions(step: String, names: Map<String, String>): List<StepMention> {
        val text = step.lowercase()
        return names.mapNotNull { (id, name) ->
            val hit = termRegexes(name).mapNotNull { it.find(text) }.minByOrNull { it.range.first } ?: return@mapNotNull null
            // Nur innerhalb des Satzteils vor der Zutat suchen; Satzzeichen nur mit folgendem Leerraum,
            // sonst würde das Dezimalkomma in „0,25 l“ den Satz teilen
            val clause = step.substring(0, hit.range.first).split(Regex("""[,.;:!?]\s""")).last()
            val m = amountBefore.find(clause)
            StepMention(id, m?.groupValues?.get(1)?.let(GermanAmounts::parseNumber), m?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.let(GermanAmounts::unitOf))
        }
    }

    /** Suchmuster je Zutatenname; die Detailseite fragt dieselben Namen für jeden Schritt und jedes Rendern ab. */
    private val regexCache = ConcurrentHashMap<String, List<Regex>>()

    private fun termRegexes(name: String): List<Regex> = regexCache.getOrPut(name) { terms(name).map(::termRegex) }

    private fun terms(name: String): List<String> =
        (name.lowercase().split(" und ") + IngredientCatalog.synonymsOf(name)).map { it.trim() }.filter { it.length >= 2 }.distinct()

    private fun termRegex(term: String): Regex {
        val escaped = Regex.escape(term)
        return if (term.length < PREFIX_MIN_LENGTH) Regex("""(?<!\p{L})$escaped(?!\p{L})""") else Regex("""(?<!\p{L})$escaped""")
    }
}

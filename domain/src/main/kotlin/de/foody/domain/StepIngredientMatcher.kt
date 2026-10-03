package de.foody.domain

/**
 * Findet die Zutaten, die in einem Arbeitsschritt erwähnt werden – für die Mengen-Erinnerung im Kochmodus.
 * Vergleich ohne Groß-/Kleinschreibung über den Wortanfang, damit „Kartoffel“ auch „Kartoffeln“ trifft.
 * Zusammengesetzte Namen („Salz und Pfeffer“) treffen, sobald ein Teil erwähnt wird.
 */
object StepIngredientMatcher {
    private const val MIN_LENGTH = 3

    /** Liefert die IDs aus [names] (ID → Name) in Reihenfolge der Map, die in [step] vorkommen. */
    fun match(step: String, names: Map<String, String>): List<String> {
        val text = step.lowercase()
        return names.filter { (_, name) ->
            name.lowercase().split(" und ").map { it.trim() }.filter { it.length >= MIN_LENGTH }
                .any { part -> Regex("""(?<![\p{L}])${Regex.escape(part)}""").containsMatchIn(text) }
        }.keys.toList()
    }
}

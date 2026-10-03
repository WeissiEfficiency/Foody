package de.foody.domain

import java.time.LocalDate
import kotlin.random.Random

/**
 * Vorschläge zum Füllen leerer Tage im Wochenplan.
 *
 * Reihenfolge der Kriterien: Was sich mit dem Vorrat kochen lässt, kommt zuerst; Favoriten werden bevorzugt;
 * ein zufälliger Anteil (fester [seed] → reproduzierbar, „Neu mischen“ = anderer Seed) sorgt für Abwechslung.
 * Kein Rezept zweimal im Vorschlag, und nichts, was gerade erst gekocht oder schon eingeplant ist.
 */
object MealSuggestions {
    data class Candidate(
        val id: String,
        val favorite: Boolean,
        /** Fehlende Pflichtzutaten laut [PantryCoverage]; null = keine Pflichtzutaten bekannt. */
        val missing: Int?,
        /** Zuletzt eingeplant (auch in der Vergangenheit); null = nie. */
        val lastPlanned: LocalDate?,
    )

    /** Ohne Wiederholung: Was in diesem Abstand vor dem ersten Vorschlagstag eingeplant war, bleibt außen vor. */
    const val REPEAT_GAP_DAYS = 14L

    fun suggest(candidates: List<Candidate>, days: List<LocalDate>, seed: Long): Map<LocalDate, String> {
        if (days.isEmpty()) return emptyMap()
        val firstDay = days.min()
        val random = Random(seed)
        val ranked = candidates
            .sortedBy { it.id } // unabhängig von der Reihenfolge der Datenbankabfrage
            .filter { c -> c.lastPlanned == null || c.lastPlanned < firstDay.minusDays(REPEAT_GAP_DAYS) }
            .map { it to score(it) + random.nextDouble() }
            .sortedByDescending { it.second }
            .map { it.first.id }
        return days.sorted().zip(ranked).toMap()
    }

    private fun score(c: Candidate): Double {
        val pantry = when (c.missing) {
            0 -> 2.0
            1 -> 1.2
            2 -> 0.5
            else -> 0.0
        }
        return pantry + if (c.favorite) 1.0 else 0.0
    }
}

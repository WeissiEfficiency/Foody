package de.foody.domain

import java.time.LocalDate
import kotlin.random.Random

/**
 * „Rezepte des Tages“: wählt pro Kalendertag eine feste, zufällig wirkende Auswahl.
 * Gleicher Tag + gleiche Rezepte → gleiche Auswahl (auch nach Neustart); am nächsten Tag eine neue.
 */
object DailyPicks {
    fun <T> pick(items: List<T>, date: LocalDate, count: Int = 3, key: (T) -> String): List<T> {
        if (items.size <= count) return items
        // Sortieren macht das Ergebnis unabhängig von der Reihenfolge der Datenbankabfrage.
        val sorted = items.sortedBy(key)
        return sorted.shuffled(Random(date.toEpochDay())).take(count)
    }
}

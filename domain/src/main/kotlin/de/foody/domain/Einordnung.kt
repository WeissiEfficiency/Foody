package de.foody.domain

import java.time.LocalTime

/**
 * Mahlzeit eines Rezepts oder Plan-Eintrags. Gespeichert wird immer der Enum-Name; Anzeigetexte kommen aus der App.
 * Mengen werden kommagetrennt gespeichert: `null` = nicht festgelegt, `""` = bewusst keine.
 */
enum class Mahlzeit(private val bezeichnung: String) {
    FRUEHSTUECK("frühstück"),
    MITTAGESSEN("mittagessen"),
    ABENDESSEN("abendessen"),
    SNACK("snack"),
    ;

    companion object {
        /** Enum-Name oder deutsche Bezeichnung, Groß-/Kleinschreibung egal; null = „Sonstiges“ (alter Freitext). */
        fun ausText(text: String): Mahlzeit? {
            val t = text.trim().lowercase()
            return entries.firstOrNull { t == it.name.lowercase() || t == it.bezeichnung }
        }

        fun mengeAus(text: String?): Set<Mahlzeit>? = mengeAus(text, entries)

        /** Reihenfolge der Einträge eines Tages: Frühstück, Mittag, Snack, Abend, dann Freitexte. */
        fun reihenfolge(slotType: String): Int = when (ausText(slotType)) {
            FRUEHSTUECK -> 0
            MITTAGESSEN -> 1
            SNACK -> 2
            ABENDESSEN -> 3
            null -> 4
        }

        /** Vorauswahl beim Einplanen: vor 10 Uhr Frühstück, vor 14 Uhr Mittag, sonst Abend. */
        fun vorschlagFuer(zeit: LocalTime): Mahlzeit = when {
            zeit.hour < 10 -> FRUEHSTUECK
            zeit.hour < 14 -> MITTAGESSEN
            else -> ABENDESSEN
        }
    }
}

enum class Gang {
    VORSPEISE, HAUPTSPEISE, NACHSPEISE, BROTZEIT;

    companion object {
        fun mengeAus(text: String?): Set<Gang>? = mengeAus(text, entries)
    }
}

/** Kommagetrennte Enum-Namen in Enum-Reihenfolge; `null` bleibt `null`, leere Menge wird `""`. */
fun <E : Enum<E>> alsText(menge: Set<E>?): String? = menge?.sortedBy { it.ordinal }?.joinToString(",") { it.name }

/** Unbekannte oder beschädigte Einträge werden übersprungen, nicht gemeldet. */
private fun <E : Enum<E>> mengeAus(text: String?, alle: List<E>): Set<E>? {
    if (text == null) return null
    val namen = text.split(',').map { it.trim() }.toSet()
    return alle.filter { it.name in namen }.toSet()
}

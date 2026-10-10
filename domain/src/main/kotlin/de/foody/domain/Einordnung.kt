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

/** Einordnung eines Rezepts: manuell festgelegt oder vermutet, je Dimension getrennt. */
data class Einordnung(
    val mahlzeiten: Set<Mahlzeit>,
    val gaenge: Set<Gang>,
    val mahlzeitenVermutet: Boolean,
    val gaengeVermutet: Boolean,
) {
    /** Leere Mahlzeitenmenge = passt überall. */
    fun passtZu(m: Mahlzeit) = mahlzeiten.isEmpty() || m in mahlzeiten

    /** Zusätzlich mindestens einer der [gewaehlt]en Gänge; keine Auswahl = kein Gangfilter. */
    fun passtZu(m: Mahlzeit, gewaehlt: Set<Gang>) = passtZu(m) && (gewaehlt.isEmpty() || gaenge.any { it in gewaehlt })

    val eingeordnet: Boolean get() = mahlzeiten.isNotEmpty()
}

/**
 * Vermutet Mahlzeiten und Gänge aus Tags und Name; Festgelegtes (nicht null) gewinnt je Dimension.
 * Die Vermutung wird nie gespeichert – Regeländerungen wirken sofort auf alle nicht festgelegten Rezepte.
 */
object RezeptEinordnung {
    private class Regel(val stichwoerter: List<String>, val mahlzeiten: Set<Mahlzeit>, val gaenge: Set<Gang>)

    private val mittagAbend = setOf(Mahlzeit.MITTAGESSEN, Mahlzeit.ABENDESSEN)

    private val regeln = listOf(
        Regel(
            listOf("frühstück", "müsli", "porridge", "pfannkuchen", "pancake", "rührei", "omelett", "granola", "overnight oats"),
            setOf(Mahlzeit.FRUEHSTUECK), emptySet(),
        ),
        Regel(
            listOf("dessert", "nachspeise", "nachtisch", "kuchen", "torte", "tiramisu", "mousse", "pudding", "eis", "crumble", "muffin"),
            setOf(Mahlzeit.SNACK), setOf(Gang.NACHSPEISE),
        ),
        Regel(listOf("vorspeise", "suppe", "salat"), mittagAbend, setOf(Gang.VORSPEISE, Gang.HAUPTSPEISE)),
        Regel(listOf("brotzeit", "brot", "aufstrich", "dip"), setOf(Mahlzeit.ABENDESSEN), setOf(Gang.BROTZEIT)),
        Regel(listOf("snack", "riegel", "smoothie", "joghurt"), setOf(Mahlzeit.FRUEHSTUECK, Mahlzeit.SNACK), emptySet()),
        Regel(
            listOf(
                "curry", "pasta", "nudel", "spaghetti", "lasagne", "auflauf", "risotto", "eintopf", "gulasch", "pfanne",
                "burger", "pizza", "braten", "schnitzel", "hauptgericht",
            ),
            mittagAbend, setOf(Gang.HAUPTSPEISE),
        ),
    )

    /** Ab dieser Länge trifft ein Stichwort auch als Teilwort („Gemüsecurry“); kürzere nur als ganzes Wort („Reis“ ≠ „eis“). */
    private const val TEILWORT_AB = 5

    fun einordnen(name: String, tags: List<String>, mahlzeiten: Set<Mahlzeit>?, gaenge: Set<Gang>?): Einordnung {
        val treffer = if (mahlzeiten != null && gaenge != null) emptyList() else treffer(name, tags)
        return Einordnung(
            mahlzeiten = mahlzeiten ?: treffer.flatMap { it.mahlzeiten }.toSet(),
            gaenge = gaenge ?: treffer.flatMap { it.gaenge }.toSet(),
            mahlzeitenVermutet = mahlzeiten == null,
            gaengeVermutet = gaenge == null,
        )
    }

    private fun treffer(name: String, tags: List<String>): List<Regel> {
        val woerter = tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() } +
            name.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        val stichwoerter = woerter.flatMap { wort ->
            val passend = regeln.flatMap { it.stichwoerter }.filter { s -> wort == s || (s.length >= TEILWORT_AB && s in wort) }
            // „Pfannkuchen“ ist Frühstück, kein Kuchen: Ein enthaltenes kürzeres Stichwort zählt nicht
            passend.filter { s -> passend.none { it != s && s in it } }
        }.toSet()
        return regeln.filter { r -> r.stichwoerter.any { it in stichwoerter } }
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

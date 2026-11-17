package de.foody.feature.rezepte.liste

import de.foody.core.data.rezepte.RezeptKarte
import de.foody.domain.Diet
import de.foody.domain.Mahlzeit

enum class Sortierung(val text: String) {
    NAME("Name"),
    NEUESTE("Neueste"),
    KCAL("kcal aufsteigend"),
    EIWEISS("Eiweiß"),
    OFT_GEKOCHT("Oft gekocht"),
}

/** Suche, Filterchips und Sortierung der Rezeptliste. */
data class ListeFilter(
    val suche: String = "",
    val favoriten: Boolean = false,
    /** Nur schnelle Rezepte: Gesamtzeit höchstens [SCHNELL_MINUTEN] (wie „≤ 30 Min.“ in den Entwürfen). */
    val schnell: Boolean = false,
    val oftGekocht: Boolean = false,
    val diaeten: Set<Diet> = emptySet(),
    val mahlzeit: Mahlzeit? = null,
    val tags: Set<String> = emptySet(),
    val archiv: Boolean = false,
    val sortierung: Sortierung = Sortierung.NAME,
) {
    /** Kein Chip gewählt (Chip „Alle“); Suche und Sortierung zählen nicht. */
    val ohneFilter: Boolean get() = this == ListeFilter(suche = suche, sortierung = sortierung)
}

/** Grenze für „≤ 30 Min.“: Vorbereitung und Kochzeit zusammen. */
const val SCHNELL_MINUTEN = 30

private val nachName = compareBy<RezeptKarte, String>(String.CASE_INSENSITIVE_ORDER) { it.name }

/**
 * Suche als Teilstring ohne Groß/klein in Name, Tags und Zutaten; ohne Archiv-Chip nur nicht archivierte, mit ihm nur
 * archivierte; „Oft gekocht“ = mindestens einmal gekocht; „≤ 30 Min.“ = Gesamtzeit bekannt und höchstens 30 Minuten. Gleichstand nach Name; kcal aufsteigend, ohne kcal zuletzt.
 */
fun filtern(karten: List<RezeptKarte>, f: ListeFilter): List<RezeptKarte> {
    val s = f.suche.trim()
    val treffer = karten.filter { k ->
        k.archiviert == f.archiv &&
            (s.isEmpty() || k.name.contains(s, true) || k.tags.any { it.contains(s, true) } || k.zutatNamen.any { it.contains(s, true) }) &&
            (!f.favoriten || k.favorit) &&
            (!f.schnell || (k.zeitMinuten ?: Int.MAX_VALUE) <= SCHNELL_MINUTEN) &&
            (!f.oftGekocht || k.gekocht > 0) &&
            k.diaeten.containsAll(f.diaeten) &&
            (f.mahlzeit == null || k.einordnung.passtZu(f.mahlzeit)) &&
            (f.tags.isEmpty() || k.tags.any { t -> f.tags.any { it.equals(t, true) } })
    }
    val ordnung = when (f.sortierung) {
        Sortierung.NAME -> nachName
        Sortierung.NEUESTE -> compareByDescending<RezeptKarte> { it.angelegt }.then(nachName)
        Sortierung.KCAL -> compareBy<RezeptKarte> { it.kcalJePortion == null }.thenBy { it.kcalJePortion }.then(nachName)
        Sortierung.EIWEISS -> compareByDescending<RezeptKarte> { it.eiweissJePortion ?: -1 }.then(nachName)
        Sortierung.OFT_GEKOCHT -> compareByDescending<RezeptKarte> { it.gekocht }.then(nachName)
    }
    return treffer.sortedWith(ordnung)
}

/** Die häufigsten Tags (höchstens [anzahl]) für die Tag-Chips. */
fun haeufigeTags(karten: List<RezeptKarte>, anzahl: Int = 12): List<String> =
    karten.filter { !it.archiviert }.flatMap { it.tags }.groupingBy { it }.eachCount()
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
        .take(anzahl).map { it.key }

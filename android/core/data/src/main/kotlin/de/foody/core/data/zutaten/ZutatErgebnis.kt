package de.foody.core.data.zutaten

/** Ergebnis einer schreibenden Aktion im Zutatenkatalog. */
sealed interface ZutatErgebnis {
    data class Gespeichert(val id: String) : ZutatErgebnis

    /** Eingaben ungültig; nichts geschrieben. */
    data class Ungueltig(val fehler: Map<ZutatFeld, String>) : ZutatErgebnis

    /** Eine andere Zutat heißt schon so (ohne Groß/klein). */
    data class NameVergeben(val id: String, val name: String) : ZutatErgebnis

    /** Der Strichcode gehört schon einer anderen Zutat. */
    data class StrichcodeVergeben(val id: String, val name: String) : ZutatErgebnis

    /** Von Rezepten verwendet; nichts gelöscht. */
    data class InVerwendung(val rezepte: Int) : ZutatErgebnis

    data object Geloescht : ZutatErgebnis

    data class Zusammengefuehrt(val id: String, val name: String) : ZutatErgebnis

    /** Zutat (oder Ziel) gibt es nicht (mehr), oder Quelle und Ziel sind gleich; nichts geändert. */
    data object NichtGefunden : ZutatErgebnis
}

package de.foody.feature.einstellungen

import de.foody.core.data.sicherung.ImportErgebnis

/** Hinweis, wenn Wiederherstellen oder Löschen bei verbundenem Sync gesperrt ist. */
const val ERST_TRENNEN = "Zuerst unter Konto die Verbindung trennen."

/** Meldung nach dem Wiederherstellen: Anzahlen; sonst Sperre oder Grund. */
fun importMeldung(e: ImportErgebnis): String = when (e) {
    is ImportErgebnis.Fertig -> buildString {
        append("${anzahl(e.rezepte, "Rezept", "Rezepte")}, ${anzahl(e.zutaten, "Zutat", "Zutaten")}, ")
        append("${anzahl(e.fotos, "Foto", "Fotos")} wiederhergestellt")
    }
    ImportErgebnis.SyncAktiv -> ERST_TRENNEN
    is ImportErgebnis.Fehler -> e.grund
}

/** „1 Foto“, „0 Fotos“, „5 Fotos“. */
private fun anzahl(n: Int, eins: String, viele: String) = "$n ${if (n == 1) eins else viele}"

package de.foody.feature.einstellungen

import de.foody.core.data.sicherung.ImportErgebnis

/** Hinweis, wenn Wiederherstellen oder Löschen bei verbundenem Sync gesperrt ist. */
const val ERST_TRENNEN = "Zuerst unter Konto die Verbindung trennen."

/** Meldung nach dem Wiederherstellen: Anzahlen, bei v1-Dateien der übersprungene Vorrat; sonst Sperre oder Grund. */
fun importMeldung(e: ImportErgebnis): String = when (e) {
    is ImportErgebnis.Fertig -> buildString {
        append("${e.rezepte} Rezepte, ${e.zutaten} Zutaten, ${e.fotos} Fotos wiederhergestellt")
        if (e.vorratUebersprungen > 0) append(" · Vorrat übersprungen: ${e.vorratUebersprungen}")
    }
    ImportErgebnis.SyncAktiv -> ERST_TRENNEN
    is ImportErgebnis.Fehler -> e.grund
}

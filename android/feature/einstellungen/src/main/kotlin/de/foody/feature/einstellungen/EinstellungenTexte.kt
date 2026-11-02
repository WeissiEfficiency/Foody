package de.foody.feature.einstellungen

import de.foody.core.data.sicherung.ImportErgebnis
import de.foody.core.erkennung.ModellDateien
import java.util.Locale

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

/** Kopieren des Modells ging schief (meist zu wenig Speicher). */
const val MODELL_FEHLER = "Installieren fehlgeschlagen (genug Speicher frei?)"

/** Meldung nach „Modell installieren“. */
fun modellMeldung(i: ModellDateien.Import): String = when (i) {
    is ModellDateien.Import.Fertig -> if (i.bekannt) "Modell installiert" else "Modell installiert (unbekannte Dateien, ohne Gewähr)"
    ModellDateien.Import.KeinModell -> "Das sind keine Modelldateien."
    ModellDateien.Import.FalscheDateien -> "Bitte beide Dateien wählen: Sprachteil und Bildteil (mmproj)."
    ModellDateien.Import.Fehler -> MODELL_FEHLER
}

/** „1,4 GB“; unter 1 GB in MB („350 MB“). */
fun groesseText(bytes: Long): String =
    if (bytes >= 1_000_000_000L) String.format(Locale.GERMANY, "%.1f GB", bytes / 1e9) else "${bytes / 1_000_000L} MB"

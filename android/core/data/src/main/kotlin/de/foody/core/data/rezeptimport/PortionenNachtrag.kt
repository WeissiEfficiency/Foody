package de.foody.core.data.rezeptimport

import androidx.room.withTransaction
import de.foody.core.data.db.FoodyDatenbank
import de.foody.domain.MarkdownRecipeImporter
import de.foody.domain.RezeptImport

/**
 * Zählt den Portionen-Nachtrag: [aktualisiert] und [uebersprungen] je Rezept, [ohneRezept] je Datei ohne Quelle
 * oder ohne Rezept mit dieser Quelle.
 */
data class NachtragErgebnis(val aktualisiert: Int, val uebersprungen: Int, val ohneRezept: Int)

/**
 * Trägt bei Rezepten, die vor Regel 27 importiert wurden, die Portionenzahl der Markdown-Datei nach
 * ([RezeptImport.portionenNachtragen]): nur `defaultServings` und Notiz, die Mengen nie. Den Knopf dafür bekommt die
 * App in Phase 6.
 *
 * - **Zuordnung:** `recipe.sourceUrl` = die vom [MarkdownRecipeImporter] gelesene Quelle, genau verglichen. Der Import
 *   speichert die Quelle unverändert so, wie der Parser sie liefert (bereits getrimmt); eine weitere Normalisierung
 *   (Schrägstrich am Ende, Groß-/Kleinschreibung) machte auch die Dublettenerkennung des Imports nicht.
 * - **Mehrere Dateien mit derselben Quelle:** Die erste Datei der Liste entscheidet; weitere werden nicht noch einmal
 *   gezählt.
 * - **Mehrere Rezepte mit derselben Quelle** (z. B. aus Sicherung oder Sync): jedes wird einzeln geprüft und gezählt,
 *   in der Reihenfolge der IDs.
 * - Alles in einer Transaktion. Bei aktivem Sync merken die Sync-Trigger die geänderten Rezepte in der Outbox vor.
 */
class PortionenNachtrag(
    private val db: FoodyDatenbank,
    private val uhr: () -> Long = System::currentTimeMillis,
) {
    /** [dateien]: Name und Inhalt der Markdown-Dateien; der Name dient nur der Anzeige beim Aufrufer. */
    suspend fun anwenden(dateien: List<Pair<String, String>>): NachtragErgebnis = db.withTransaction {
        val dao = db.rezeptDao()
        val jetzt = uhr()
        val behandelteQuellen = HashSet<String>()
        var aktualisiert = 0
        var uebersprungen = 0
        var ohneRezept = 0
        for ((_, inhalt) in dateien) {
            val datei = MarkdownRecipeImporter.parse(inhalt)
            val quelle = datei.sourceUrl?.takeIf { it.isNotBlank() }
            val rezepte = quelle?.let { dao.nachQuelle(it) }.orEmpty()
            if (quelle == null || rezepte.isEmpty()) {
                ohneRezept++
                continue
            }
            if (!behandelteQuellen.add(quelle)) continue
            for (rezept in rezepte) {
                val nachtrag = RezeptImport.portionenNachtragen(rezept.notes, rezept.defaultServings, datei)
                if (nachtrag == null) {
                    uebersprungen++
                } else {
                    // Zeitstempel steigt streng, auch wenn die Uhr hinter der letzten Änderung liegt
                    dao.portionenSetzen(rezept.id, nachtrag.portionen, nachtrag.notiz, maxOf(jetzt, rezept.updatedAt + 1))
                    aktualisiert++
                }
            }
        }
        NachtragErgebnis(aktualisiert, uebersprungen, ohneRezept)
    }
}

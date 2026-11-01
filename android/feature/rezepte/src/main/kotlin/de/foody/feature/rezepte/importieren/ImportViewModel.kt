package de.foody.feature.rezepte.importieren

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.core.data.rezepte.ImportErgebnis
import de.foody.core.data.rezepte.RezeptImporter
import de.foody.core.data.rezeptimport.NachtragErgebnis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ImportZustand {
    data object Bereit : ImportZustand

    /** [gesamt] 0 = Dateien werden noch gesucht. */
    data class Laeuft(val fertig: Int, val gesamt: Int) : ImportZustand

    data class Fertig(val ergebnis: ImportErgebnis) : ImportZustand

    /** „Portionen nachtragen“ ist durch ([nachtragText]). */
    data class NachtragFertig(val ergebnis: NachtragErgebnis) : ImportZustand
}

/** „1 importiert · 2 übersprungen (schon vorhanden) · 2 Fehler: x.md, y.md“ – leere Teile entfallen. */
fun zusammenfassung(e: ImportErgebnis): String = listOfNotNull(
    "${e.importiert.size} importiert",
    "${e.uebersprungen} übersprungen (schon vorhanden)".takeIf { e.uebersprungen > 0 },
    "${e.fehler.size} Fehler: ${e.fehler.joinToString(", ")}".takeIf { e.fehler.isNotEmpty() },
).joinToString(" · ")

/** „2 Rezepte aktualisiert · 1 schon richtig · 3 Dateien ohne passendes Rezept“ – leere Teile entfallen. */
fun nachtragText(e: NachtragErgebnis): String = listOfNotNull(
    (if (e.aktualisiert == 1) "1 Rezept aktualisiert" else "${e.aktualisiert} Rezepte aktualisiert").takeIf { e.aktualisiert > 0 },
    "${e.uebersprungen} schon richtig".takeIf { e.uebersprungen > 0 },
    (if (e.ohneRezept == 1) "1 Datei ohne passendes Rezept" else "${e.ohneRezept} Dateien ohne passendes Rezept")
        .takeIf { e.ohneRezept > 0 },
).joinToString(" · ").ifEmpty { "Nichts nachzutragen" }

@HiltViewModel
class ImportViewModel @Inject constructor(private val importer: RezeptImporter) : ViewModel() {
    private val stand = MutableStateFlow<ImportZustand>(ImportZustand.Bereit)
    val zustand: StateFlow<ImportZustand> = stand.asStateFlow()

    fun dateien(uris: List<Uri>) = starten { importer.dateien(uris, ::fortschritt) }

    fun ordner(baum: Uri) = starten { importer.ordner(baum, ::fortschritt) }

    /** Portionen aus den Dateien des Ordners nachtragen; läuft wie ein Import, einer zur Zeit. */
    fun portionen(baum: Uri) {
        if (stand.value is ImportZustand.Laeuft) return
        stand.value = ImportZustand.Laeuft(0, 0)
        viewModelScope.launch {
            stand.value = try {
                ImportZustand.NachtragFertig(importer.portionenNachtragen(baum))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ImportZustand.Fertig(ImportErgebnis(emptyList(), 0, listOf("Nachtrag abgebrochen")))
            }
        }
    }

    fun zuruecksetzen() {
        if (stand.value !is ImportZustand.Laeuft) stand.value = ImportZustand.Bereit
    }

    private fun fortschritt(fertig: Int, gesamt: Int) {
        stand.value = ImportZustand.Laeuft(fertig, gesamt)
    }

    /** Ein Import zur Zeit; ein unerwarteter Fehler beendet ihn mit einer Zusammenfassung statt abzustürzen. */
    private fun starten(import: suspend () -> ImportErgebnis) {
        if (stand.value is ImportZustand.Laeuft) return
        stand.value = ImportZustand.Laeuft(0, 0)
        viewModelScope.launch {
            stand.value = ImportZustand.Fertig(
                try {
                    import()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    ImportErgebnis(emptyList(), 0, listOf("Import abgebrochen"))
                },
            )
        }
    }
}

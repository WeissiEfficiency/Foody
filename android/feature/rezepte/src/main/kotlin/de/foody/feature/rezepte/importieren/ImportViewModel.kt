package de.foody.feature.rezepte.importieren

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.core.data.rezepte.ImportErgebnis
import de.foody.core.data.rezepte.RezeptImporter
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
}

/** „1 importiert · 2 übersprungen (schon vorhanden) · 2 Fehler: x.md, y.md“ – leere Teile entfallen. */
fun zusammenfassung(e: ImportErgebnis): String = listOfNotNull(
    "${e.importiert.size} importiert",
    "${e.uebersprungen} übersprungen (schon vorhanden)".takeIf { e.uebersprungen > 0 },
    "${e.fehler.size} Fehler: ${e.fehler.joinToString(", ")}".takeIf { e.fehler.isNotEmpty() },
).joinToString(" · ")

@HiltViewModel
class ImportViewModel @Inject constructor(private val importer: RezeptImporter) : ViewModel() {
    private val stand = MutableStateFlow<ImportZustand>(ImportZustand.Bereit)
    val zustand: StateFlow<ImportZustand> = stand.asStateFlow()

    fun dateien(uris: List<Uri>) = starten { importer.dateien(uris, ::fortschritt) }

    fun ordner(baum: Uri) = starten { importer.ordner(baum, ::fortschritt) }

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

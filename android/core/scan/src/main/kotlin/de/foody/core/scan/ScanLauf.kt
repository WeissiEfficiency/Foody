package de.foody.core.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import de.foody.core.data.zutaten.Packungswerte
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Ablauf hinter dem Scan-Knopf, gehalten vom ViewModel: Strichcode und Texterkennung laufen in [scope]
 * (`viewModelScope`) und brechen beim Drehen nicht ab; der Knopf-Zustand liegt in [gespeichert] und übersteht ein
 * Prozessende (samt Pfad eines offenen Kamerafotos). Treffer gehen an [onPackung] bzw. [onImKatalog].
 */
class ScanLauf(
    /** Play-Dienste da; sonst ist der Knopf gesperrt. */
    val verfuegbar: Boolean,
    /** Module vorladen, sobald der Knopf zu sehen ist. */
    val vorladen: () -> Unit,
    private val strichcodeLesen: suspend () -> ScanAusgang,
    private val fotoLesen: suspend (bildUri: String, strichcode: String?) -> ScanAusgang,
    private val scope: CoroutineScope,
    gespeichert: SavedStateHandle,
    private val onPackung: (Packungswerte) -> Unit,
    private val onImKatalog: (ScanAusgang.ImKatalog) -> Unit,
) {
    constructor(
        scan: PackungScan,
        scope: CoroutineScope,
        gespeichert: SavedStateHandle,
        onPackung: (Packungswerte) -> Unit,
        onImKatalog: (ScanAusgang.ImKatalog) -> Unit,
    ) : this(scan.verfuegbar, scan::vorladen, scan::strichcode, scan::foto, scope, gespeichert, onPackung, onImKatalog)

    @OptIn(SavedStateHandleSaveableApi::class)
    val zustand: ScanKnopfZustand = gespeichert.saveable(SCHLUESSEL, saver = ScanKnopfZustand.Saver) { ScanKnopfZustand() }

    /** Strichcode über das Menü: ein neuer Versuch, ohne den Strichcode eines früheren. */
    fun strichcode() {
        zustand.menueVersuch()
        zustand.starten()
        scope.launch { weitergeben(zustand.auswerten(strichcodeLesen())) }
    }

    /**
     * Foto der Nährwerttabelle erkennen; ein gemerkter Strichcode hängt sich an. [danach] räumt auf (Kameradatei),
     * auch wenn das ViewModel vorher endet.
     */
    fun foto(bildUri: String, danach: () -> Unit = {}) {
        zustand.starten()
        val code = zustand.strichcode
        scope.launch {
            try {
                weitergeben(zustand.auswerten(fotoLesen(bildUri, code)))
            } finally {
                danach()
            }
        }
    }

    private fun weitergeben(e: ScanKnopfZustand.Ergebnis?) = when (e) {
        is ScanKnopfZustand.Ergebnis.Packung -> onPackung(e.werte)
        is ScanKnopfZustand.Ergebnis.ImKatalog -> onImKatalog(e.treffer)
        null -> Unit
    }

    private companion object {
        const val SCHLUESSEL = "packungScan"
    }
}

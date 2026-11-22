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
 * Prozessende (samt Pfad eines offenen Kamerafotos). Treffer gehen an [onPackung] bzw. [onImKatalog], der gelesene
 * Strichcode vorher an [onGelesen], jeder andere Ausgang an [onOhneTreffer].
 */
class ScanLauf(
    /** Play-Dienste da; sonst ist der Knopf gesperrt. */
    val verfuegbar: Boolean,
    /** Module vorladen, sobald der Knopf zu sehen ist. */
    val vorladen: () -> Unit,
    /** Liest und schlägt nach; den gelesenen Code meldet es vor dem Nachschlagen an [onGelesen]. */
    private val strichcodeLesen: suspend (onGelesen: (String) -> Unit) -> ScanAusgang,
    private val fotoLesen: suspend (bildUri: String, strichcode: String?) -> ScanAusgang,
    private val scope: CoroutineScope,
    gespeichert: SavedStateHandle,
    private val onPackung: (Packungswerte) -> Unit,
    private val onImKatalog: (ScanAusgang.ImKatalog) -> Unit,
    /** Strichcode gelesen; Katalog und Open Food Facts sind noch nicht gefragt. */
    private val onGelesen: (String) -> Unit = {},
    /** Ausgang ohne Packung und ohne Katalog-Treffer (nicht gefunden, keine Tabelle, abgebrochen, Fehler …). */
    private val onOhneTreffer: (ScanAusgang) -> Unit = {},
) {
    constructor(
        scan: PackungScan,
        scope: CoroutineScope,
        gespeichert: SavedStateHandle,
        onPackung: (Packungswerte) -> Unit,
        onImKatalog: (ScanAusgang.ImKatalog) -> Unit,
        onGelesen: (String) -> Unit = {},
        onOhneTreffer: (ScanAusgang) -> Unit = {},
    ) : this(scan.verfuegbar, scan::vorladen, scan::strichcode, scan::foto, scope, gespeichert, onPackung, onImKatalog, onGelesen, onOhneTreffer)

    @OptIn(SavedStateHandleSaveableApi::class)
    val zustand: ScanKnopfZustand = gespeichert.saveable(SCHLUESSEL, saver = ScanKnopfZustand.Saver) { ScanKnopfZustand() }

    /** Strichcode über das Menü: ein neuer Versuch, ohne den Strichcode eines früheren. */
    fun strichcode() {
        zustand.menueVersuch()
        zustand.starten()
        scope.launch { weitergeben(strichcodeLesen(onGelesen)) }
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
                weitergeben(fotoLesen(bildUri, code))
            } finally {
                danach()
            }
        }
    }

    private fun weitergeben(a: ScanAusgang) = when (val e = zustand.auswerten(a)) {
        is ScanKnopfZustand.Ergebnis.Packung -> onPackung(e.werte)
        is ScanKnopfZustand.Ergebnis.ImKatalog -> onImKatalog(e.treffer)
        null -> onOhneTreffer(a)
    }

    private companion object {
        const val SCHLUESSEL = "packungScan"
    }
}

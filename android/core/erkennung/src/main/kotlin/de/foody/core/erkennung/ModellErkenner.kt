package de.foody.core.erkennung

import de.foody.domain.FotoAnweisung
import de.foody.domain.FotoErkennung
import de.foody.domain.FotoKontext
import de.foody.domain.FotoVorschlag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * Ablauf einer Erkennung (Spec Phase 2): Modell und Speicher prüfen, Bild vorbereiten, dann auf [ablauf] (ein eigener
 * Thread) die Auswahl aus den eigenen Rezepten und immer auch die Trainingsanweisung (Beschreibung des Gerichts). Alles zusammen
 * höchstens [ZEITLIMIT_MS]; Abbrechen und Zeitlimit stoppen auch die native Seite. Eine Erkennung zur Zeit.
 */
class ModellErkenner @Inject constructor(
    private val dateien: ModellDateien,
    private val modell: BildSprachModell,
    private val bilder: BildVorbereitung,
    private val speicher: FreierSpeicher,
    @Erkennen private val ablauf: CoroutineContext,
) : MahlzeitErkenner {
    private val einzeln = Mutex()

    override suspend fun erkennen(bildUri: String, kontext: FotoKontext): ErkennungStatus = einzeln.withLock {
        val pfade = dateien.pfade() ?: return ErkennungStatus.KeinModell
        if (speicher.bytes() < MIN_FREIER_SPEICHER) return ErkennungStatus.ZuWenigSpeicher
        val bild = bilder.vorbereiten(bildUri) ?: return ErkennungStatus.Fehler
        try {
            modell.sitzung().use { sitzung ->
                val start = System.nanoTime()
                val status = withTimeoutOrNull(ZEITLIMIT_MS) { abbrechbar(sitzung) { befragen(sitzung, pfade, bild.path, kontext) } }
                when (status) {
                    null -> ErkennungStatus.Zeitlimit
                    is Ergebnis.Vorschlag -> ErkennungStatus.Erkannt(status.vorschlag, status.alternative, (System.nanoTime() - start) / 1_000_000)
                    Ergebnis.Unlesbar -> ErkennungStatus.Unlesbar
                    Ergebnis.Fehler -> ErkennungStatus.Fehler
                }
            }
        } finally {
            bild.delete()
        }
    }

    private sealed interface Ergebnis {
        data class Vorschlag(val vorschlag: FotoVorschlag, val alternative: FotoVorschlag?) : Ergebnis

        data object Unlesbar : Ergebnis

        data object Fehler : Ergebnis
    }

    /** Blockiert; läuft auf [ablauf]. */
    private fun befragen(sitzung: ModellSitzung, pfade: ModellPfade, bild: String, kontext: FotoKontext): Ergebnis {
        if (!sitzung.laden(pfade, bild)) return Ergebnis.Fehler
        val rezept = FotoAnweisung.auswahl(kontext)?.let { auswahl ->
            val antwort = sitzung.fragen(auswahl) ?: return Ergebnis.Fehler
            FotoErkennung.vorschlag(antwort, kontext) as? FotoVorschlag.Rezept
        }
        val gericht = FotoErkennung.vorschlag(sitzung.fragen(FotoAnweisung.GERICHT) ?: return Ergebnis.Fehler, kontext)
        return when {
            rezept != null -> Ergebnis.Vorschlag(rezept, gericht)
            gericht != null -> Ergebnis.Vorschlag(gericht, null)
            else -> Ergebnis.Unlesbar
        }
    }

    /**
     * Führt den blockierenden [block] auf [ablauf] aus. Wird gewartet abgebrochen (Zeitlimit, Nutzer), bekommt die
     * Sitzung [ModellSitzung.abbrechen]; zurück geht es erst, wenn die native Seite fertig ist (danach wird sie freigegeben).
     */
    private suspend fun <T> abbrechbar(sitzung: ModellSitzung, block: () -> T): T = coroutineScope {
        val arbeit = async(ablauf) { block() }
        try {
            arbeit.await()
        } catch (e: CancellationException) {
            sitzung.abbrechen()
            throw e
        }
    }

    companion object {
        const val ZEITLIMIT_MS = 60_000L

        /**
         * Gemessen 2,6–3,5 GB beim Erkennen (ADR 0017), davon 1,1 GB Gewichte per mmap: Dateicache, den Android jederzeit
         * freigeben kann. Zusätzlich gebraucht werden etwa 1,5 GB (Bildteil, Kontext, Rechenpuffer).
         */
        const val MIN_FREIER_SPEICHER = 2_000_000_000L
    }
}

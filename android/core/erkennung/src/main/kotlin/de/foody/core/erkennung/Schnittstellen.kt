package de.foody.core.erkennung

import de.foody.domain.FotoKontext
import de.foody.domain.FotoVorschlag

/** Erkennt eine Mahlzeit auf einem Foto (Uri als Text, damit die Schnittstelle ohne Android-Typen testbar bleibt). */
fun interface MahlzeitErkenner {
    suspend fun erkennen(bildUri: String, kontext: FotoKontext): ErkennungStatus
}

sealed interface ErkennungStatus {
    /**
     * Vorschlag fürs Eintragen-Blatt. Ist [vorschlag] ein Rezept, steht in [alternative] die Beschreibung des Gerichts
     * (Teller oder Frei): Das Modell wählt fast immer ein Rezept, auch wenn keines passt (Messung Phase 2), deshalb muss
     * die Vorschau mit einem Tipp umschalten können. [dauerMs] vom Laden bis zur letzten Antwort.
     */
    data class Erkannt(val vorschlag: FotoVorschlag, val alternative: FotoVorschlag?, val dauerMs: Long) : ErkennungStatus

    /** Kein Modell installiert (z. B. auf dem Tablet): der Knopf bleibt ausgeblendet. */
    data object KeinModell : ErkennungStatus

    /** Weniger als [ModellErkenner.MIN_FREIER_SPEICHER] frei: andere Apps schließen. */
    data object ZuWenigSpeicher : ErkennungStatus

    /** Länger als [ModellErkenner.ZEITLIMIT_MS]. */
    data object Zeitlimit : ErkennungStatus

    /** Das Modell hat geantwortet, aber ohne verwertbares JSON. */
    data object Unlesbar : ErkennungStatus

    /** Bild nicht lesbar, Laufzeit fehlt oder Modell lädt nicht. */
    data object Fehler : ErkennungStatus
}

/** Pfade der beiden Modelldateien (Sprachteil und Bildteil). */
data class ModellPfade(val sprache: String, val bild: String)

/** Bild-Sprach-Modell (llama.cpp); Tests ersetzen es durch ein Fake. Alle Aufrufe blockieren. */
fun interface BildSprachModell {
    fun sitzung(): ModellSitzung
}

/** Eine Erkennung: Modell und Bild einmal laden, dann Fragen zum Bild stellen. */
interface ModellSitzung : AutoCloseable {
    /** Lädt Modell und Bild; `false`, wenn eines davon nicht geht oder abgebrochen wurde. */
    fun laden(pfade: ModellPfade, bildPfad: String): Boolean

    /** Antwort auf eine Anweisung zum Bild; `null` nach [abbrechen] oder bei einem Fehler. */
    fun fragen(anweisung: String): String?

    /** Bricht Laden oder Antworten bald ab; darf aus einem anderen Thread kommen. */
    fun abbrechen()

    /** Gibt Modell und Speicher frei. */
    override fun close()
}

/** Bereitet das Foto für das Modell vor (verkleinert, gedreht); `null`, wenn es nicht lesbar ist. */
fun interface BildVorbereitung {
    suspend fun vorbereiten(bildUri: String): java.io.File?
}

/** Freier Arbeitsspeicher in Bytes. */
fun interface FreierSpeicher {
    fun bytes(): Long
}

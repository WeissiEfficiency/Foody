package de.foody.app.scan

import de.foody.app.data.db.IngredientEntity
import de.foody.domain.ScanErgebnis

/** Liest einen Strichcode (EAN/UPC); `null` = abgebrochen. */
fun interface StrichcodeLeser {
    suspend fun lesen(): StrichcodeStatus
}

sealed interface StrichcodeStatus {
    data class Gelesen(val code: String) : StrichcodeStatus
    data object Abgebrochen : StrichcodeStatus
    data object WirdGeladen : StrichcodeStatus
    data object Fehler : StrichcodeStatus
}

/** Erkennt die Nährwerttabelle auf einem Bild (Uri als Text, damit die Schnittstelle ohne Android-Typen testbar bleibt). */
fun interface TabellenScanner {
    suspend fun scan(bildUri: String): ScanStatus
}

sealed interface ScanStatus {
    data class Erkannt(val ergebnis: ScanErgebnis) : ScanStatus
    /** Das Erkennungsmodul wird gerade über die Play-Dienste geladen; der Download ist angestoßen. */
    data object WirdGeladen : ScanStatus
    data object Fehler : ScanStatus
}

/** Online-Produktsuche per Strichcode. */
fun interface ProduktSuche {
    suspend fun suche(code: String): Antwort

    sealed interface Antwort {
        data class Gefunden(val packung: Packung) : Antwort
        data object Unbekannt : Antwort
        data object Offline : Antwort
    }
}

/** Eigene Zutaten mit gemerktem Strichcode. */
fun interface KatalogSuche {
    suspend fun zutatMitStrichcode(code: String): IngredientEntity?
}

/** Laufen Google-Play-Dienste? Ohne sie gibt es weder Code Scanner noch Texterkennung. */
fun interface PlayDienste {
    fun verfuegbar(): Boolean
}

/** Darf die Produktnummer an Open Food Facts gehen? (Einstellung in „Mehr“) */
fun interface OnlineSucheErlaubt {
    fun erlaubt(): Boolean
}

/** Ergebnis eines Scans für die Oberfläche. */
sealed interface ScanAusgang {
    /** Werte zum Vorausfüllen (Open Food Facts oder Foto). */
    data class Gefunden(val packung: Packung) : ScanAusgang
    /** Die Zutat mit diesem Strichcode gibt es schon. */
    data class ImKatalog(val zutat: IngredientEntity) : ScanAusgang
    /** Strichcode gelesen, aber keine Werte – Foto anbieten; [strichcode] beim Speichern trotzdem merken. */
    data class NichtGefunden(val strichcode: String, val grund: Grund) : ScanAusgang
    /** Foto ohne erkennbare Nährwerttabelle. */
    data class KeineTabelle(val strichcode: String?) : ScanAusgang
    data object WirdGeladen : ScanAusgang
    data object NichtVerfuegbar : ScanAusgang
    data object Abgebrochen : ScanAusgang
    data object Fehler : ScanAusgang

    /** Warum ein gelesener Strichcode keine Werte brachte – bestimmt die Meldung. */
    enum class Grund { UNBEKANNT, OFFLINE, ONLINE_AUS }
}

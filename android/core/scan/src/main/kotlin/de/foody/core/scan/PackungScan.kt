package de.foody.core.scan

import de.foody.core.data.zutaten.Packungswerte
import javax.inject.Inject

/**
 * Ablauf „Packung scannen“ (wie v1, ADR 0008): Strichcode lesen → eigenen Katalog fragen (offline) → Open Food Facts
 * (wenn erlaubt) → sonst Foto der Nährwerttabelle anbieten. Liefert nur Vorschläge; übernommen wird erst, wenn der
 * Nutzer speichert.
 */
class PackungScan @Inject constructor(
    private val leser: StrichcodeLeser,
    private val tabelle: TabellenScanner,
    private val suche: ProduktSuche,
    private val katalog: KatalogSuche,
    private val play: PlayDienste,
    private val online: OnlineSucheErlaubt,
) {
    val verfuegbar: Boolean get() = play.verfuegbar()

    /** Beim Anzeigen des Scan-Knopfs: Module vorladen, damit der erste Scan nicht erst „wird vorbereitet“ zeigt. */
    fun vorladen() {
        if (play.verfuegbar()) play.vorladen()
    }

    suspend fun strichcode(): ScanAusgang {
        if (!play.verfuegbar()) return ScanAusgang.NichtVerfuegbar
        val code = when (val s = leser.lesen()) {
            is StrichcodeStatus.Gelesen -> s.code
            StrichcodeStatus.Abgebrochen -> return ScanAusgang.Abgebrochen
            StrichcodeStatus.WirdGeladen -> return ScanAusgang.WirdGeladen
            StrichcodeStatus.Fehler -> return ScanAusgang.Fehler
        }
        katalog.mitStrichcode(code)?.let { return ScanAusgang.ImKatalog(it.id, it.name, code) }
        return online(code)
    }

    /**
     * Nur Open Food Facts, ohne den Katalog-Schritt („Online abrufen“ im Zutateneditor). Ist die Online-Suche aus,
     * geht keine Anfrage hinaus.
     */
    suspend fun online(code: String): ScanAusgang {
        if (!online.erlaubt()) return ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.ONLINE_AUS)
        return when (val a = suche.suche(code)) {
            is ProduktSuche.Antwort.Gefunden -> ScanAusgang.Gefunden(a.packung)
            ProduktSuche.Antwort.Unbekannt -> ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.UNBEKANNT)
            ProduktSuche.Antwort.Offline -> ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.OFFLINE)
        }
    }

    /** Foto der Nährwerttabelle; [strichcode] aus einem vorherigen Scan wird an die Packung gehängt. */
    suspend fun foto(bildUri: String, strichcode: String?): ScanAusgang {
        if (!play.verfuegbar()) return ScanAusgang.NichtVerfuegbar
        return when (val s = tabelle.scan(bildUri)) {
            is ScanStatus.Erkannt ->
                if (s.ergebnis.werte.isEmpty()) ScanAusgang.KeineTabelle(strichcode)
                else ScanAusgang.Gefunden(Packungswerte(null, s.ergebnis.basis, s.ergebnis.werte, strichcode, QUELLE_FOTO))
            ScanStatus.WirdGeladen -> ScanAusgang.WirdGeladen
            ScanStatus.Fehler -> ScanAusgang.Fehler
        }
    }

    companion object {
        const val QUELLE_FOTO = "Packung (Foto)"
    }
}

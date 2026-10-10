package de.foody.app.scan

import javax.inject.Inject

/**
 * Ablauf „Von Packung scannen“ (Spec C, Abschnitt 2): Strichcode lesen → eigenen Katalog fragen (offline) →
 * Open Food Facts (wenn erlaubt) → sonst Foto der Nährwerttabelle anbieten. Liefert nur Vorschläge;
 * übernommen wird erst, wenn der Nutzer speichert.
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

    suspend fun strichcode(): ScanAusgang {
        if (!play.verfuegbar()) return ScanAusgang.NichtVerfuegbar
        val code = when (val s = leser.lesen()) {
            is StrichcodeStatus.Gelesen -> s.code
            StrichcodeStatus.Abgebrochen -> return ScanAusgang.Abgebrochen
            StrichcodeStatus.WirdGeladen -> return ScanAusgang.WirdGeladen
            StrichcodeStatus.Fehler -> return ScanAusgang.Fehler
        }
        katalog.zutatMitStrichcode(code)?.let { return ScanAusgang.ImKatalog(it) }
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
                else ScanAusgang.Gefunden(Packung(null, s.ergebnis.basis, s.ergebnis.werte, strichcode, QUELLE_FOTO))
            ScanStatus.WirdGeladen -> ScanAusgang.WirdGeladen
            ScanStatus.Fehler -> ScanAusgang.Fehler
        }
    }

    companion object {
        const val QUELLE_FOTO = "Packung (Foto)"
    }
}

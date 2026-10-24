package de.foody.app3

import android.os.Build
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.sync.Ergebnis
import de.foody.core.sync.ErsterSync
import de.foody.core.sync.SyncAusloeser
import de.foody.core.sync.SyncKonto
import de.foody.feature.einstellungen.KontoZugriff
import de.foody.feature.einstellungen.VerbindungsZustand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** „Server verbinden“ auf dem echten Sync: Konto, Zeile `sync_state`, Auslöser. */
class KontoAnbindung(
    private val konto: SyncKonto,
    private val db: FoodyDatenbank,
    private val ausloeser: SyncAusloeser,
) : KontoZugriff {
    override val zustand: Flow<VerbindungsZustand> = db.syncDao().zustandFlow().map { z ->
        VerbindungsZustand(
            verbunden = z?.active == true,
            adresse = z?.serverUrl,
            letzterSync = z?.lastSyncAt,
            letzterFehler = z?.lastError,
        )
    }.distinctUntilChanged()

    override suspend fun anmelden(adresse: String, benutzer: String, passwort: String): Ergebnis<Unit> =
        konto.anmelden(adresse, benutzer, passwort, geraetename())

    override suspend fun hatLokaleDaten(): Boolean = konto.hatLokaleDaten()

    override suspend fun istWiederanmeldung(): Boolean = konto.istWiederanmeldung()

    override suspend fun aktivieren(modus: ErsterSync): Ergebnis<Unit> = konto.aktivieren(modus)

    override suspend fun trennen() = konto.trennen()

    override fun jetztSynchronisieren() {
        ausloeser.jetzt()
    }
}

/** Gerätename für die Server-Anmeldung wie in der v1: „Hersteller Modell“, gekürzt. */
private fun geraetename(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(MAX_GERAETENAME).ifBlank { "Android" }

private const val MAX_GERAETENAME = 64

package de.foody.feature.einstellungen

import de.foody.core.sync.Ergebnis
import de.foody.core.sync.ErsterSync
import kotlinx.coroutines.flow.Flow

/** Stand der Verbindung für die Anzeige; [letzterFehler] ist eine Kennung (`offline`, …), nie ein Server-Text. */
data class VerbindungsZustand(val verbunden: Boolean, val adresse: String?, val letzterSync: Long?, val letzterFehler: String?)

/**
 * Was „Server verbinden“ vom Sync braucht. Die App bindet es an `SyncKonto`, `sync_state` und `SyncAusloeser` (und
 * ergänzt beim Anmelden den Gerätenamen); Tests setzen einen Ersatz ein.
 */
interface KontoZugriff {
    val zustand: Flow<VerbindungsZustand>

    suspend fun anmelden(adresse: String, benutzer: String, passwort: String): Ergebnis<Unit>

    suspend fun hatLokaleDaten(): Boolean

    suspend fun aktivieren(modus: ErsterSync): Ergebnis<Unit>

    suspend fun trennen()

    fun jetztSynchronisieren()
}

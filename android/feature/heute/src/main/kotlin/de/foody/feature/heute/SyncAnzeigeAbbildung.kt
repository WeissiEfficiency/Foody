package de.foody.feature.heute

import de.foody.core.designsystem.komponenten.SyncAnzeige

/** Sync-Zustand → Anzeige im Kopf; `null`, wenn nicht verbunden (dann kein Symbol). */
fun syncAnzeige(aktiv: Boolean, laeuft: Boolean, letzterFehler: String?): SyncAnzeige? = when {
    !aktiv -> null
    laeuft -> SyncAnzeige.Laeuft
    letzterFehler == null -> SyncAnzeige.Synchron
    letzterFehler == "offline" -> SyncAnzeige.Wartet
    letzterFehler == "anmeldung_abgelaufen" -> SyncAnzeige.Fehler("Anmeldung abgelaufen")
    letzterFehler == "app_zu_alt" -> SyncAnzeige.Fehler("App zu alt")
    letzterFehler == "server_zu_alt" -> SyncAnzeige.Fehler("Server zu alt")
    else -> SyncAnzeige.Fehler("Sync-Fehler")
}

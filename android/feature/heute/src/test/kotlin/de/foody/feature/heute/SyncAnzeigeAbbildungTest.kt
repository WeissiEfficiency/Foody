package de.foody.feature.heute

import de.foody.core.designsystem.komponenten.SyncAnzeige
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncAnzeigeAbbildungTest {
    @Test
    fun nichtAktivNull() {
        assertNull(syncAnzeige(aktiv = false, laeuft = true, letzterFehler = "offline"))
    }

    @Test
    fun laeuftVorFehler() {
        assertEquals(SyncAnzeige.Laeuft, syncAnzeige(aktiv = true, laeuft = true, letzterFehler = "offline"))
    }

    @Test
    fun ohneFehlerSynchron() {
        assertEquals(SyncAnzeige.Synchron, syncAnzeige(aktiv = true, laeuft = false, letzterFehler = null))
    }

    @Test
    fun jeKennungDerSpecText() {
        fun a(k: String) = syncAnzeige(aktiv = true, laeuft = false, letzterFehler = k)
        assertEquals(SyncAnzeige.Wartet, a("offline"))
        assertEquals(SyncAnzeige.Fehler("Anmeldung abgelaufen"), a("anmeldung_abgelaufen"))
        assertEquals(SyncAnzeige.Fehler("App zu alt"), a("app_zu_alt"))
        assertEquals(SyncAnzeige.Fehler("Server zu alt"), a("server_zu_alt"))
        assertEquals(SyncAnzeige.Fehler("Sync-Fehler"), a("cursor_haengt"))
        assertEquals(SyncAnzeige.Fehler("Sync-Fehler"), a("server_fehler"))
    }
}

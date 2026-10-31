package de.foody.feature.einstellungen

import kotlin.test.Test
import kotlin.test.assertEquals

class FehlerTexteTest {
    @Test
    fun anmeldeKennungen() {
        assertEquals("Benutzername oder Passwort falsch.", anmeldeFehlerText("falsche_zugangsdaten"))
        assertEquals("Die Adresse ist ungültig (https nötig).", anmeldeFehlerText("ungueltige_adresse"))
        assertEquals("Zu viele Versuche – bitte kurz warten.", anmeldeFehlerText("zu_viele_versuche"))
        assertEquals("Keine Verbindung zum Server.", anmeldeFehlerText("offline"))
        assertEquals("Der Server ist zu alt für diese App.", anmeldeFehlerText("server_zu_alt"))
        assertEquals("Diese App ist zu alt für den Server.", anmeldeFehlerText("app_zu_alt"))
        assertEquals("Anmeldung konnte nicht gespeichert werden.", anmeldeFehlerText("token_nicht_gespeichert"))
    }

    @Test
    fun unbekannteAnmeldeKennungRueckfall() {
        assertEquals("Anmeldung fehlgeschlagen.", anmeldeFehlerText("server_fehler"))
        assertEquals("Anmeldung fehlgeschlagen.", anmeldeFehlerText(""))
    }

    @Test
    fun syncKennungen() {
        assertEquals("Keine Verbindung zum Server.", syncFehlerText("offline"))
        assertEquals("Anmeldung abgelaufen – bitte neu anmelden.", syncFehlerText("anmeldung_abgelaufen"))
        assertEquals("Der Server ist zu alt für diese App.", syncFehlerText("server_zu_alt"))
        assertEquals("Diese App ist zu alt für den Server.", syncFehlerText("app_zu_alt"))
        assertEquals("Gerätespeicher voll – bitte Platz schaffen.", syncFehlerText("speicher_voll"))
        assertEquals("Synchronisieren fehlgeschlagen.", syncFehlerText("cursor_haengt"))
    }

    @Test
    fun nichtUebertrageneEintraege() {
        assertEquals("1 Eintrag nicht vollständig übertragen", nichtUebertragenTitel(1))
        assertEquals("3 Einträge nicht vollständig übertragen", nichtUebertragenTitel(3))
        assertEquals("Zutat „Mehl“: ungültige Daten", problemText("ingredient", "Mehl", "invalid_payload"))
        assertEquals("Plan-Eintrag (nicht auf diesem Gerät): verweist auf etwas, das fehlt", problemText("meal_slot", null, "missing_reference"))
        assertEquals("Rezept „Suppe“: vom Server abgelehnt", problemText("recipe", "Suppe", "name_too_long"))
    }

    @Test
    fun zuletztRelativ() {
        val jetzt = 1_000_000_000L
        assertEquals("noch nie", zuletztText(null, jetzt))
        assertEquals("gerade eben", zuletztText(jetzt - 30_000, jetzt))
        assertEquals("vor 1 Minute", zuletztText(jetzt - 60_000, jetzt))
        assertEquals("vor 5 Minuten", zuletztText(jetzt - 5 * 60_000, jetzt))
        assertEquals("vor 2 Stunden", zuletztText(jetzt - 2 * 3_600_000, jetzt))
        assertEquals("vor 3 Tagen", zuletztText(jetzt - 3 * 86_400_000L, jetzt))
    }
}

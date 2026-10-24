package de.foody.feature.einstellungen

private const val KEINE_VERBINDUNG = "Keine Verbindung zum Server."
private const val SERVER_ZU_ALT = "Der Server ist zu alt für diese App."
private const val APP_ZU_ALT = "Diese App ist zu alt für den Server."

/** Klartext zu einer Kennung aus `SyncKonto.anmelden`/`aktivieren`; Unbekanntes → „Anmeldung fehlgeschlagen.“ */
fun anmeldeFehlerText(kennung: String): String = when (kennung) {
    "falsche_zugangsdaten" -> "Benutzername oder Passwort falsch."
    "ungueltige_adresse" -> "Die Adresse ist ungültig (https nötig)."
    "zu_viele_versuche" -> "Zu viele Versuche – bitte kurz warten."
    "offline" -> KEINE_VERBINDUNG
    "server_zu_alt" -> SERVER_ZU_ALT
    "app_zu_alt" -> APP_ZU_ALT
    "token_nicht_gespeichert" -> "Anmeldung konnte nicht gespeichert werden."
    else -> "Anmeldung fehlgeschlagen."
}

/** Klartext zum letzten Fehler eines Sync-Laufs (`sync_state.lastError`). */
fun syncFehlerText(kennung: String): String = when (kennung) {
    "offline" -> KEINE_VERBINDUNG
    "anmeldung_abgelaufen" -> "Anmeldung abgelaufen – bitte neu anmelden."
    "server_zu_alt" -> SERVER_ZU_ALT
    "app_zu_alt" -> APP_ZU_ALT
    else -> "Synchronisieren fehlgeschlagen."
}

private const val MINUTE_MS = 60_000L
private const val STUNDE_MS = 60 * MINUTE_MS
private const val TAG_MS = 24 * STUNDE_MS

/** „zuletzt synchronisiert“ relativ zu [jetzt] (Epoch-ms). */
fun zuletztText(letzterSync: Long?, jetzt: Long): String {
    if (letzterSync == null) return "noch nie"
    val vergangen = (jetzt - letzterSync).coerceAtLeast(0)
    fun mehrzahl(n: Long, eins: String, viele: String) = "vor $n ${if (n == 1L) eins else viele}"
    return when {
        vergangen < MINUTE_MS -> "gerade eben"
        vergangen < STUNDE_MS -> mehrzahl(vergangen / MINUTE_MS, "Minute", "Minuten")
        vergangen < TAG_MS -> mehrzahl(vergangen / STUNDE_MS, "Stunde", "Stunden")
        else -> mehrzahl(vergangen / TAG_MS, "Tag", "Tagen")
    }
}

package de.foody.core.sync

import de.foody.api.protocol.ErrorCode

/**
 * Fehler beim Zugriff auf den Sync-Server. [kennung] ist eine der festen Kennungen für `sync_state.lastError`
 * (`offline`, `anmeldung_abgelaufen`, `app_zu_alt`, `server_zu_alt`, `cursor_haengt`, `server_fehler`).
 *
 * Meldung und Ursachen enthalten nie Token oder Antwort-Bodies: Die Meldung ist die Kennung, Ursachen werden nur bei
 * Netzfehlern behalten (deren Meldungen nennen höchstens Host und Port).
 */
sealed class ServerFehler(val kennung: String, ursache: Throwable? = null) : Exception(kennung, ursache) {
    /** Keine Verbindung oder Zeitüberschreitung; vorübergehend, später erneut versuchen. */
    class Offline(ursache: Throwable? = null) : ServerFehler(OFFLINE, ursache)

    /** `401`: Token ungültig oder widerrufen; bei der Anmeldung mit [code] `invalid_credentials`. */
    class AnmeldungAbgelaufen(val code: ErrorCode?) : ServerFehler(ANMELDUNG_ABGELAUFEN)

    /** `409 protocol_too_old`: Die App spricht ein zu altes Protokoll. */
    class AppZuAlt : ServerFehler(APP_ZU_ALT)

    /** `409 server_too_old` oder `404` auf `/api/v3`: Der Server kennt Protokoll 3 nicht. */
    class ServerZuAlt : ServerFehler(SERVER_ZU_ALT)

    /** `410 cursor_expired`: Der Pull-Cursor liegt hinter der Kompaktierung; der Sync gleicht dann voll ab. */
    class CursorAbgelaufen : ServerFehler(SERVER_FEHLER)

    /** `413 too_large`: Anfrage oder Foto zu groß. */
    class ZuGross : ServerFehler(SERVER_FEHLER)

    /** Sonstige `3xx`/`4xx` (Weiterleitungen werden nicht verfolgt); [code] aus dem Fehler-Body, falls lesbar. */
    class Abgelehnt(val status: Int, val code: ErrorCode?) : ServerFehler(SERVER_FEHLER)

    /** `5xx`, eine Antwort, die nicht dem Protokoll entspricht, oder ein unerwarteter Fehler ([status] `0`). */
    class Server(val status: Int) : ServerFehler(SERVER_FEHLER)

    companion object {
        const val OFFLINE = "offline"
        const val ANMELDUNG_ABGELAUFEN = "anmeldung_abgelaufen"
        const val APP_ZU_ALT = "app_zu_alt"
        const val SERVER_ZU_ALT = "server_zu_alt"

        /** Der Pull-Cursor rückt nicht vor (vergibt der Sync-Lauf, nicht der Server-Zugang). */
        const val CURSOR_HAENGT = "cursor_haengt"
        const val SERVER_FEHLER = "server_fehler"
    }
}

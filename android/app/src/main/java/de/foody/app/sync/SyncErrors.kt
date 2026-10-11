package de.foody.app.sync

import androidx.annotation.StringRes
import de.foody.app.R
import de.foody.sync.protocol.ErrorCode
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** Wo der Fehler auftrat; bestimmt z. B., wie ein 400 `invalid_input` zu lesen ist. */
enum class ErrorContext { LOGIN, REGISTER, GENERAL }

/** Übersetzt Fehler der Sync-Aufrufe in deutsche Meldungen (String-Ressourcen). */
object SyncErrors {
    @StringRes
    fun messageFor(e: Throwable, context: ErrorContext): Int {
        if (hasTlsCause(e)) return R.string.sync_error_certificate
        if (e !is SyncApiException) return R.string.sync_error_generic
        return when (e) {
            is SyncApiException.Throttled -> R.string.sync_error_throttled
            is SyncApiException.Unauthorized ->
                if (context == ErrorContext.GENERAL) R.string.sync_error_unauthorized else R.string.sync_error_invalid_credentials
            is SyncApiException.ProtocolMismatch ->
                if (e.code == ErrorCode.SERVER_TOO_OLD) R.string.sync_error_update_server else R.string.sync_error_update_app
            is SyncApiException.ClientError -> when (e.code) {
                ErrorCode.INVALID_CREDENTIALS -> R.string.sync_error_invalid_credentials
                ErrorCode.INVALID_INVITE -> R.string.sync_error_invalid_invite
                ErrorCode.USERNAME_TAKEN -> R.string.sync_error_username_taken
                ErrorCode.INVALID_INPUT ->
                    if (context == ErrorContext.REGISTER) R.string.sync_error_invalid_input_register else R.string.sync_error_generic
                // Ohne Fehlercode: keine Foody-Antwort (falsche Adresse, Weiterleitung, Proxy-Fehlerseite).
                null -> R.string.sync_error_unreachable
                else -> R.string.sync_error_generic
            }
            is SyncApiException.Transient -> R.string.sync_error_unreachable
            is SyncApiException.NoHousehold, is SyncApiException.CursorExpired, is SyncApiException.TooLarge ->
                R.string.sync_error_generic
        }
    }

    /** TLS-Ursache irgendwo in der Cause-Kette (begrenzt, falls eine Kette Schleifen enthält). */
    private fun hasTlsCause(e: Throwable): Boolean {
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth++ < 16) {
            if (current is SSLHandshakeException || current is SSLPeerUnverifiedException || current is CertPathValidatorException) return true
            current = current.cause
        }
        return false
    }
}

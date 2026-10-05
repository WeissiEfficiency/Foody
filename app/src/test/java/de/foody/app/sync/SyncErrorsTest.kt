package de.foody.app.sync

import de.foody.app.R
import de.foody.sync.protocol.ErrorCode
import java.io.IOException
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncErrorsTest {
    private fun msg(e: Throwable, c: ErrorContext = ErrorContext.GENERAL) = SyncErrors.messageFor(e, c)

    @Test
    fun credentialsAndThrottle() {
        assertEquals(R.string.sync_error_invalid_credentials, msg(SyncApiException.Unauthorized(401, ErrorCode.INVALID_CREDENTIALS), ErrorContext.LOGIN))
        assertEquals(R.string.sync_error_throttled, msg(SyncApiException.Throttled(429, ErrorCode.THROTTLED), ErrorContext.LOGIN))
    }

    @Test
    fun unauthorizedInGeneralContextMeansSignedOut() {
        assertEquals(R.string.sync_error_unauthorized, msg(SyncApiException.Unauthorized(401, ErrorCode.UNAUTHORIZED)))
    }

    @Test
    fun registerErrors() {
        assertEquals(R.string.sync_error_invalid_invite, msg(SyncApiException.ClientError(400, ErrorCode.INVALID_INVITE), ErrorContext.REGISTER))
        assertEquals(R.string.sync_error_username_taken, msg(SyncApiException.ClientError(409, ErrorCode.USERNAME_TAKEN), ErrorContext.REGISTER))
        assertEquals(R.string.sync_error_invalid_input_register, msg(SyncApiException.ClientError(400, ErrorCode.INVALID_INPUT), ErrorContext.REGISTER))
    }

    @Test
    fun protocolMismatch() {
        assertEquals(R.string.sync_error_update_app, msg(SyncApiException.ProtocolMismatch(409, ErrorCode.PROTOCOL_TOO_OLD)))
        assertEquals(R.string.sync_error_update_server, msg(SyncApiException.ProtocolMismatch(409, ErrorCode.SERVER_TOO_OLD)))
    }

    @Test
    fun tlsCauseAnywhereInChain() {
        assertEquals(R.string.sync_error_certificate, msg(SyncApiException.Transient(0, null, SSLHandshakeException("x"))))
        val wrapped = IOException("outer", RuntimeException("mid", CertPathValidatorException("anchor")))
        assertEquals(R.string.sync_error_certificate, msg(SyncApiException.Transient(0, null, wrapped)))
    }

    @Test
    fun peerUnverifiedIsCertificateError() {
        assertEquals(R.string.sync_error_certificate, msg(SyncApiException.Transient(0, null, javax.net.ssl.SSLPeerUnverifiedException("Hostname"))))
        // Die Oberklasse allein (z. B. Verbindungsabbruch) ist kein Zertifikatsproblem.
        assertEquals(R.string.sync_error_unreachable, msg(SyncApiException.Transient(0, null, javax.net.ssl.SSLException("reset"))))
    }

    @Test
    fun otherNetworkErrorsAreUnreachable() {
        assertEquals(R.string.sync_error_unreachable, msg(SyncApiException.Transient(0, null, IOException("refused"))))
        assertEquals(R.string.sync_error_unreachable, msg(SyncApiException.Transient(503, null)))
        assertEquals(R.string.sync_error_unreachable, msg(SyncApiException.ClientError(404, null), ErrorContext.LOGIN))
    }

    @Test
    fun unknownThrowableIsGeneric() {
        assertEquals(R.string.sync_error_generic, msg(IllegalStateException("x")))
    }
}

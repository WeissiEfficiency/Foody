package de.foody.app.sync

import de.foody.sync.protocol.AuthResponse
import de.foody.sync.protocol.DeviceDto
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.HouseholdDto
import de.foody.sync.protocol.InviteDto
import de.foody.sync.protocol.LoginRequest
import de.foody.sync.protocol.PullResponse
import de.foody.sync.protocol.PushResponse
import de.foody.sync.protocol.RegisterRequest
import de.foody.sync.protocol.SyncRecord

/** Aufrufe des selbst gehosteten Sync-Servers (`/api/v1`). Fehler kommen als [SyncApiException]. */
interface SyncApi {
    suspend fun login(req: LoginRequest): AuthResponse
    suspend fun register(req: RegisterRequest): AuthResponse
    suspend fun households(): List<HouseholdDto>
    suspend fun createHousehold(name: String): HouseholdDto
    suspend fun selectHousehold(id: String)
    suspend fun createInvite(): InviteDto
    suspend fun devices(): List<DeviceDto>
    suspend fun revokeDevice(id: String)
    suspend fun push(records: List<SyncRecord>): PushResponse
    suspend fun pull(since: Long, limit: Int): PullResponse
}

/** Fehler eines Sync-Aufrufs; [status] ist 0, wenn keine HTTP-Antwort vorlag, [code] `null`, wenn der Server keinen nannte. */
sealed class SyncApiException(val status: Int, val code: ErrorCode?, cause: Throwable? = null) :
    Exception("Sync-Fehler (HTTP $status, ${code ?: "ohne Code"})", cause) {

    /** 401: Token ungültig oder Gerät widerrufen. */
    class Unauthorized(status: Int, code: ErrorCode?) : SyncApiException(status, code)

    /** 409 `protocol_too_old` / `server_too_old`: App und Server sprechen kein gemeinsames Protokoll. */
    class ProtocolMismatch(status: Int, code: ErrorCode?) : SyncApiException(status, code)

    /** 409 `no_household`: das Gerät gehört noch zu keinem Haushalt. */
    class NoHousehold(status: Int, code: ErrorCode?) : SyncApiException(status, code)

    /** 410: der Pull-Cursor liegt hinter der Kompaktierung; ein vollständiger Pull ab 0 ist nötig. */
    class CursorExpired(status: Int, code: ErrorCode?) : SyncApiException(status, code)

    /** 413: Anfrage zu groß. */
    class TooLarge(status: Int, code: ErrorCode?) : SyncApiException(status, code)

    /** 429: zu viele Versuche. */
    class Throttled(status: Int, code: ErrorCode?) : SyncApiException(status, code)

    /** Übrige 4xx-Antworten (Eingabe abgelehnt, falsche Zugangsdaten, …). */
    class ClientError(status: Int, code: ErrorCode?) : SyncApiException(status, code)

    /** 5xx, Netzwerkfehler, Zeitüberschreitung oder unlesbare Antwort: später erneut versuchen. */
    class Transient(status: Int, code: ErrorCode?, cause: Throwable? = null) : SyncApiException(status, code, cause)
}

package de.foody.core.sync

import de.foody.api.protocol.AuthResponse
import de.foody.api.protocol.LoginRequest
import de.foody.api.protocol.PullResponse
import de.foody.api.protocol.PushResponse
import de.foody.api.protocol.SyncRecord

/**
 * Zugang zum Sync-Server (Protokoll 3, `/api/v3`). Jeder Fehler kommt als [ServerFehler]; Coroutine-Abbrüche laufen
 * unverändert durch.
 */
interface ServerZugang {
    /** `POST /auth/login`: liefert Geräte-Token und Konto-ID. Schickt nie ein Token mit. */
    suspend fun anmelden(anfrage: LoginRequest): AuthResponse

    /** `POST /auth/abmelden`: widerruft das eigene Token. */
    suspend fun abmelden()

    /** `POST /sync/push`: höchstens `Protocol.MAX_PUSH_RECORDS` Datensätze bzw. `MAX_PUSH_BYTES`. */
    suspend fun push(datensaetze: List<SyncRecord>): PushResponse

    /** `GET /sync/pull?since&limit`. */
    suspend fun pull(since: Long, limit: Int): PullResponse

    /** `POST /photos/missing`: die Hashes, die der Server noch nicht hat (beliebig viele, in Paketen gefragt). */
    suspend fun fotosFehlend(hashes: List<String>): List<String>

    /** `PUT /photos/{sha256}`: lädt ein JPEG hoch. */
    suspend fun fotoHochladen(sha256: String, bytes: ByteArray)

    /** `GET /photos/{sha256}`: das JPEG oder `null`, wenn der Server es nicht hat. */
    suspend fun fotoLaden(sha256: String): ByteArray?
}

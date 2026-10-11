package de.foody.server

import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.Protocol
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/** Obergrenze für Request-Bodies aller Routen außer `/sync/push` (dort gilt [Protocol.MAX_PUSH_BYTES]). */
const val MAX_BODY_BYTES = 64L * 1024

private fun tooLarge() = ApiException(HttpStatusCode.PayloadTooLarge, ErrorCode.TOO_LARGE)

/**
 * Liest den Body höchstens bis [maxBytes]: erst über Content-Length, dann beim gezählten Lesen
 * (chunked hat keine). Wirft `413 too_large`, bevor mehr als [maxBytes] Bytes im Speicher landen.
 */
suspend fun ApplicationCall.readBoundedBytes(maxBytes: Long): ByteArray {
    val declared = request.contentLength()
    if (declared != null && declared > maxBytes) throw tooLarge()
    val bytes = receiveChannel().readRemaining(maxBytes + 1).readByteArray()
    if (bytes.size > maxBytes) throw tooLarge()
    return bytes
}

/** Liest einen JSON-Body mit [MAX_BODY_BYTES]-Grenze; falscher Medientyp → 415, ungültiges JSON → 400. */
suspend fun <T> ApplicationCall.receiveJson(serializer: KSerializer<T>): T {
    if (!request.contentType().match(ContentType.Application.Json)) {
        throw ApiException(HttpStatusCode.UnsupportedMediaType, ErrorCode.INVALID_INPUT)
    }
    val bytes = readBoundedBytes(MAX_BODY_BYTES)
    return try {
        Protocol.json.decodeFromString(serializer, bytes.decodeToString())
    } catch (_: SerializationException) {
        throw ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INPUT)
    } catch (_: IllegalArgumentException) {
        throw ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INPUT)
    }
}

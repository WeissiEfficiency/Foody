package de.foody.server.photos

import de.foody.server.ApiException
import de.foody.server.ServerDeps
import de.foody.server.auth.DevicePrincipal
import de.foody.server.receiveJson
import de.foody.server.readBoundedBytes
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.PhotoHash
import de.foody.sync.protocol.PhotosMissingRequest
import de.foody.sync.protocol.PhotosMissingResponse
import de.foody.sync.protocol.Protocol
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.response.respondBytes

private fun invalidInput() = ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INPUT)

private fun householdOf(device: DevicePrincipal): String =
    device.householdId ?: throw ApiException(HttpStatusCode.Conflict, ErrorCode.NO_HOUSEHOLD)

/** JPEG-Signatur: `FF D8 FF`. */
private fun isJpeg(bytes: ByteArray) =
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

/**
 * Foto-Endpunkte; innerhalb von `authenticate("device")` einzuhängen. Der Haushalt kommt immer aus dem Gerät,
 * das Pfadsegment wird vor jedem Dateizugriff gegen [PhotoHash.REGEX] geprüft.
 */
fun Route.photoRoutes(deps: ServerDeps) {
    post("/photos/missing") {
        val household = householdOf(call.principal<DevicePrincipal>()!!)
        val request = call.receiveJson(PhotosMissingRequest.serializer())
        if (request.hashes.size > Protocol.MAX_PUSH_RECORDS) throw ApiException(HttpStatusCode.PayloadTooLarge, ErrorCode.TOO_LARGE)
        if (!request.hashes.all(PhotoHash::isValid)) throw invalidInput()
        val missing = request.hashes.distinct().filterNot { deps.photos.exists(household, it) }
        call.respond(PhotosMissingResponse(missing))
    }
    put("/photos/{sha256}") {
        val household = householdOf(call.principal<DevicePrincipal>()!!)
        val sha = call.parameters["sha256"]?.takeIf(PhotoHash::isValid) ?: throw invalidInput()
        // Größe prüfen, bevor mehr als nötig im Speicher landet; Content-Type wird nicht vertraut.
        val bytes = call.readBoundedBytes(Protocol.MAX_PHOTO_BYTES)
        if (!isJpeg(bytes)) throw invalidInput()
        if (PhotoHash.of(bytes) != sha) throw invalidInput()
        if (!deps.photos.exists(household, sha)) deps.photos.write(household, sha, bytes)
        call.respond(HttpStatusCode.NoContent)
    }
    get("/photos/{sha256}") {
        val household = householdOf(call.principal<DevicePrincipal>()!!)
        val sha = call.parameters["sha256"]?.takeIf(PhotoHash::isValid) ?: throw invalidInput()
        val file = deps.photos.read(household, sha) ?: throw ApiException(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND)
        call.respondBytes(java.nio.file.Files.readAllBytes(file), ContentType.Image.JPEG)
    }
}

package de.foody.server.photos

import de.foody.api.protocol.ErrorCode
import de.foody.api.protocol.PhotoHash
import de.foody.api.protocol.PhotosMissingRequest
import de.foody.api.protocol.PhotosMissingResponse
import de.foody.api.protocol.Protocol
import de.foody.server.ApiException
import de.foody.server.ServerDeps
import de.foody.server.auth.DevicePrincipal
import de.foody.server.readBoundedBytes
import de.foody.server.receiveJson
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

private fun invalidInput() = ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INPUT)

/** JPEG-Signatur: `FF D8 FF`. */
private fun isJpeg(bytes: ByteArray) =
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

/**
 * Foto-Endpunkte; innerhalb von `authenticate("device")` einzuhängen. Das Konto kommt immer aus dem Gerät,
 * das Pfadsegment wird vor jedem Dateizugriff gegen [PhotoHash.REGEX] geprüft.
 */
fun Route.photoRoutes(deps: ServerDeps) {
    post("/photos/missing") {
        val konto = call.principal<DevicePrincipal>()!!.kontoId
        val request = call.receiveJson(PhotosMissingRequest.serializer())
        if (request.hashes.size > Protocol.MAX_PUSH_RECORDS) throw ApiException(HttpStatusCode.PayloadTooLarge, ErrorCode.TOO_LARGE)
        if (!request.hashes.all(PhotoHash::isValid)) throw invalidInput()
        val (present, missing) = request.hashes.distinct().partition { deps.photos.exists(konto, it) }
        // Der Absender will diese Fotos gleich referenzieren: Schonfrist der Aufräumung neu starten.
        present.forEach { deps.photos.touch(konto, it, deps.clock.instant()) }
        call.respond(PhotosMissingResponse(missing))
    }
    put("/photos/{sha256}") {
        val konto = call.principal<DevicePrincipal>()!!.kontoId
        val sha = call.parameters["sha256"]?.takeIf(PhotoHash::isValid) ?: throw invalidInput()
        // Größe prüfen, bevor mehr als nötig im Speicher landet; Content-Type wird nicht vertraut.
        val bytes = call.readBoundedBytes(Protocol.MAX_PHOTO_BYTES)
        if (!isJpeg(bytes)) throw invalidInput()
        if (PhotoHash.of(bytes) != sha) throw invalidInput()
        if (deps.photos.exists(konto, sha)) deps.photos.touch(konto, sha, deps.clock.instant())
        else deps.photos.write(konto, sha, bytes)
        call.respond(HttpStatusCode.NoContent)
    }
    get("/photos/{sha256}") {
        val konto = call.principal<DevicePrincipal>()!!.kontoId
        val sha = call.parameters["sha256"]?.takeIf(PhotoHash::isValid) ?: throw invalidInput()
        val file = deps.photos.read(konto, sha) ?: throw ApiException(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND)
        call.respondBytes(java.nio.file.Files.readAllBytes(file), ContentType.Image.JPEG)
    }
}

package de.foody.server.sync

import de.foody.server.ApiException
import de.foody.server.ServerDeps
import de.foody.server.auth.DevicePrincipal
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.PushRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.SerializationException

private fun invalidInput() = ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INPUT)
private fun tooLarge() = ApiException(HttpStatusCode.PayloadTooLarge, ErrorCode.TOO_LARGE)

private fun householdOf(device: DevicePrincipal): String =
    device.householdId ?: throw ApiException(HttpStatusCode.Conflict, ErrorCode.NO_HOUSEHOLD)

/** Push/Pull; innerhalb von `authenticate("device")` einzuhängen. Der Haushalt kommt immer aus dem Gerät. */
fun Route.syncRoutes(deps: ServerDeps) {
    post("/sync/push") {
        val household = householdOf(call.principal<DevicePrincipal>()!!)
        // Größe prüfen, bevor geparst wird: erst über Content-Length, dann beim gezählten Lesen (chunked hat keine).
        val declared = call.request.contentLength()
        if (declared != null && declared > Protocol.MAX_PUSH_BYTES) throw tooLarge()
        val bytes = call.receiveChannel().readRemaining(Protocol.MAX_PUSH_BYTES + 1).readByteArray()
        if (bytes.size > Protocol.MAX_PUSH_BYTES) throw tooLarge()
        val request = try {
            Protocol.json.decodeFromString(PushRequest.serializer(), bytes.decodeToString())
        } catch (_: SerializationException) {
            throw invalidInput()
        } catch (_: IllegalArgumentException) {
            throw invalidInput()
        }
        if (request.records.size > Protocol.MAX_PUSH_RECORDS) throw tooLarge()
        call.respond(deps.sync.push(household, request.records))
    }
    get("/sync/pull") {
        val household = householdOf(call.principal<DevicePrincipal>()!!)
        val sinceParam = call.request.queryParameters["since"]
        val since = if (sinceParam == null) 0L else sinceParam.toLongOrNull()?.takeIf { it >= 0 } ?: throw invalidInput()
        val limitParam = call.request.queryParameters["limit"]
        val limit = if (limitParam == null) {
            Protocol.MAX_PULL_LIMIT
        } else {
            (limitParam.toLongOrNull() ?: throw invalidInput()).coerceIn(1L, Protocol.MAX_PULL_LIMIT.toLong()).toInt()
        }
        call.respond(deps.sync.pull(household, since, limit))
    }
}

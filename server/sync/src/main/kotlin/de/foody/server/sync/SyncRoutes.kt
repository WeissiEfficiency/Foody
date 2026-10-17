package de.foody.server.sync

import de.foody.server.ApiException
import de.foody.server.ServerDeps
import de.foody.server.auth.DevicePrincipal
import de.foody.server.readBoundedBytes
import de.foody.api.protocol.ErrorCode
import de.foody.api.protocol.Protocol
import de.foody.api.protocol.PushRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.SerializationException

private fun invalidInput() = ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INPUT)
private fun tooLarge() = ApiException(HttpStatusCode.PayloadTooLarge, ErrorCode.TOO_LARGE)

/** Push/Pull; innerhalb von `authenticate("device")` einzuhängen. Das Konto kommt immer aus dem Gerät. */
fun Route.syncRoutes(deps: ServerDeps) {
    post("/sync/push") {
        val konto = call.principal<DevicePrincipal>()!!.kontoId
        // Größe prüfen, bevor geparst wird (gezähltes Lesen, siehe BodyLimit.kt).
        val bytes = call.readBoundedBytes(Protocol.MAX_PUSH_BYTES)
        val request = try {
            Protocol.json.decodeFromString(PushRequest.serializer(), bytes.decodeToString())
        } catch (_: SerializationException) {
            throw invalidInput()
        } catch (_: IllegalArgumentException) {
            throw invalidInput()
        }
        if (request.records.size > Protocol.MAX_PUSH_RECORDS) throw tooLarge()
        call.respond(deps.sync.push(konto, request.records))
    }
    get("/sync/pull") {
        val konto = call.principal<DevicePrincipal>()!!.kontoId
        val sinceParam = call.request.queryParameters["since"]
        val since = if (sinceParam == null) 0L else sinceParam.toLongOrNull()?.takeIf { it >= 0 } ?: throw invalidInput()
        val limitParam = call.request.queryParameters["limit"]
        val limit = if (limitParam == null) {
            Protocol.MAX_PULL_LIMIT
        } else {
            (limitParam.toLongOrNull() ?: throw invalidInput()).coerceIn(1L, Protocol.MAX_PULL_LIMIT.toLong()).toInt()
        }
        call.respond(deps.sync.pull(konto, since, limit))
    }
}

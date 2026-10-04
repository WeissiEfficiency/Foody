package de.foody.server

import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.ErrorDto
import de.foody.sync.protocol.Protocol
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/** Fachlicher Fehler, der als [status] mit [ErrorDto] beantwortet wird. */
class ApiException(val status: HttpStatusCode, val code: ErrorCode) : RuntimeException(code.name)

@Serializable
private data class HealthDto(val status: String)

/**
 * Prüft die Protokollversion des Clients. Als Route-Plugin auf `/api/v1` installiert läuft es vor
 * allen später darunter installierten Plugins (z. B. Authentifizierung).
 */
private val ProtocolCheck = createRouteScopedPlugin("ProtocolCheck") {
    onCall { call ->
        val version = call.request.headers[Protocol.HEADER]?.trim()?.toIntOrNull()
        when {
            version == null || version < Protocol.MIN_VERSION ->
                throw ApiException(HttpStatusCode.Conflict, ErrorCode.PROTOCOL_TOO_OLD)
            version > Protocol.VERSION ->
                throw ApiException(HttpStatusCode.Conflict, ErrorCode.SERVER_TOO_OLD)
        }
    }
}

fun Application.foodyModule(deps: ServerDeps) {
    val log = LoggerFactory.getLogger("de.foody.server")
    install(ContentNegotiation) { json(Protocol.json) }
    install(XForwardedHeaders)
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(e.status, ErrorDto(e.code)) }
        exception<Throwable> { call, e ->
            log.error("Unbehandelte Ausnahme", e)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
    routing {
        get("/health") { call.respond(HealthDto("ok")) }
        route("/api/v1") {
            install(ProtocolCheck)
            // Platzhalter, den Task 4 mit den Haushalts-Routen füllt.
            get("/households") { call.respond(HttpStatusCode.NotImplemented) }
        }
    }
}

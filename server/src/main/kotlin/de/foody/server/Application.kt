package de.foody.server

import de.foody.server.auth.DevicePrincipal
import de.foody.server.auth.deviceAuthRoutes
import de.foody.server.auth.publicAuthRoutes
import de.foody.server.household.householdRoutes
import de.foody.server.sync.syncRoutes
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.ErrorDto
import de.foody.sync.protocol.Protocol
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
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
    install(Authentication) {
        bearer("device") {
            // Fehlender, falscher oder widerrufener Token: einheitlich 401 mit ErrorDto (über StatusPages).
            authHeader { call ->
                val header = runCatching { call.request.parseAuthorizationHeader() }.getOrNull()
                if (header !is HttpAuthHeader.Single || !header.authScheme.equals("Bearer", ignoreCase = true)) {
                    throw ApiException(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
                }
                header
            }
            authenticate { credential ->
                deps.accounts.deviceForToken(credential.token)
                    ?: throw ApiException(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
            }
        }
    }
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(e.status, ErrorDto(e.code)) }
        // Client-Fehler beim Lesen des Bodys sind keine Serverfehler.
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorDto(ErrorCode.INVALID_INPUT))
        }
        exception<CannotTransformContentToTypeException> { call, _ ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ErrorDto(ErrorCode.INVALID_INPUT))
        }
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ErrorDto(ErrorCode.INVALID_INPUT))
        }
        exception<Throwable> { call, e ->
            // Abbruch durch den Client (Verbindung weg) ist kein Fehler.
            if (e is CancellationException) throw e
            log.error("Unbehandelte Ausnahme", e)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
    routing {
        get("/health") { call.respond(HealthDto("ok")) }
        route("/api/v1") {
            install(ProtocolCheck)
            publicAuthRoutes(deps)
            authenticate("device") {
                deviceAuthRoutes(deps)
                householdRoutes(deps)
                syncRoutes(deps)
            }
        }
    }
}

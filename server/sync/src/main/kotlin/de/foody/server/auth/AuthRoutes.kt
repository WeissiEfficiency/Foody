package de.foody.server.auth

import de.foody.api.protocol.AuthResponse
import de.foody.api.protocol.ErrorCode
import de.foody.api.protocol.LoginRequest
import de.foody.api.protocol.PasswordChangeRequest
import de.foody.server.ApiException
import de.foody.server.ServerDeps
import de.foody.server.receiveJson
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

private const val MAX_DEVICE_NAME = 64
private const val MAX_USERNAME = 32
private const val MAX_PASSWORD = 200
private val log = LoggerFactory.getLogger("de.foody.server.auth")

/** Öffentliche Auth-Routen (ohne Token); unterhalb von `/api/v3` einzuhängen. */
fun Route.publicAuthRoutes(deps: ServerDeps) {
    post("/auth/login") {
        val request = call.receiveJson(LoginRequest.serializer())
        // Überlange Eingaben können nie gültig sein: ohne Drosselungseintrag und ohne Argon2 abweisen.
        if (request.username.length > MAX_USERNAME || request.password.length > MAX_PASSWORD) {
            throw ApiException(HttpStatusCode.Unauthorized, ErrorCode.INVALID_CREDENTIALS)
        }
        val ip = call.request.origin.remoteHost
        deps.throttle.check(ip, request.username)
        // Argon2 rechnet spürbar: nicht auf dem Thread der Anfrage
        val kontoId = withContext(Dispatchers.Default) { deps.accounts.authenticate(request.username, request.password) }
        if (kontoId == null) {
            log.warn("Fehlgeschlagene Anmeldung für {} von {}", request.username, ip)
            throw ApiException(HttpStatusCode.Unauthorized, ErrorCode.INVALID_CREDENTIALS)
        }
        deps.throttle.success(ip, request.username)
        val token = deps.accounts.createDevice(kontoId, request.deviceName.take(MAX_DEVICE_NAME))
        call.respond(AuthResponse(token, kontoId))
    }
}

/** Auth-Routen mit Token; innerhalb von `authenticate("device")` einzuhängen. */
fun Route.deviceAuthRoutes(deps: ServerDeps) {
    // „Trennen“: widerruft nur das eigene Token; danach antwortet der Server diesem Gerät mit 401.
    post("/auth/abmelden") {
        deps.accounts.abmelden(call.principal<DevicePrincipal>()!!.deviceId)
        call.respond(HttpStatusCode.NoContent)
    }
    post("/auth/password") {
        val device = call.principal<DevicePrincipal>()!!
        val request = call.receiveJson(PasswordChangeRequest.serializer())
        if (request.oldPassword.length > MAX_PASSWORD || request.newPassword.length > MAX_PASSWORD) {
            throw ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INPUT)
        }
        // Ein gestohlener Gerätetoken darf das alte Passwort nicht unbegrenzt raten lassen.
        val ip = call.request.origin.remoteHost
        val key = "password:${device.kontoId}"
        deps.throttle.check(ip, key)
        withContext(Dispatchers.Default) {
            deps.accounts.changePassword(device.kontoId, device.deviceId, request.oldPassword, request.newPassword)
        }
        deps.throttle.success(ip, key)
        call.respond(HttpStatusCode.NoContent)
    }
}

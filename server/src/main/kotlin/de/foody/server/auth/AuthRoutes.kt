package de.foody.server.auth

import de.foody.server.ApiException
import de.foody.server.ServerDeps
import de.foody.server.receiveJson
import de.foody.sync.protocol.AuthResponse
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.LoginRequest
import de.foody.sync.protocol.PasswordChangeRequest
import de.foody.sync.protocol.RegisterRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

private const val MAX_DEVICE_NAME = 64
private const val REGISTER_THROTTLE_KEY = "invite:register"
private const val MAX_USERNAME = 32
private const val MAX_PASSWORD = 200
private const val MAX_CODE = 32

/** Öffentliche Auth-Routen (ohne Token); unterhalb von `/api/v1` einzuhängen. */
fun Route.publicAuthRoutes(deps: ServerDeps) {
    post("/auth/login") {
        val request = call.receiveJson(LoginRequest.serializer())
        // Überlange Eingaben können nie gültig sein: ohne Drosselungseintrag und ohne Argon2 abweisen.
        if (request.username.length > MAX_USERNAME || request.password.length > MAX_PASSWORD) {
            throw ApiException(HttpStatusCode.Unauthorized, ErrorCode.INVALID_CREDENTIALS)
        }
        val ip = call.request.origin.remoteHost
        deps.throttle.check(ip, request.username)
        val userId = deps.accounts.authenticate(request.username, request.password)
            ?: throw ApiException(HttpStatusCode.Unauthorized, ErrorCode.INVALID_CREDENTIALS)
        deps.throttle.success(ip, request.username)
        val householdId = deps.accounts.soleHouseholdOf(userId)
        val token = deps.accounts.createDevice(userId, householdId, request.deviceName.take(MAX_DEVICE_NAME))
        call.respond(AuthResponse(token, userId, householdId))
    }

    post("/auth/register") {
        val request = call.receiveJson(RegisterRequest.serializer())
        if (request.code.length > MAX_CODE || request.username.length > MAX_USERNAME || request.password.length > MAX_PASSWORD) {
            throw ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INPUT)
        }
        // Einladungscodes haben nur 40 Bit: Versuche je IP (nicht je Code) drosseln, sonst ließe sich
        // durch wechselnde Codes raten. Der Schlüssel enthält ':' und kollidiert so nie mit einem Benutzernamen.
        val ip = call.request.origin.remoteHost
        deps.throttle.check(ip, REGISTER_THROTTLE_KEY)
        val auth = deps.accounts.redeemInvite(
            request.code,
            request.username,
            request.password,
            request.deviceName.take(MAX_DEVICE_NAME),
        )
        // Bewusst kein throttle.success: sonst ließe sich der IP-Zähler durch Einlösen eigener Einladungen zurücksetzen.
        call.respond(auth)
    }
}

/** Auth-Routen mit Token; innerhalb von `authenticate("device")` einzuhängen. */
fun Route.deviceAuthRoutes(deps: ServerDeps) {
    post("/auth/password") {
        val device = call.principal<DevicePrincipal>()!!
        val request = call.receiveJson(PasswordChangeRequest.serializer())
        if (request.oldPassword.length > MAX_PASSWORD || request.newPassword.length > MAX_PASSWORD) {
            throw ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INPUT)
        }
        // Ein gestohlener Gerätetoken darf das alte Passwort nicht unbegrenzt raten lassen.
        val ip = call.request.origin.remoteHost
        val key = "password:${device.userId}"
        deps.throttle.check(ip, key)
        deps.accounts.changePassword(device.userId, device.deviceId, request.oldPassword, request.newPassword)
        deps.throttle.success(ip, key)
        call.respond(HttpStatusCode.NoContent)
    }
}

package de.foody.server.auth

import de.foody.server.ApiException
import de.foody.server.ServerDeps
import de.foody.sync.protocol.AuthResponse
import de.foody.sync.protocol.DeviceDto
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.LoginRequest
import de.foody.sync.protocol.PasswordChangeRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

private const val MAX_DEVICE_NAME = 64

/** Öffentliche Auth-Routen (ohne Token); unterhalb von `/api/v1` einzuhängen. */
fun Route.publicAuthRoutes(deps: ServerDeps) {
    post("/auth/login") {
        val request = call.receive<LoginRequest>()
        val ip = call.request.origin.remoteHost
        deps.throttle.check(ip, request.username)
        val userId = deps.accounts.authenticate(request.username, request.password)
        if (userId == null) {
            deps.throttle.failure(ip, request.username)
            throw ApiException(HttpStatusCode.Unauthorized, ErrorCode.INVALID_CREDENTIALS)
        }
        deps.throttle.success(ip, request.username)
        val householdId = deps.accounts.soleHouseholdOf(userId)
        val token = deps.accounts.createDevice(userId, householdId, request.deviceName.take(MAX_DEVICE_NAME))
        call.respond(AuthResponse(token, userId, householdId))
    }
}

/** Auth-Routen mit Token; innerhalb von `authenticate("device")` einzuhängen. */
fun Route.deviceAuthRoutes(deps: ServerDeps) {
    post("/auth/password") {
        val device = call.principal<DevicePrincipal>()!!
        val request = call.receive<PasswordChangeRequest>()
        deps.accounts.changePassword(device.userId, device.deviceId, request.oldPassword, request.newPassword)
        call.respond(HttpStatusCode.NoContent)
    }
    // Minimal; Task 4 ergänzt Widerruf usw. und kann diese Route verschieben.
    get("/devices") {
        val device = call.principal<DevicePrincipal>()!!
        call.respond(
            deps.accounts.devicesOf(device.userId).map {
                DeviceDto(it.id, it.name, it.lastSeenAt, current = it.id == device.deviceId)
            },
        )
    }
}

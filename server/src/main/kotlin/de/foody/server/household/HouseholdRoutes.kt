package de.foody.server.household

import de.foody.server.ApiException
import de.foody.server.ServerDeps
import de.foody.server.auth.DevicePrincipal
import de.foody.server.receiveJson
import de.foody.sync.protocol.CreateHouseholdRequest
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.SelectHouseholdRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** Haushalts-, Einladungs-, Geräte- und Mitgliederrouten; innerhalb von `authenticate("device")` einzuhängen. */
fun Route.householdRoutes(deps: ServerDeps) {
    get("/households") {
        val device = call.principal<DevicePrincipal>()!!
        call.respond(deps.accounts.households(device.userId))
    }
    post("/households") {
        val device = call.principal<DevicePrincipal>()!!
        val request = call.receiveJson(CreateHouseholdRequest.serializer())
        call.respond(HttpStatusCode.Created, deps.accounts.createHousehold(device.userId, device.deviceId, request.name))
    }
    post("/device/household") {
        val device = call.principal<DevicePrincipal>()!!
        val request = call.receiveJson(SelectHouseholdRequest.serializer())
        deps.accounts.selectHousehold(device.deviceId, device.userId, request.householdId)
        call.respond(HttpStatusCode.NoContent)
    }
    post("/invites") {
        val device = call.principal<DevicePrincipal>()!!
        val householdId = device.householdId ?: throw ApiException(HttpStatusCode.Conflict, ErrorCode.NO_HOUSEHOLD)
        call.respond(HttpStatusCode.Created, deps.accounts.createInvite(device.userId, householdId))
    }
    get("/devices") {
        val device = call.principal<DevicePrincipal>()!!
        call.respond(deps.accounts.devices(device.userId, device.deviceId))
    }
    delete("/devices/{id}") {
        val device = call.principal<DevicePrincipal>()!!
        deps.accounts.revokeDevice(device.userId, call.parameters["id"].orEmpty())
        call.respond(HttpStatusCode.NoContent)
    }
    delete("/households/{id}/members/{userId}") {
        val device = call.principal<DevicePrincipal>()!!
        deps.accounts.removeMember(device.userId, call.parameters["id"].orEmpty(), call.parameters["userId"].orEmpty())
        call.respond(HttpStatusCode.NoContent)
    }
}

package de.foody.server.ki

import de.foody.api.protocol.KiStatus
import de.foody.server.ServerDeps
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** KI-Endpunkte; innerhalb von `authenticate("device")` einzuhängen. Phase 2: nur der Status, kein Modellaufruf. */
fun Route.kiRoutes(deps: ServerDeps) {
    get("/ki/status") { call.respond(KiStatus(eingerichtet = deps.config.kiKey != null)) }
}

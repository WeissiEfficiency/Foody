package de.foody.server.web

import de.foody.server.ServerDeps
import io.ktor.server.routing.Route

/**
 * Web-Oberfläche des Servers: die Routen, die `:server` (server/start) in `foodyModule` einhängt.
 * Seiten folgen in den nächsten Schritten von Teil 1.
 */
object WebOberflaeche {
    fun routen(deps: ServerDeps): List<Route.() -> Unit> = emptyList()
}

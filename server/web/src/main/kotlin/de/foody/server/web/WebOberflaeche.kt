package de.foody.server.web

import io.ktor.server.routing.Route

/**
 * Web-Oberfläche des Servers: die Routen, die `:server` (server/start) in `foodyModule` einhängt.
 * Noch leer – die Seiten folgen mit Teil 1 der Web-Oberfläche.
 */
object WebOberflaeche {
    val routen: List<Route.() -> Unit> = emptyList()
}

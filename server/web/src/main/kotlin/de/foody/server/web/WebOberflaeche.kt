package de.foody.server.web

import de.foody.server.ServerDeps
import de.foody.server.web.Statisch.liefere
import de.foody.server.web.seiten.HaushaltSeite
import de.foody.server.web.seiten.LoginSeite
import de.foody.server.web.seiten.RezeptSeiten
import io.ktor.http.ContentType
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * Web-Oberfläche des Servers: die Routen, die `:server` (server/start) in `foodyModule` einhängt
 * (außerhalb von `/api/v1`). Lesen über [HaushaltsDaten], Schreiben nur über `SyncService.push`.
 */
object WebOberflaeche {
    fun routen(deps: ServerDeps): List<Route.() -> Unit> {
        val daten = HaushaltsDaten(deps.db)
        return listOf(
            { statisch() },
            { anmeldung(deps) },
            { seiten(deps, daten) },
        )
    }

    private fun Route.statisch() {
        get("/web/foody.css") { call.liefere(Statisch.css, ContentType.Text.CSS) }
        get("/web/htmx.min.js") { call.liefere(Statisch.htmx, ContentType.Application.JavaScript) }
    }

    private fun Route.anmeldung(deps: ServerDeps) {
        get("/login") { LoginSeite.zeigen(call) }
        post("/login") { LoginSeite.anmelden(call, deps) }
        get("/registrieren") { LoginSeite.registrierenZeigen(call) }
        post("/registrieren") { LoginSeite.registrieren(call, deps) }
        post("/logout") { LoginSeite.abmelden(call, deps) }
        get("/haushalt") { HaushaltSeite.zeigen(call, deps) }
        post("/haushalt") { HaushaltSeite.waehlen(call, deps) }
        post("/haushalt/neu") { HaushaltSeite.anlegen(call, deps) }
    }

    private fun Route.seiten(deps: ServerDeps, daten: HaushaltsDaten) {
        get("/") {
            val s = call.sitzungMitHaushalt(deps) ?: return@get
            RezeptSeiten.liste(call, s, daten.fuer(s.haushalt!!))
        }
        get("/rezepte/liste") {
            val s = call.sitzungMitHaushalt(deps) ?: return@get
            RezeptSeiten.listenTeil(call, daten.fuer(s.haushalt!!))
        }
        get("/rezept/{id}") {
            val s = call.sitzungMitHaushalt(deps) ?: return@get
            RezeptSeiten.rezept(call, s, daten.fuer(s.haushalt!!), call.parameters["id"].orEmpty())
        }
    }
}

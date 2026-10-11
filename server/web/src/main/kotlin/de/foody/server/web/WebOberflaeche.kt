package de.foody.server.web

import de.foody.server.ServerDeps
import de.foody.server.web.Statisch.liefere
import de.foody.server.web.seiten.EditorSeite
import de.foody.server.web.seiten.EinkaufSeite
import de.foody.server.web.seiten.HaushaltSeite
import de.foody.server.web.seiten.LoginSeite
import de.foody.server.web.seiten.PlanTagebuchSeiten
import de.foody.server.web.seiten.RezeptSeiten
import io.ktor.http.ContentType
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.LocalDate

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
        webGet("/login") { LoginSeite.zeigen(call) }
        webPost("/login") { LoginSeite.anmelden(call, deps) }
        webGet("/registrieren") { LoginSeite.registrierenZeigen(call) }
        webPost("/registrieren") { LoginSeite.registrieren(call, deps) }
        webPost("/logout") { LoginSeite.abmelden(call, deps) }
        webGet("/haushalt") { HaushaltSeite.zeigen(call, deps) }
        webPost("/haushalt") { HaushaltSeite.waehlen(call, deps) }
        webPost("/haushalt/neu") { HaushaltSeite.anlegen(call, deps) }
    }

    private fun Route.seiten(deps: ServerDeps, daten: HaushaltsDaten) {
        webGet("/") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            RezeptSeiten.liste(call, s, daten.fuer(s.haushalt!!))
        }
        webGet("/rezepte/liste") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            RezeptSeiten.listenTeil(call, daten.fuer(s.haushalt!!))
        }
        webGet("/plan") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            PlanTagebuchSeiten.plan(call, s, daten.fuer(s.haushalt!!), LocalDate.now(deps.clock))
        }
        webGet("/tagebuch") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            PlanTagebuchSeiten.tagebuch(call, s, daten.fuer(s.haushalt!!), LocalDate.now(deps.clock))
        }
        webGet("/einkauf") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            EinkaufSeite.zeigen(call, s, daten.fuer(s.haushalt!!))
        }
        webPost("/einkauf/{id}/abhaken") { EinkaufSeite.abhaken(call, deps, daten, call.parameters["id"].orEmpty()) }
        webGet("/rezept/neu") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            EditorSeite.neu(call, s)
        }
        webGet("/rezept/zeile") {
            call.sitzungMitHaushalt(deps) ?: return@webGet
            EditorSeite.zeile(call)
        }
        webGet("/rezept/zutatenliste") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            EditorSeite.zutatenliste(call, daten.fuer(s.haushalt!!))
        }
        webPost("/rezept/speichern") { EditorSeite.speichern(call, deps, daten) }
        webGet("/rezept/{id}/bearbeiten") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            EditorSeite.bearbeiten(call, s, daten.fuer(s.haushalt!!), call.parameters["id"].orEmpty())
        }
        webPost("/rezept/{id}/archivieren") { EditorSeite.archivieren(call, deps, daten, call.parameters["id"].orEmpty(), archiviert = true) }
        webPost("/rezept/{id}/wiederherstellen") { EditorSeite.archivieren(call, deps, daten, call.parameters["id"].orEmpty(), archiviert = false) }
        webGet("/rezept/{id}") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            RezeptSeiten.rezept(call, s, daten.fuer(s.haushalt!!), call.parameters["id"].orEmpty())
        }
    }
}

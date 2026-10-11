package de.foody.server.web

import de.foody.server.ServerDeps
import de.foody.server.web.Statisch.liefere
import de.foody.server.web.seiten.EditorSeite
import de.foody.server.web.seiten.EinkaufSeite
import de.foody.server.web.seiten.HaushaltSeite
import de.foody.server.web.seiten.ImportSeite
import de.foody.server.web.seiten.ListeAusPlanSeiten
import de.foody.server.web.seiten.LoginSeite
import de.foody.server.web.seiten.PlanSeiten
import de.foody.server.web.seiten.PlanTagebuchSeiten
import de.foody.server.web.seiten.RezeptSeiten
import de.foody.server.web.seiten.VorratSeiten
import io.ktor.http.ContentType
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.ZoneId

/**
 * Web-Oberfläche des Servers: die Routen, die `:server` (server/start) in `foodyModule` einhängt
 * (außerhalb von `/api/v1`). Lesen über [HaushaltsDaten], Schreiben nur über `SyncService.push`.
 */
object WebOberflaeche {
    fun routen(deps: ServerDeps, zone: ZoneId = standardZone()): List<Route.() -> Unit> {
        val daten = HaushaltsDaten(deps.db)
        return listOf(
            { statisch() },
            { anmeldung(deps) },
            { seiten(deps, daten, zone) },
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

    private fun Route.seiten(deps: ServerDeps, daten: HaushaltsDaten, zone: ZoneId) {
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
            PlanTagebuchSeiten.plan(call, s, daten.fuer(s.haushalt!!), deps.heute(zone))
        }
        webPost("/plan/aktion") { PlanSeiten.aktion(call, deps, daten, deps.heute(zone), pfadId = null) }
        webPost("/plan/einplanen") { PlanSeiten.einplanen(call, deps, daten, deps.heute(zone)) }
        webGet("/plan/neu") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            PlanSeiten.neu(call, s, daten.fuer(s.haushalt!!), deps.heute(zone))
        }
        webGet("/plan/neu/liste") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            PlanSeiten.neuListe(call, daten.fuer(s.haushalt!!), deps.heute(zone))
        }
        webGet("/plan/vorschlag") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            PlanSeiten.vorschlag(call, deps, s, daten.fuer(s.haushalt!!), deps.heute(zone))
        }
        webPost("/plan/vorschlag") { PlanSeiten.vorschlagUebernehmen(call, deps, daten, deps.heute(zone)) }
        webPost("/plan/{id}") { PlanSeiten.aktion(call, deps, daten, deps.heute(zone), call.parameters["id"].orEmpty()) }
        webGet("/vorrat") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            VorratSeiten.liste(call, s, daten.fuer(s.haushalt!!), deps.heute(zone))
        }
        webPost("/vorrat") { VorratSeiten.anlegen(call, deps, daten, deps.heute(zone)) }
        webGet("/vorrat/{id}") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            VorratSeiten.eintrag(call, s, daten.fuer(s.haushalt!!), call.parameters["id"].orEmpty())
        }
        webPost("/vorrat/{id}") { VorratSeiten.aendern(call, deps, daten, deps.heute(zone), call.parameters["id"].orEmpty()) }
        webPost("/vorrat/{id}/loeschen") { VorratSeiten.loeschen(call, deps, daten, call.parameters["id"].orEmpty()) }
        webGet("/tagebuch") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            PlanTagebuchSeiten.tagebuch(call, s, daten.fuer(s.haushalt!!), deps.heute(zone))
        }
        webGet("/einkauf") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            EinkaufSeite.zeigen(call, s, daten.fuer(s.haushalt!!))
        }
        webGet("/einkauf/neu") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            ListeAusPlanSeiten.neu(call, deps, s, daten, deps.heute(zone))
        }
        webPost("/einkauf/neu") { ListeAusPlanSeiten.anlegen(call, deps, daten, deps.heute(zone)) }
        webGet("/einkauf/{liste}/abgleich") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            ListeAusPlanSeiten.abgleich(call, deps, s, daten, call.parameters["liste"].orEmpty())
        }
        webPost("/einkauf/{liste}/abgleich") { ListeAusPlanSeiten.abgleichUebernehmen(call, deps, daten, call.parameters["liste"].orEmpty()) }
        webPost("/einkauf/abhaken") { EinkaufSeite.abhaken(call, deps, daten, pfadId = null) }
        webPost("/einkauf/{id}/abhaken") { EinkaufSeite.abhaken(call, deps, daten, call.parameters["id"].orEmpty()) }
        webPost("/einkauf/eingabe") { EinkaufSeite.eingabe(call, deps, daten) }
        webPost("/einkauf/rezept") { EinkaufSeite.rezeptAufListe(call, deps, daten, deps.heute(zone)) }
        webPost("/einkauf/{liste}/bearbeiten") { EinkaufSeite.bearbeitenSpeichern(call, deps, daten, call.parameters["liste"].orEmpty()) }
        webGet("/einkauf/{liste}/loeschen") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            EinkaufSeite.loeschenBestaetigen(call, s, daten.fuer(s.haushalt!!), call.parameters["liste"].orEmpty())
        }
        webPost("/einkauf/{liste}/loeschen") { EinkaufSeite.listeLoeschen(call, deps, daten, call.parameters["liste"].orEmpty()) }
        webGet("/import") {
            val s = call.sitzungMitHaushalt(deps) ?: return@webGet
            ImportSeite.zeigen(call, s)
        }
        webPost("/import") { ImportSeite.importieren(call, deps, daten) }
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
            RezeptSeiten.rezept(call, s, daten.fuer(s.haushalt!!), call.parameters["id"].orEmpty(), deps.heute(zone))
        }
    }
}

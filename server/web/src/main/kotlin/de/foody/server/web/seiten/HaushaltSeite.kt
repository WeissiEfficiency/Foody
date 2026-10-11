package de.foody.server.web.seiten

import de.foody.server.ApiException
import de.foody.server.ServerDeps
import de.foody.server.web.csrfAus
import de.foody.server.web.csrfGueltig
import de.foody.server.web.formular
import de.foody.server.web.seiteAntworten
import de.foody.server.web.sitzungOderLogin
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.hiddenInput
import kotlinx.html.input
import kotlinx.html.p

/** Haushalt wählen (bei mehreren) oder anlegen (bei keinem). */
internal object HaushaltSeite {
    suspend fun zeigen(call: ApplicationCall, deps: ServerDeps, fehler: String? = null) {
        val s = call.sitzungOderLogin(deps) ?: return
        val haushalte = deps.accounts.households(s.principal.userId)
        call.seiteAntworten("Haushalt", s, null) {
            h1 { +"Haushalt wählen" }
            if (fehler != null) p("fehler") { +fehler }
            if (haushalte.isEmpty()) {
                p("hinweis") { +"Du gehörst noch zu keinem Haushalt. Lege einen an oder lass dich in der App einladen." }
            }
            for (h in haushalte) {
                div("karte") {
                    form(action = "/haushalt", method = FormMethod.post, classes = "zeile") {
                        hiddenInput(name = "_csrf") { value = s.csrf }
                        hiddenInput(name = "haushalt") { value = h.id }
                        button { +h.name }
                        if (h.id == s.haushalt) p("leise") { +"aktuell" }
                    }
                }
            }
            h2 { +"Neuer Haushalt" }
            form(action = "/haushalt/neu", method = FormMethod.post, classes = "zeile") {
                hiddenInput(name = "_csrf") { value = s.csrf }
                input(InputType.text, name = "name") { required = true; placeholder = "Name des Haushalts" }
                button { +"Anlegen" }
            }
        }
    }

    suspend fun waehlen(call: ApplicationCall, deps: ServerDeps) {
        val form = call.formular()
        val s = call.sitzungOderLogin(deps) ?: return
        if (!s.csrfGueltig(call.csrfAus(form))) return call.respond(HttpStatusCode.Forbidden)
        val id = form["haushalt"].orEmpty()
        try {
            deps.accounts.selectHousehold(s.principal.deviceId, s.principal.userId, id)
        } catch (_: ApiException) {
            return zeigen(call, deps, "Diesen Haushalt gibt es für dich nicht.")
        }
        call.respondRedirect("/")
    }

    suspend fun anlegen(call: ApplicationCall, deps: ServerDeps) {
        val form = call.formular()
        val s = call.sitzungOderLogin(deps) ?: return
        if (!s.csrfGueltig(call.csrfAus(form))) return call.respond(HttpStatusCode.Forbidden)
        try {
            deps.accounts.createHousehold(s.principal.userId, s.principal.deviceId, form["name"].orEmpty())
        } catch (_: ApiException) {
            return zeigen(call, deps, "Name fehlt oder ist zu lang.")
        }
        call.respondRedirect("/")
    }
}

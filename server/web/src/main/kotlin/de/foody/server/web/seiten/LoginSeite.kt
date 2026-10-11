package de.foody.server.web.seiten

import de.foody.server.ApiException
import de.foody.server.ServerDeps
import de.foody.server.web.csrfAus
import de.foody.server.web.csrfGueltig
import de.foody.server.web.formular
import de.foody.server.web.geraeteName
import de.foody.server.web.gleicheWebsite
import de.foody.server.web.loescheSitzung
import de.foody.server.web.seiteAntworten
import de.foody.server.web.setzeSitzung
import de.foody.server.web.sitzung
import de.foody.server.web.userAgent
import de.foody.sync.protocol.ErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p

private const val MAX_BENUTZER = 32
private const val MAX_PASSWORT = 200
private const val MAX_CODE = 32

/** Login, Registrierung mit Einladungscode und Abmelden – Prüfung und Drosselung wie `/api/v1/auth/login` und `register`. */
internal object LoginSeite {
    suspend fun zeigen(call: ApplicationCall, fehler: String? = null, benutzer: String = "") =
        call.seiteAntworten("Anmelden", null, null) {
            div("anmeldung karte") {
                h1 { +"Foody" }
                if (fehler != null) p("fehler") { +fehler }
                else if (call.request.queryParameters["abgelaufen"] == "1") p("hinweis") { +"Bitte erneut anmelden." }
                form(action = "/login", method = FormMethod.post) {
                    label { attributes["for"] = "benutzer"; +"Benutzername" }
                    input(InputType.text, name = "benutzer") { id = "benutzer"; value = benutzer; required = true; attributes["autocomplete"] = "username" }
                    label { attributes["for"] = "passwort"; +"Passwort" }
                    input(InputType.password, name = "passwort") { id = "passwort"; required = true; attributes["autocomplete"] = "current-password" }
                    button { +"Anmelden" }
                }
                p("leise") { a(href = "/registrieren") { +"Mit Einladungscode registrieren" } }
            }
        }

    suspend fun anmelden(call: ApplicationCall, deps: ServerDeps) {
        // Login-CSRF: eine fremde Website darf den Browser nicht mit einem fremden Konto anmelden
        if (!call.gleicheWebsite()) return call.respond(HttpStatusCode.Forbidden)
        val form = call.formular()
        val benutzer = form["benutzer"].orEmpty().trim()
        val passwort = form["passwort"].orEmpty()
        val falsch = "Benutzername oder Passwort falsch."
        if (benutzer.length > MAX_BENUTZER || passwort.length > MAX_PASSWORT) return zeigen(call, falsch, "")
        val ip = call.request.origin.remoteHost
        try {
            deps.throttle.check(ip, benutzer)
        } catch (_: ApiException) {
            return zeigen(call, "Zu viele Versuche. Bitte in 15 Minuten erneut versuchen.", benutzer)
        }
        val userId = deps.accounts.authenticate(benutzer, passwort) ?: return zeigen(call, falsch, benutzer)
        deps.throttle.success(ip, benutzer)
        // Erneute Anmeldung im selben Browser: das bisherige Browser-Gerät widerrufen (keine Karteileichen,
        // ein alter Cookie gilt danach nicht mehr)
        call.sitzung(deps)?.let { alt -> runCatching { deps.accounts.revokeDevice(alt.principal.userId, alt.principal.deviceId) } }
        val haushalt = deps.accounts.soleHouseholdOf(userId)
        val token = deps.accounts.createDevice(userId, haushalt, geraeteName(call.userAgent()))
        call.setzeSitzung(token)
        call.respondRedirect(if (haushalt == null) "/haushalt" else "/")
    }

    suspend fun registrierenZeigen(call: ApplicationCall, fehler: String? = null) =
        call.seiteAntworten("Registrieren", null, null) {
            div("anmeldung karte") {
                h1 { +"Registrieren" }
                if (fehler != null) p("fehler") { +fehler }
                form(action = "/registrieren", method = FormMethod.post) {
                    label { attributes["for"] = "code"; +"Einladungscode" }
                    input(InputType.text, name = "code") { id = "code"; required = true }
                    label { attributes["for"] = "benutzer"; +"Benutzername" }
                    input(InputType.text, name = "benutzer") { id = "benutzer"; required = true; attributes["autocomplete"] = "username" }
                    label { attributes["for"] = "passwort"; +"Passwort (mindestens 10 Zeichen)" }
                    input(InputType.password, name = "passwort") { id = "passwort"; required = true; attributes["autocomplete"] = "new-password" }
                    button { +"Registrieren" }
                }
                p("leise") { a(href = "/login") { +"Zur Anmeldung" } }
            }
        }

    suspend fun registrieren(call: ApplicationCall, deps: ServerDeps) {
        if (!call.gleicheWebsite()) return call.respond(HttpStatusCode.Forbidden)
        val form = call.formular()
        val code = form["code"].orEmpty().trim()
        val benutzer = form["benutzer"].orEmpty().trim()
        val passwort = form["passwort"].orEmpty()
        if (code.length > MAX_CODE || benutzer.length > MAX_BENUTZER || passwort.length > MAX_PASSWORT) {
            return registrierenZeigen(call, "Eingaben zu lang.")
        }
        val ip = call.request.origin.remoteHost
        val auth = try {
            // Wie /auth/register: Versuche je IP drosseln (Einladungscodes haben nur 40 Bit)
            deps.throttle.check(ip, "invite:register")
            deps.accounts.redeemInvite(code, benutzer, passwort, geraeteName(call.userAgent()))
        } catch (e: ApiException) {
            return registrierenZeigen(call, when (e.code) {
                ErrorCode.THROTTLED -> "Zu viele Versuche. Bitte später erneut versuchen."
                ErrorCode.INVALID_INVITE -> "Einladungscode ungültig oder abgelaufen."
                ErrorCode.USERNAME_TAKEN -> "Dieser Benutzername ist schon vergeben."
                else -> "Passwort mindestens 10 Zeichen, Benutzername 3–32 Zeichen (Buchstaben, Ziffern, . _ -)."
            })
        }
        call.setzeSitzung(auth.token)
        call.respondRedirect(if (auth.householdId == null) "/haushalt" else "/")
    }

    suspend fun abmelden(call: ApplicationCall, deps: ServerDeps) {
        val s = call.sitzung(deps)
        if (s == null) {
            call.respondRedirect("/login")
            return
        }
        val form = call.formular()
        if (!s.csrfGueltig(call.csrfAus(form))) return call.respond(HttpStatusCode.Forbidden)
        deps.accounts.revokeDevice(s.principal.userId, s.principal.deviceId)
        call.loescheSitzung()
        call.respondRedirect("/login")
    }
}

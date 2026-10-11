package de.foody.server.web

import de.foody.server.ServerDeps
import de.foody.server.auth.DevicePrincipal
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal const val SITZUNGS_COOKIE = "foody_sitzung"
private const val MAX_ALTER_SEKUNDEN = 30 * 24 * 60 * 60
private const val CSRF_TEXT = "foody-web-csrf"

/** Angemeldetes Browser-Gerät samt Token (für das CSRF-Token der Seite). */
internal class Sitzung(val principal: DevicePrincipal, val token: String) {
    val csrf: String get() = csrfToken(token)
    val haushalt: String? get() = principal.householdId
}

/** CSRF-Token = Hex(HMAC-SHA256(Schlüssel = Gerätetoken, Text = „foody-web-csrf“)); hängt an der Sitzung, nicht am Formular. */
internal fun csrfToken(geraeteToken: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(geraeteToken.toByteArray(), "HmacSHA256"))
    return mac.doFinal(CSRF_TEXT.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Vergleich in konstanter Zeit. */
internal fun Sitzung.csrfGueltig(gesendet: String?): Boolean =
    gesendet != null && MessageDigest.isEqual(gesendet.toByteArray(), csrf.toByteArray())

/** Sitzung aus dem Cookie, oder `null` (abgelaufen, widerrufen, fehlt). */
internal fun ApplicationCall.sitzung(deps: ServerDeps): Sitzung? {
    val token = request.cookies[SITZUNGS_COOKIE] ?: return null
    val principal = deps.accounts.deviceForToken(token) ?: return null
    return Sitzung(principal, token)
}

/** Sitzung oder Weiterleitung zum Login (dann `null`). */
internal suspend fun ApplicationCall.sitzungOderLogin(deps: ServerDeps): Sitzung? =
    sitzung(deps) ?: run { respondRedirect("/login"); null }

/** Sitzung mit gewähltem Haushalt; sonst Weiterleitung zu Login bzw. Haushaltswahl (dann `null`). */
internal suspend fun ApplicationCall.sitzungMitHaushalt(deps: ServerDeps): Sitzung? {
    val s = sitzungOderLogin(deps) ?: return null
    if (s.haushalt == null) {
        respondRedirect("/haushalt")
        return null
    }
    return s
}

/** Formularfelder, wenn die Anfrage ein Formular ist; sonst leer (statt 415 – so greift die CSRF-Prüfung). */
internal suspend fun ApplicationCall.formular(): Parameters =
    if (request.contentType().match(ContentType.Application.FormUrlEncoded)) receiveParameters() else Parameters.Empty

/** CSRF aus Formularfeld `_csrf` oder Header `X-Foody-CSRF` (htmx). */
internal fun ApplicationCall.csrfAus(form: Parameters?): String? =
    form?.get("_csrf") ?: request.headers["X-Foody-CSRF"]

internal fun ApplicationCall.setzeSitzung(token: String) {
    response.cookies.append(
        Cookie(
            name = SITZUNGS_COOKIE, value = token, encoding = CookieEncoding.RAW, maxAge = MAX_ALTER_SEKUNDEN, path = "/",
            secure = true, httpOnly = true, extensions = mapOf("SameSite" to "Strict"),
        ),
    )
}

internal fun ApplicationCall.loescheSitzung() {
    response.cookies.append(
        Cookie(
            name = SITZUNGS_COOKIE, value = "", encoding = CookieEncoding.RAW, maxAge = 0, path = "/",
            secure = true, httpOnly = true, extensions = mapOf("SameSite" to "Strict"),
        ),
    )
}

/** „Browser (Firefox)“ usw. für die Geräteliste der App. */
internal fun geraeteName(userAgent: String?): String {
    val ua = userAgent.orEmpty()
    val familie = when {
        "Edg/" in ua -> "Edge"
        "Firefox/" in ua -> "Firefox"
        "Chrome/" in ua || "Chromium/" in ua -> "Chrome"
        "Safari/" in ua -> "Safari"
        else -> null
    }
    return if (familie == null) "Browser" else "Browser ($familie)"
}

internal fun ApplicationCall.userAgent(): String? = request.headers[HttpHeaders.UserAgent]

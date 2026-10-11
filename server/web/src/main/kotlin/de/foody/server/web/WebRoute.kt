package de.foody.server.web

import de.foody.server.ApiException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.html.h1
import kotlinx.html.p

/**
 * Gemeinsamer Rahmen aller Web-Routen: Fehler erscheinen als Seite im Foody-Layout statt als leere Antwort oder als
 * JSON der API. Unerwartete Ausnahmen zeigen keine Details; protokolliert wird wie bisher.
 */
internal fun Route.webGet(pfad: String, block: suspend RoutingContext.() -> Unit) = get(pfad) { rahmen(block) }

internal fun Route.webPost(pfad: String, block: suspend RoutingContext.() -> Unit) = post(pfad) { rahmen(block) }

private suspend fun RoutingContext.rahmen(block: suspend RoutingContext.() -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        val titel = when (e.status) {
            HttpStatusCode.NotFound -> "Nicht gefunden"
            HttpStatusCode.Forbidden -> "Keine Berechtigung"
            HttpStatusCode.TooManyRequests -> "Zu viele Versuche"
            HttpStatusCode.PayloadTooLarge -> "Zu groß"
            else -> "Das ging nicht"
        }
        call.seiteAntworten(titel, null, null, e.status) {
            h1 { +titel }
            p { +"Bitte zurückgehen und es erneut versuchen." }
        }
    } catch (e: Exception) {
        call.application.log.error("Fehler in der Web-Oberfläche", e)
        call.seiteAntworten("Fehler", null, null, HttpStatusCode.InternalServerError) {
            h1 { +"Da ist etwas schiefgegangen." }
            p { +"Bitte die Seite neu laden. Wenn es wieder passiert, hilft ein Blick ins Server-Protokoll." }
        }
    }
}

/**
 * Kommt die Anfrage von derselben Website? Schützt Login und Registrierung davor, dass eine fremde Seite den Browser
 * mit einem fremden Konto anmeldet. Abgelehnt, wenn `Sec-Fetch-Site` weder `same-origin` noch `none` ist oder `Origin`
 * nicht zum `Host` passt; ohne beide Header (ältere Browser) angenommen.
 */
internal fun ApplicationCall.gleicheWebsite(): Boolean {
    request.headers["Sec-Fetch-Site"]?.let { if (it != "same-origin" && it != "none") return false }
    val origin = request.headers[HttpHeaders.Origin] ?: return true
    val host = request.headers[HttpHeaders.Host] ?: return false
    val url = try {
        Url(origin)
    } catch (_: Exception) {
        return false
    }
    val autoritaet = if (url.specifiedPort == 0 || url.specifiedPort == url.protocol.defaultPort) url.host else "${url.host}:${url.port}"
    return autoritaet.equals(host, ignoreCase = true) || url.host.equals(host, ignoreCase = true)
}

package de.foody.server.web

import de.foody.server.ApiException
import de.foody.sync.protocol.ErrorCode
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Gemeinsamer Rahmen der Web-Routen und Schutz von Login/Registrierung vor fremden Websites. */
class WebRahmenTest {
    @Test fun loginVonFremderWebsiteIst403() = webServer { env ->
        val (benutzer, _, _) = env.haushalt()
        val c = ohneWeiterleitung()
        val formular = parameters { append("benutzer", benutzer); append("passwort", "geheimgeheim") }
        assertEquals(HttpStatusCode.Forbidden, c.submitForm("/login", formular) { header("Sec-Fetch-Site", "cross-site") }.status)
        assertEquals(HttpStatusCode.Forbidden, c.submitForm("/login", formular) { header(HttpHeaders.Origin, "https://boese.example") }.status)
        assertEquals(HttpStatusCode.Found, c.submitForm("/login", formular) { header("Sec-Fetch-Site", "same-origin") }.status)
        assertEquals(HttpStatusCode.Found, c.submitForm("/login", formular).status, "ohne beide Header wie bisher")
    }

    @Test fun registrierenVonFremderWebsiteIst403() = webServer {
        val antwort = ohneWeiterleitung().submitForm("/registrieren", parameters {
            append("code", "FOODY-AAAA-BBBB"); append("benutzer", "x"); append("passwort", "y")
        }) { header("Sec-Fetch-Site", "cross-site") }
        assertEquals(HttpStatusCode.Forbidden, antwort.status)
    }

    @Test fun fehlerZeigtLayoutOhneDetails() = testApplication {
        routing { webGet("/kaputt") { error("geheim") } }
        val antwort = client.get("/kaputt")
        assertEquals(HttpStatusCode.InternalServerError, antwort.status)
        val html = antwort.bodyAsText()
        assertTrue("Da ist etwas schiefgegangen." in html)
        assertFalse("geheim" in html)
    }

    @Test fun apiExceptionWirdMeldung() = testApplication {
        routing { webPost("/weg") { throw ApiException(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND) } }
        val antwort = client.submitForm("/weg", parameters { })
        assertEquals(HttpStatusCode.NotFound, antwort.status)
        val html = antwort.bodyAsText()
        assertTrue("<html" in html && "Nicht gefunden" in html, html)
        assertFalse("\"code\"" in html, "kein JSON")
    }

    @Test fun navigationHatVorrat() = webServer { env ->
        val (benutzer, _, _) = env.haushalt()
        val c = ohneWeiterleitung()
        val html = c.get("/") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText()
        assertTrue(html.indexOf("href=\"/einkauf\"") < html.indexOf("href=\"/vorrat\"") && html.indexOf("href=\"/vorrat\"") < html.indexOf("href=\"/tagebuch\""))
    }
}

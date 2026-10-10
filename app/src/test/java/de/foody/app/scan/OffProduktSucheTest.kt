package de.foody.app.scan

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OffProduktSucheTest {
    private var userAgent: String? = null
    private var pfad: String? = null

    private fun suche(antwort: suspend io.ktor.client.engine.mock.MockRequestHandleScope.() -> io.ktor.client.request.HttpResponseData) =
        OffProduktSuche(HttpClient(MockEngine { req -> userAgent = req.headers[HttpHeaders.UserAgent]; pfad = req.url.encodedPath; antwort() }), "1.2.3")

    @Test fun gefunden() = runTest {
        val a = suche {
            respond("""{"status":1,"product":{"product_name":"Joghurt","nutriments":{"energy-kj_100g":276}}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }.suche("4006040002031")
        assertEquals("Joghurt", assertIs<ProduktSuche.Antwort.Gefunden>(a).packung.name)
        assertEquals("/api/v2/product/4006040002031.json", pfad)
        assertTrue(userAgent!!.startsWith("Foody/1.2.3"), userAgent)
    }

    @Test fun unbekannt() = runTest {
        assertEquals(ProduktSuche.Antwort.Unbekannt, suche { respond("""{"status":0}""", HttpStatusCode.NotFound) }.suche("1"))
    }

    @Test fun serverfehlerUndOfflineGeltenAlsOffline() = runTest {
        assertEquals(ProduktSuche.Antwort.Offline, suche { respondError(HttpStatusCode.ServiceUnavailable) }.suche("1"))
        assertEquals(ProduktSuche.Antwort.Offline, suche { throw IOException("kein Netz") }.suche("1"))
    }
}

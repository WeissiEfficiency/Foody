package de.foody.server

import de.foody.server.ki.KiKontingent
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// KI-Gerüst: Kontingent je Konto, Status-Endpunkt, Konfiguration. Kein Modellaufruf.
class KiTest {
    @Test
    fun kontingentBegrenzt() {
        val ki = KiKontingent(MutableClock(), proStunde = 2)
        assertTrue(ki.versuche("k"))
        assertTrue(ki.versuche("k"))
        assertFalse(ki.versuche("k"))
    }

    @Test
    fun kontingentJeKonto() {
        val ki = KiKontingent(MutableClock(), proStunde = 2)
        ki.versuche("k1")
        ki.versuche("k1")
        assertFalse(ki.versuche("k1"))
        assertTrue(ki.versuche("k2"))
    }

    @Test
    fun kontingentErneuertSich() {
        val clock = MutableClock()
        val ki = KiKontingent(clock, proStunde = 2)
        ki.versuche("k")
        clock.advance(Duration.ofMinutes(30))
        ki.versuche("k")
        assertFalse(ki.versuche("k"))
        // Gleitendes Fenster: nach 61 Minuten ist nur der erste Versuch herausgefallen.
        clock.advance(Duration.ofMinutes(31))
        assertTrue(ki.versuche("k"))
        assertFalse(ki.versuche("k"))
    }

    @Test
    fun abgelehnterVersuchVerbrauchtNichts() {
        val clock = MutableClock()
        val ki = KiKontingent(clock, proStunde = 1)
        ki.versuche("k")
        clock.advance(Duration.ofMinutes(59))
        assertFalse(ki.versuche("k"))
        clock.advance(Duration.ofMinutes(2))
        assertTrue(ki.versuche("k"))
    }

    @Test
    fun statusOhneSchluessel() = testServer { env ->
        val (token, _) = env.setupKonto()
        val response = client.get("/api/v3/ki/status") { protocol(); bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"eingerichtet":false}""", response.bodyAsText())
    }

    @Test
    fun statusMitSchluessel() = testServer(anpassen = { it.copy(kiKey = "x") }) { env ->
        val (token, _) = env.setupKonto()
        val response = client.get("/api/v3/ki/status") { protocol(); bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"eingerichtet":true}""", response.bodyAsText())
    }

    @Test
    fun statusBrauchtAnmeldung() = testServer {
        val response = client.get("/api/v3/ki/status") { protocol() }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun konfigurationAusUmgebung() {
        val gesetzt = ServerConfig.fromEnv(mapOf("FOODY_KI_KEY" to "geheim", "FOODY_KI_PRO_STUNDE" to "5"))
        assertEquals("geheim", gesetzt.kiKey)
        assertEquals(5, gesetzt.kiProStunde)
        val leer = ServerConfig.fromEnv(mapOf("FOODY_KI_KEY" to "   "))
        assertEquals(null, leer.kiKey)
        assertEquals(30, leer.kiProStunde)
        assertEquals(null, ServerConfig.fromEnv(emptyMap()).kiKey)
        for (falsch in listOf("abc", "0", "-3", "")) {
            assertEquals(30, ServerConfig.fromEnv(mapOf("FOODY_KI_PRO_STUNDE" to falsch)).kiProStunde, falsch)
        }
    }

    @Test
    fun schluesselNichtImToString() {
        val config = ServerConfig.fromEnv(mapOf("FOODY_KI_KEY" to "sk-streng-geheim", "FOODY_ADMIN_PASSWORD" to "pw-geheim"))
        val text = config.toString()
        assertFalse("sk-streng-geheim" in text, text)
        assertFalse("pw-geheim" in text, text)
    }
}

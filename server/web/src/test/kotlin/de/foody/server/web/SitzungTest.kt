package de.foody.server.web

import de.foody.sync.protocol.DeviceDto
import de.foody.sync.protocol.Protocol
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer

internal const val FIREFOX = "Mozilla/5.0 (Windows NT 10.0; rv:131.0) Gecko/20100101 Firefox/131.0"

internal fun ApplicationTestBuilder.ohneWeiterleitung(): HttpClient = createClient { followRedirects = false }

/** Meldet im Browser an; liefert den Cookie-Header `foody_sitzung=…` (Tests verwalten Cookies selbst). */
internal suspend fun HttpClient.anmelden(benutzer: String, passwort: String = "geheimgeheim"): String {
    val antwort = submitForm("/login", parameters { append("benutzer", benutzer); append("passwort", passwort) }) {
        header(HttpHeaders.UserAgent, FIREFOX)
    }
    assertEquals(HttpStatusCode.Found, antwort.status, antwort.bodyAsText())
    return sitzungsCookie(antwort)
}

internal fun sitzungsCookie(antwort: HttpResponse): String {
    val setCookie = assertNotNull(antwort.headers.getAll(HttpHeaders.SetCookie)?.firstOrNull { it.startsWith("foody_sitzung=") })
    return setCookie.substringBefore(';')
}

/** CSRF-Token aus dem Layout (`<meta name="csrf" content="…">`). */
internal suspend fun HttpClient.csrf(cookie: String, pfad: String = "/"): String {
    val html = get(pfad) { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
    return Regex("""name="csrf" content="([0-9a-f]+)"""").find(html)!!.groupValues[1]
}

class SitzungTest {
    @Test fun ohneCookieZumLogin() = webServer {
        val antwort = ohneWeiterleitung().get("/")
        assertEquals(HttpStatusCode.Found, antwort.status)
        assertEquals("/login", antwort.headers[HttpHeaders.Location])
    }

    @Test fun falschesPasswortZeigtFehlerUndDrosselt() = webServer { env ->
        val (benutzer, _, _) = env.haushalt()
        val client = ohneWeiterleitung()
        val erste = client.submitForm("/login", parameters { append("benutzer", benutzer); append("passwort", "falsch") })
        assertEquals(HttpStatusCode.OK, erste.status)
        assertTrue("Benutzername oder Passwort falsch" in erste.bodyAsText())
        repeat(5) { client.submitForm("/login", parameters { append("benutzer", benutzer); append("passwort", "falsch") }) }
        assertTrue("Zu viele Versuche" in client.submitForm("/login", parameters { append("benutzer", benutzer); append("passwort", "falsch") }).bodyAsText())
    }

    @Test fun anmeldungSetztSicheresCookie() = webServer { env ->
        val (benutzer, _, _) = env.haushalt()
        val antwort = ohneWeiterleitung().submitForm("/login", parameters { append("benutzer", benutzer); append("passwort", "geheimgeheim") })
        val setCookie = antwort.headers.getAll(HttpHeaders.SetCookie)!!.first { it.startsWith("foody_sitzung=") }
        for (teil in listOf("HttpOnly", "Secure", "SameSite=Strict", "Path=/", "Max-Age=2592000")) {
            assertTrue(teil.lowercase() in setCookie.lowercase(), "$teil fehlt in $setCookie")
        }
        assertEquals("/", antwort.headers[HttpHeaders.Location])
    }

    @Test fun browserErscheintAlsGeraet() = webServer { env ->
        val (benutzer, appToken, _) = env.haushalt()
        ohneWeiterleitung().anmelden(benutzer)
        val geraete = client.get("/api/v1/devices") {
            header(HttpHeaders.Authorization, "Bearer $appToken"); header(Protocol.HEADER, Protocol.VERSION.toString())
        }.bodyAsText().let { Protocol.json.decodeFromString(ListSerializer(DeviceDto.serializer()), it) }
        assertTrue(geraete.any { it.name == "Browser (Firefox)" }, geraete.toString())
    }

    @Test fun logoutWiderruft() = webServer { env ->
        val (benutzer, _, _) = env.haushalt()
        val client = ohneWeiterleitung()
        val cookie = client.anmelden(benutzer)
        val token = client.csrf(cookie)
        val logout = client.submitForm("/logout", parameters { append("_csrf", token) }) { header(HttpHeaders.Cookie, cookie) }
        assertEquals(HttpStatusCode.Found, logout.status)
        assertEquals("/login", client.get("/") { header(HttpHeaders.Cookie, cookie) }.headers[HttpHeaders.Location])
    }

    @Test fun widerrufenesGeraetMussNeuAnmelden() = webServer { env ->
        val (benutzer, _, _) = env.haushalt()
        val client = ohneWeiterleitung()
        val cookie = client.anmelden(benutzer)
        val principal = env.deps.accounts.deviceForToken(cookie.substringAfter('='))!!
        env.deps.accounts.revokeDevice(principal.userId, principal.deviceId)
        assertEquals("/login", client.get("/") { header(HttpHeaders.Cookie, cookie) }.headers[HttpHeaders.Location])
    }

    @Test fun postOhneCsrfIst403() = webServer { env ->
        val (benutzer, _, _) = env.haushalt()
        val client = ohneWeiterleitung()
        val cookie = client.anmelden(benutzer)
        assertEquals(HttpStatusCode.Forbidden, client.post("/logout") { header(HttpHeaders.Cookie, cookie) }.status)
        assertEquals(HttpStatusCode.Forbidden,
            client.submitForm("/logout", parameters { append("_csrf", "00ff") }) { header(HttpHeaders.Cookie, cookie) }.status)
    }

    @Test fun mehrereHaushalteFuehrenZurAuswahl() = webServer { env ->
        val (benutzer, appToken, _) = env.haushalt("Zuhause")
        val p = env.deps.accounts.deviceForToken(appToken)!!
        val zweiter = env.deps.accounts.createHousehold(p.userId, p.deviceId, "Ferienhaus")
        val client = ohneWeiterleitung()
        val antwort = client.submitForm("/login", parameters { append("benutzer", benutzer); append("passwort", "geheimgeheim") })
        assertEquals("/haushalt", antwort.headers[HttpHeaders.Location])
        val cookie = sitzungsCookie(antwort)
        val seite = client.get("/haushalt") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("Zuhause" in seite && "Ferienhaus" in seite)
        val token = Regex("""name="csrf" content="([0-9a-f]+)"""").find(seite)!!.groupValues[1]
        val wahl = client.submitForm("/haushalt", parameters { append("_csrf", token); append("haushalt", zweiter.id) }) {
            header(HttpHeaders.Cookie, cookie)
        }
        assertEquals("/", wahl.headers[HttpHeaders.Location])
        assertEquals(zweiter.id, env.deps.accounts.deviceForToken(cookie.substringAfter('='))!!.householdId)
    }

    @Test fun registrierenMitEinladung() = webServer { env ->
        val admin = env.deps.accounts.createUser("chef", "geheimgeheim", isAdmin = true)
        val code = env.deps.accounts.createInvite(createdBy = admin, householdId = null).code
        val antwort = ohneWeiterleitung().submitForm("/registrieren", parameters {
            append("code", code); append("benutzer", "neueperson"); append("passwort", "langespasswort")
        })
        assertEquals(HttpStatusCode.Found, antwort.status, antwort.bodyAsText())
        assertEquals("/haushalt", antwort.headers[HttpHeaders.Location])
        sitzungsCookie(antwort)
    }
}

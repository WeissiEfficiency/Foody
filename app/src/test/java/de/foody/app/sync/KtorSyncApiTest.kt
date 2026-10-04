package de.foody.app.sync

import de.foody.server.sync.Compactor
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.IngredientPayload
import de.foody.sync.protocol.LoginRequest
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.PushStatus
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.plugin
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

class KtorSyncApiTest {
    private fun ingredient(id: String, name: String = "Mehl") = SyncRecord(
        id = id, type = RecordType.INGREDIENT, updatedAt = 1,
        payload = Protocol.json.encodeToJsonElement(IngredientPayload(name = name)) as JsonObject,
    )

    private fun deletion(id: String) = SyncRecord(id, RecordType.INGREDIENT, deleted = true, updatedAt = 2)

    private fun api(client: HttpClient, base: String = "https://localhost", token: () -> String? = { null }) =
        KtorSyncApi(base, client, token)

    private suspend fun KtorSyncApi.adminLogin() = login(LoginRequest(TEST_ADMIN_USER, TEST_ADMIN_PASSWORD, "Test"))

    @Test
    fun loginSendsProtocolHeaderAndReturnsToken() = syncServerTest { _, _ ->
        // Ohne den Header lehnt der Server mit 409 ab – der Erfolg belegt ihn.
        val response = api(client).adminLogin()
        assertTrue(response.token.isNotBlank())
        assertEquals(null, response.householdId)
    }

    @Test
    fun pushAndPullRoundTrip() = syncServerTest { _, _ ->
        var token: String? = null
        val api = api(client) { token }
        token = api.adminLogin().token
        val household = api.createHousehold("Zuhause")
        assertEquals("Zuhause", household.name)
        assertEquals(listOf(household.id), api.households().map { it.id })

        val pushed = api.push(listOf(ingredient("i1")))
        assertEquals(PushStatus.ACCEPTED, pushed.results.single().status)

        val pulled = api.pull(0, 500)
        assertEquals(listOf("i1"), pulled.records.map { it.id })
        assertEquals(false, pulled.hasMore)
    }

    @Test
    fun accountCallsWork() = syncServerTest { _, _ ->
        var token: String? = null
        val api = api(client) { token }
        token = api.adminLogin().token
        val household = api.createHousehold("Zuhause")
        api.selectHousehold(household.id)
        assertTrue(api.createInvite().code.isNotBlank())
        val devices = api.devices()
        assertEquals(1, devices.size)
        assertTrue(devices.single().current)
    }

    @Test
    fun errorsMapToTypedExceptions() = syncServerTest { deps, clock ->
        // Ungültiges Token
        val bad = api(client) { "kein-gueltiges-token" }
        assertFailsWith<SyncApiException.Unauthorized> { bad.pull(0, 10) }

        // Falsche Zugangsdaten: der Server antwortet mit 401 und dem Code invalid_credentials
        val wrong = assertFailsWith<SyncApiException.Unauthorized> {
            api(client).login(LoginRequest(TEST_ADMIN_USER, "falsch-falsch", "Test"))
        }
        assertEquals(ErrorCode.INVALID_CREDENTIALS, wrong.code)

        // Gerät ohne Haushalt
        var token: String? = null
        val api = api(client) { token }
        token = api.adminLogin().token
        val noHousehold = assertFailsWith<SyncApiException.NoHousehold> { api.push(listOf(ingredient("i1"))) }
        assertEquals(ErrorCode.NO_HOUSEHOLD, noHousehold.code)

        // Cursor hinter der Kompaktierung
        api.createHousehold("Zuhause")
        api.push(listOf(ingredient("i1")))
        api.push(listOf(deletion("i1")))
        clock.advance(Duration.ofDays(91))
        assertEquals(1, Compactor(deps.db, clock).run())
        val expired = assertFailsWith<SyncApiException.CursorExpired> { api.pull(1, 10) }
        assertEquals(410, expired.status)
        assertEquals(ErrorCode.CURSOR_EXPIRED, expired.code)
    }

    @Test
    fun networkFailuresAreTransient() = runBlocking<Unit> {
        // Nichts lauscht auf Port 1: Verbindungsfehler (IOException) -> Transient
        val client = HttpClient(OkHttp)
        try {
            assertFailsWith<SyncApiException.Transient> { KtorSyncApi("http://127.0.0.1:1", client) { null }.pull(0, 10) }
        } finally {
            client.close()
        }
    }

    @Test
    fun nonJsonErrorBodiesAreTolerated() = syncServerTest { _, _ ->
        // Unbekannter Pfad (wie hinter einem falsch konfigurierten Reverse-Proxy): 404 ohne ErrorDto-Body
        val e = assertFailsWith<SyncApiException.ClientError> { api(client, "https://localhost/proxy").pull(0, 10) }
        assertEquals(404, e.status)
        assertEquals(null, e.code)
    }

    @Test
    fun baseUrlWithTrailingSlashWorks() = syncServerTest { _, _ ->
        val paths = mutableListOf<String>()
        val recording = client.also {
            it.plugin(HttpSend).intercept { request ->
                paths += request.url.build().encodedPath
                execute(request)
            }
        }
        api(recording, "https://localhost/").adminLogin()
        api(recording, "https://localhost").adminLogin()
        assertEquals(2, paths.size)
        assertEquals(paths[0], paths[1])
        assertEquals("/api/v1/auth/login", paths[0])
    }

    private fun field(target: Any?, name: String): Any? {
        checkNotNull(target)
        var c: Class<*>? = target.javaClass
        while (c != null) {
            c.declaredFields.firstOrNull { it.name == name || it.name == "_" + name }?.let {
                it.isAccessible = true
                return it.get(target)
            }
            c = c.superclass
        }
        error("Feld $name fehlt in ${target.javaClass}")
    }

    @Test
    fun defaultClientHasFullTimeoutsAndNoRedirects() {
        val client = defaultHttpClient()
        try {
            // Die Konfiguration ist nicht öffentlich lesbar – Reflection auf die Ktor-Felder.
            val timeout = field(client.plugin(HttpTimeout), "config")
            assertEquals(15_000L, field(timeout, "connectTimeoutMillis"))
            assertEquals(60_000L, field(timeout, "requestTimeoutMillis"))
            assertEquals(60_000L, field(timeout, "socketTimeoutMillis"))
            assertEquals(false, field(field(client, "config"), "followRedirects"))
        } finally {
            client.close()
        }
    }

    @Test
    fun malformedResponseDoesNotLeakTokenIntoExceptions() = testApplication {
        val secret = "SECRET-TOKEN-4711"
        routing {
            post("/api/v1/auth/login") {
                call.respondText("""{"token":"$secret","userId":[1,2""", ContentType.Application.Json)
            }
        }
        val e = assertFailsWith<SyncApiException.Transient> { api(client, "https://localhost").adminLogin() }
        var t: Throwable? = e
        while (t != null) {
            assertTrue(!t.toString().contains(secret) && t.message?.contains(secret) != true, "Token in $t")
            t = t.cause
        }
    }

    @Test
    fun redirectIsClientErrorNotTransient() = testApplication {
        routing {
            post("/api/v1/auth/login") { call.respond(HttpStatusCode.Found) }
        }
        val e = assertFailsWith<SyncApiException.ClientError> { api(client, "https://localhost").adminLogin() }
        assertEquals(302, e.status)
    }
}

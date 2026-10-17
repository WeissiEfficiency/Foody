package de.foody.server

import de.foody.server.auth.KontoExistiert
import de.foody.server.auth.PasswordHasher
import de.foody.server.auth.Tokens
import de.foody.api.protocol.AuthResponse
import de.foody.api.protocol.ErrorCode
import de.foody.api.protocol.Protocol
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import de.foody.api.protocol.LoginRequest
import de.foody.api.protocol.PasswordChangeRequest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthTest {
    private val pw = "geheimgeheim"

    private suspend fun HttpClient.login(user: String, password: String, device: String = "Handy"): HttpResponse =
        post("/api/v3/auth/login") {
            protocol()
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(LoginRequest(user, password, device)))
        }

    /** Authentifizierter Aufruf zum Prüfen eines Tokens (Pull ohne Datensätze). */
    private suspend fun HttpClient.probe(token: String?): HttpResponse = get("/api/v3/sync/pull") {
        protocol()
        if (token != null) bearerAuth(token)
    }

    private fun userCount(env: TestEnv): Int = env.deps.db.tx { c ->
        c.createStatement().use { s -> s.executeQuery("SELECT COUNT(*) FROM user").use { it.next(); it.getInt(1) } }
    }

    @Test
    fun hashUsesArgon2idParameters() {
        val hasher = PasswordHasher()
        val encoded = hasher.hash(pw)
        assertTrue(encoded.startsWith("\$argon2id\$v=19\$m=19456,t=2,p=1\$"), encoded)
        assertTrue(hasher.verify(pw, encoded))
        assertFalse(hasher.verify("falschfalsch", encoded))
    }

    @Test
    fun bootstrapAdminOnlyOnEmptyDatabase() = testServer { env ->
        assertFalse(env.deps.accounts.bootstrapAdmin(null, null))
        assertFalse(env.deps.accounts.bootstrapAdmin("admin", null))
        assertEquals(0, userCount(env))
        assertTrue(env.deps.accounts.bootstrapAdmin("admin", pw))
        assertFalse(env.deps.accounts.bootstrapAdmin("other", pw))
        assertEquals(1, userCount(env))
    }

    @Test
    fun loginReturnsTokenAndTokenAuthenticates() = testServer { env ->
        val kontoId = env.deps.accounts.createUser("stefan", pw)
        val response = client.login("stefan", pw)
        assertEquals(HttpStatusCode.OK, response.status)
        val auth = Protocol.json.decodeFromString<AuthResponse>(response.bodyAsText())
        assertEquals(kontoId, auth.kontoId)
        assertTrue(auth.token.isNotEmpty())
        assertEquals(kontoId, env.deps.accounts.deviceForToken(auth.token)!!.kontoId)

        assertEquals(HttpStatusCode.OK, client.probe(auth.token).status)

        val denied = client.probe(null)
        assertEquals(HttpStatusCode.Unauthorized, denied.status)
        assertEquals("""{"code":"unauthorized"}""", denied.bodyAsText())
        assertEquals(HttpStatusCode.Unauthorized, client.probe("falsch").status)
    }

    @Test
    fun loginIsCaseInsensitiveOnUsername() = testServer { env ->
        env.deps.accounts.createUser("Stefan", pw)
        assertEquals(HttpStatusCode.OK, client.login("stefan", pw).status)
    }

    @Test
    fun wrongPasswordAndUnknownUserLookTheSame() = testServer { env ->
        env.deps.accounts.createUser("stefan", pw)
        val wrong = client.login("stefan", "falschfalsch")
        val unknown = client.login("niemand", "falschfalsch")
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertEquals(HttpStatusCode.Unauthorized, unknown.status)
        assertEquals("""{"code":"invalid_credentials"}""", wrong.bodyAsText())
        assertEquals(wrong.bodyAsText(), unknown.bodyAsText())
    }

    @Test
    fun fiveFailuresLockForFifteenMinutes() = testServer { env ->
        env.deps.accounts.createUser("stefan", pw)
        repeat(5) { assertEquals(HttpStatusCode.Unauthorized, client.login("stefan", "falschfalsch").status) }
        val locked = client.login("stefan", pw)
        assertEquals(HttpStatusCode.TooManyRequests, locked.status)
        assertEquals("""{"code":"throttled"}""", locked.bodyAsText())
        env.clock.advance(Duration.ofMinutes(15))
        assertEquals(HttpStatusCode.OK, client.login("stefan", pw).status)
    }

    @Test
    fun passwordPolicy() = testServer { env ->
        val e1 = assertFailsWith<ApiException> { env.deps.accounts.createUser("stefan", "123456789") }
        assertEquals(ErrorCode.INVALID_INPUT, e1.code)
        val e2 = assertFailsWith<ApiException> { env.deps.accounts.createUser("a b", pw) }
        assertEquals(ErrorCode.INVALID_INPUT, e2.code)
        val e3 = assertFailsWith<ApiException> { env.deps.accounts.createUser("ab", pw) }
        assertEquals(ErrorCode.INVALID_INPUT, e3.code)
        env.deps.accounts.createUser("stefan", pw)
        assertFailsWith<KontoExistiert> { env.deps.accounts.createUser("STEFAN", pw) }
    }

    @Test
    fun changePasswordRevokesOtherDevices() = testServer { env ->
        env.deps.accounts.createUser("stefan", pw)
        val t1 = Protocol.json.decodeFromString<AuthResponse>(client.login("stefan", pw, "Eins").bodyAsText()).token
        val t2 = Protocol.json.decodeFromString<AuthResponse>(client.login("stefan", pw, "Zwei").bodyAsText()).token
        val change = client.post("/api/v3/auth/password") {
            protocol()
            bearerAuth(t1)
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(PasswordChangeRequest(pw, "neuesPasswort1")))
        }
        assertEquals(HttpStatusCode.NoContent, change.status)
        assertEquals(HttpStatusCode.OK, client.probe(t1).status)
        assertEquals(HttpStatusCode.Unauthorized, client.probe(t2).status)
        assertEquals(HttpStatusCode.Unauthorized, client.login("stefan", pw).status)
        assertEquals(HttpStatusCode.OK, client.login("stefan", "neuesPasswort1").status)
    }

    @Test
    fun tokenIsStoredOnlyAsHash() = testServer { env ->
        val kontoId = env.deps.accounts.createUser("stefan", pw)
        val token = env.deps.accounts.createDevice(kontoId, "Handy")
        val stored = env.deps.db.tx { c ->
            c.createStatement().use { s -> s.executeQuery("SELECT token_hash FROM device").use { it.next(); it.getString(1) } }
        }
        assertNotEquals(token, stored)
        assertEquals(Tokens.sha256Hex(token), stored)
    }

    @Test
    fun abmeldenWiderruft() = testServer { env ->
        val kontoId = env.deps.accounts.createUser("stefan", pw)
        val token = env.deps.accounts.createDevice(kontoId, "Handy")
        val zweites = env.deps.accounts.createDevice(kontoId, "Tablet")
        val device = env.deps.accounts.deviceForToken(token)!!
        env.deps.accounts.abmelden(device.deviceId)
        assertNull(env.deps.accounts.deviceForToken(token))
        assertEquals(HttpStatusCode.Unauthorized, client.probe(token).status)
        assertEquals(kontoId, env.deps.accounts.deviceForToken(zweites)!!.kontoId, "nur das eigene Gerät wird getrennt")
    }

    @Test
    fun concurrentWrongLoginsStayWithinLimit() = testServer { env ->
        env.deps.accounts.createUser("stefan", pw)
        val statuses = coroutineScope {
            (1..10).map { async(Dispatchers.Default) { client.login("stefan", "falschfalsch").status } }.awaitAll()
        }
        assertEquals(5, statuses.count { it == HttpStatusCode.Unauthorized })
        assertEquals(5, statuses.count { it == HttpStatusCode.TooManyRequests })
    }

    @Test
    fun protocolCheckRunsBeforeAuthentication() = testServer {
        val response = client.get("/api/v3/sync/pull")
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("""{"code":"protocol_too_old"}""", response.bodyAsText())
    }

    @Test
    fun oversizedLoginBodyIsRejected() = testServer {
        val response = client.post("/api/v3/auth/login") {
            protocol()
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(LoginRequest("x".repeat(70 * 1024), "p", "Handy")))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals("""{"code":"too_large"}""", response.bodyAsText())
    }

    @Test
    fun overlongLoginCredentialsAre401WithoutThrottleEntry() = testServer { env ->
        env.deps.accounts.createUser("stefan", pw)
        repeat(6) { i ->
            val r = client.login("u".repeat(33) + i, pw)
            assertEquals(HttpStatusCode.Unauthorized, r.status)
            assertEquals("""{"code":"invalid_credentials"}""", r.bodyAsText())
        }
        assertEquals(HttpStatusCode.Unauthorized, client.login("stefan", "p".repeat(201)).status)
        assertEquals(0, env.deps.throttle.size())
        assertEquals(HttpStatusCode.OK, client.login("stefan", pw).status)
    }

    private suspend fun HttpClient.changePassword(token: String, old: String): HttpResponse =
        post("/api/v3/auth/password") {
            protocol()
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(PasswordChangeRequest(old, "neuesPasswort1")))
        }

    @Test
    fun changePasswordWithWrongOldPasswordIs401() = testServer { env ->
        val kontoId = env.deps.accounts.createUser("stefan", pw)
        val token = env.deps.accounts.createDevice(kontoId, "Handy")
        val r = client.changePassword(token, "falschfalsch")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertEquals("""{"code":"invalid_credentials"}""", r.bodyAsText())
    }

    @Test
    fun changePasswordIsThrottled() = testServer { env ->
        val kontoId = env.deps.accounts.createUser("stefan", pw)
        val token = env.deps.accounts.createDevice(kontoId, "Handy")
        repeat(5) { assertEquals(HttpStatusCode.Unauthorized, client.changePassword(token, "falschfalsch").status) }
        val locked = client.changePassword(token, pw)
        assertEquals(HttpStatusCode.TooManyRequests, locked.status)
        assertEquals("""{"code":"throttled"}""", locked.bodyAsText())
    }
}

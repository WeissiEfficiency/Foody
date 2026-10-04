package de.foody.server

import de.foody.server.auth.PasswordHasher
import de.foody.server.auth.Tokens
import de.foody.sync.protocol.AuthResponse
import de.foody.sync.protocol.DeviceDto
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.Protocol
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import de.foody.sync.protocol.LoginRequest
import de.foody.sync.protocol.PasswordChangeRequest
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
        post("/api/v1/auth/login") {
            protocol()
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(LoginRequest(user, password, device)))
        }

    private suspend fun HttpClient.devices(token: String?): HttpResponse = get("/api/v1/devices") {
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
        val userId = env.deps.accounts.createUser("stefan", pw)
        val response = client.login("stefan", pw)
        assertEquals(HttpStatusCode.OK, response.status)
        val auth = Protocol.json.decodeFromString<AuthResponse>(response.bodyAsText())
        assertEquals(userId, auth.userId)
        assertNull(auth.householdId)
        assertTrue(auth.token.isNotEmpty())

        val ok = client.devices(auth.token)
        assertEquals(HttpStatusCode.OK, ok.status)
        val list = Protocol.json.decodeFromString<List<DeviceDto>>(ok.bodyAsText())
        assertEquals(1, list.size)
        assertTrue(list.single().current)
        assertEquals("Handy", list.single().name)

        val denied = client.devices(null)
        assertEquals(HttpStatusCode.Unauthorized, denied.status)
        assertEquals("""{"code":"unauthorized"}""", denied.bodyAsText())
        assertEquals(HttpStatusCode.Unauthorized, client.devices("falsch").status)
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
        val e4 = assertFailsWith<ApiException> { env.deps.accounts.createUser("STEFAN", pw) }
        assertEquals(ErrorCode.USERNAME_TAKEN, e4.code)
    }

    @Test
    fun changePasswordRevokesOtherDevices() = testServer { env ->
        env.deps.accounts.createUser("stefan", pw)
        val t1 = Protocol.json.decodeFromString<AuthResponse>(client.login("stefan", pw, "Eins").bodyAsText()).token
        val t2 = Protocol.json.decodeFromString<AuthResponse>(client.login("stefan", pw, "Zwei").bodyAsText()).token
        val change = client.post("/api/v1/auth/password") {
            protocol()
            bearerAuth(t1)
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(PasswordChangeRequest(pw, "neuesPasswort1")))
        }
        assertEquals(HttpStatusCode.NoContent, change.status)
        assertEquals(HttpStatusCode.OK, client.devices(t1).status)
        assertEquals(HttpStatusCode.Unauthorized, client.devices(t2).status)
        assertEquals(HttpStatusCode.Unauthorized, client.login("stefan", pw).status)
        assertEquals(HttpStatusCode.OK, client.login("stefan", "neuesPasswort1").status)
    }

    @Test
    fun tokenIsStoredOnlyAsHash() = testServer { env ->
        val userId = env.deps.accounts.createUser("stefan", pw)
        val token = env.deps.accounts.createDevice(userId, null, "Handy")
        val stored = env.deps.db.tx { c ->
            c.createStatement().use { s -> s.executeQuery("SELECT token_hash FROM device").use { it.next(); it.getString(1) } }
        }
        assertNotEquals(token, stored)
        assertEquals(Tokens.sha256Hex(token), stored)
    }
}

package de.foody.server

import de.foody.server.auth.InviteCodes
import de.foody.server.auth.Tokens
import de.foody.sync.protocol.AuthResponse
import de.foody.sync.protocol.CreateHouseholdRequest
import de.foody.sync.protocol.DeviceDto
import de.foody.sync.protocol.HouseholdDto
import de.foody.sync.protocol.InviteDto
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.RegisterRequest
import de.foody.sync.protocol.SelectHouseholdRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class HouseholdTest {
    private val pw = "geheimgeheim"

    private fun TestEnv.user(name: String): Pair<String, String> {
        val id = deps.accounts.createUser(name, pw)
        return id to deps.accounts.createDevice(id, null, "Gerät $name")
    }

    private suspend fun HttpClient.createHousehold(token: String, name: String = "Zuhause"): HttpResponse =
        post("/api/v1/households") {
            protocol()
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(CreateHouseholdRequest(name)))
        }

    private suspend fun HttpClient.households(token: String): List<HouseholdDto> =
        Protocol.json.decodeFromString(get("/api/v1/households") { protocol(); bearerAuth(token) }.bodyAsText())

    private suspend fun HttpClient.invite(token: String): HttpResponse = post("/api/v1/invites") {
        protocol()
        bearerAuth(token)
    }

    private suspend fun HttpClient.register(
        code: String,
        username: String,
        password: String = pw,
    ): HttpResponse = post("/api/v1/auth/register") {
        protocol()
        contentType(ContentType.Application.Json)
        setBody(Protocol.json.encodeToString(RegisterRequest(code, username, password, "Neues Gerät")))
    }

    private suspend fun HttpClient.devices(token: String): HttpResponse =
        get("/api/v1/devices") { protocol(); bearerAuth(token) }

    private suspend fun HttpClient.newInviteCode(token: String): String =
        Protocol.json.decodeFromString<InviteDto>(invite(token).bodyAsText()).code

    private suspend fun HttpClient.selectHousehold(token: String, householdId: String): HttpResponse =
        post("/api/v1/device/household") {
            protocol()
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(Protocol.json.encodeToString(SelectHouseholdRequest(householdId)))
        }

    private suspend fun HttpClient.removeMember(token: String, householdId: String, userId: String): HttpResponse =
        delete("/api/v1/households/$householdId/members/$userId") { protocol(); bearerAuth(token) }

    private fun inviteUsedAt(env: TestEnv): Long? = env.deps.db.tx { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT used_at FROM invite").use { rs ->
                rs.next()
                val value = rs.getLong(1)
                if (rs.wasNull()) null else value
            }
        }
    }

    @Test
    fun inviteCodeFormatAndNormalization() {
        val code = InviteCodes.generate(SecureRandom())
        assertTrue(Regex("^FOODY-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$").matches(code), code)
        assertEquals(code.removePrefix("FOODY-").replace("-", ""), InviteCodes.normalize(code))
        assertEquals(InviteCodes.normalize("FOODY-ABCD-EFGH"), InviteCodes.normalize("foody-abcd-efgh"))
        assertEquals(InviteCodes.normalize("FOODY-ABCD-EFGH"), InviteCodes.normalize("ABCD EFGH"))
        assertEquals("00001111", InviteCodes.normalize("OOOO-IIII"))
        assertEquals("11111111", InviteCodes.normalize("LLLL-llll"))
        assertNull(InviteCodes.normalize("zu kurz"))
        assertNull(InviteCodes.normalize("ABCD-EFGU"))
    }

    @Test
    fun ownerInvitesPartnerWhoJoinsHousehold() = testServer { env ->
        val (_, tokenA) = env.user("anna")
        val household = Protocol.json.decodeFromString<HouseholdDto>(client.createHousehold(tokenA).bodyAsText())
        assertEquals("owner", household.role)
        val code = client.newInviteCode(tokenA)

        val response = client.register(code, "ben")
        assertEquals(HttpStatusCode.OK, response.status)
        val auth = Protocol.json.decodeFromString<AuthResponse>(response.bodyAsText())
        assertEquals(household.id, auth.householdId)
        assertEquals(listOf(HouseholdDto(household.id, "Zuhause", "member")), client.households(auth.token))
        // Das neue Gerät ist am Haushalt gebunden: Einladen funktioniert ohne weitere Auswahl.
        assertEquals(HttpStatusCode.OK, client.invite(auth.token).status)
    }

    @Test
    fun inviteStoresOnlyHashOfNormalizedCode() = testServer { env ->
        val (_, tokenA) = env.user("anna")
        client.createHousehold(tokenA)
        val code = client.newInviteCode(tokenA)
        val stored = env.deps.db.tx { c ->
            c.createStatement().use { s -> s.executeQuery("SELECT code_hash FROM invite").use { it.next(); it.getString(1) } }
        }
        assertEquals(Tokens.sha256Hex(InviteCodes.normalize(code)!!), stored)
    }

    @Test
    fun inviteIsSingleUseAndExpires() = testServer { env ->
        val (_, tokenA) = env.user("anna")
        client.createHousehold(tokenA)
        val code = client.newInviteCode(tokenA)
        assertEquals(HttpStatusCode.OK, client.register(code, "ben").status)
        val again = client.register(code, "carl")
        assertEquals(HttpStatusCode.BadRequest, again.status)
        assertEquals("""{"code":"invalid_invite"}""", again.bodyAsText())

        val expiring = client.newInviteCode(tokenA)
        env.clock.advance((7.days + 1.seconds).toJavaDuration())
        val late = client.register(expiring, "dora")
        assertEquals(HttpStatusCode.BadRequest, late.status)
        assertEquals("""{"code":"invalid_invite"}""", late.bodyAsText())
    }

    @Test
    fun registerRejectsTakenUsernameCaseInsensitive() = testServer { env ->
        env.deps.accounts.createUser("stefan", pw)
        val (_, tokenA) = env.user("anna")
        client.createHousehold(tokenA)
        val code = client.newInviteCode(tokenA)
        val taken = client.register(code, "STEFAN")
        assertEquals(HttpStatusCode.Conflict, taken.status)
        assertEquals("""{"code":"username_taken"}""", taken.bodyAsText())
        assertNull(inviteUsedAt(env))
        assertEquals(HttpStatusCode.OK, client.register(code, "ben").status)
    }

    @Test
    fun registerAppliesCredentialPolicyWithoutConsumingInvite() = testServer { env ->
        val (_, tokenA) = env.user("anna")
        client.createHousehold(tokenA)
        val code = client.newInviteCode(tokenA)
        assertEquals(HttpStatusCode.BadRequest, client.register(code, "ben", "kurz").status)
        assertEquals(HttpStatusCode.BadRequest, client.register(code, "b e").status)
        assertNull(inviteUsedAt(env))
        assertEquals(HttpStatusCode.OK, client.register(code, "ben").status)
    }

    @Test
    fun wrongInviteCodesAreThrottled() = testServer { env ->
        val (_, tokenA) = env.user("anna")
        client.createHousehold(tokenA)
        val good = client.newInviteCode(tokenA)
        repeat(5) { i ->
            assertEquals(HttpStatusCode.BadRequest, client.register("FOODY-0000-000$i", "ben").status)
        }
        val locked = client.register(good, "ben")
        assertEquals(HttpStatusCode.TooManyRequests, locked.status)
        assertEquals("""{"code":"throttled"}""", locked.bodyAsText())
    }

    @Test
    fun cannotSelectForeignHousehold() = testServer { env ->
        val (_, tokenB) = env.user("ben")
        val (_, tokenC) = env.user("carl")
        val foreign = Protocol.json.decodeFromString<HouseholdDto>(client.createHousehold(tokenC).bodyAsText())
        val response = client.selectHousehold(tokenB, foreign.id)
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("""{"code":"forbidden"}""", response.bodyAsText())
    }

    @Test
    fun selectingOwnHouseholdBindsDevice() = testServer { env ->
        val (userId, token) = env.user("anna")
        val h = Protocol.json.decodeFromString<HouseholdDto>(client.createHousehold(token).bodyAsText())
        // Zweites Gerät desselben Benutzers ist ungebunden und wählt den Haushalt.
        val second = env.deps.accounts.createDevice(userId, null, "Zweit")
        assertEquals(HttpStatusCode.Conflict, client.invite(second).status)
        assertEquals(HttpStatusCode.NoContent, client.selectHousehold(second, h.id).status)
        assertEquals(HttpStatusCode.OK, client.invite(second).status)
    }

    @Test
    fun householdNameLengthIsValidated() = testServer { env ->
        val (_, token) = env.user("anna")
        assertEquals(HttpStatusCode.BadRequest, client.createHousehold(token, "").status)
        val tooLong = client.createHousehold(token, "x".repeat(101))
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)
        assertEquals("""{"code":"invalid_input"}""", tooLong.bodyAsText())
        assertEquals(HttpStatusCode.OK, client.createHousehold(token, "x".repeat(100)).status)
    }

    @Test
    fun inviteWithoutHouseholdIsNoHousehold() = testServer { env ->
        val (_, token) = env.user("anna")
        val response = client.invite(token)
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("""{"code":"no_household"}""", response.bodyAsText())
    }

    @Test
    fun devicesListMarksCurrentAndRevokeWorks() = testServer { env ->
        val (userId, token1) = env.user("anna")
        val token2 = env.deps.accounts.createDevice(userId, null, "Zweit")
        val (_, foreignToken) = env.user("ben")

        val list = Protocol.json.decodeFromString<List<DeviceDto>>(client.devices(token1).bodyAsText())
        assertEquals(2, list.size)
        assertEquals(1, list.count { it.current })
        val other = list.single { !it.current }

        val foreignId =
            Protocol.json.decodeFromString<List<DeviceDto>>(client.devices(foreignToken).bodyAsText()).single().id
        val foreign = client.delete("/api/v1/devices/$foreignId") { protocol(); bearerAuth(token1) }
        assertEquals(HttpStatusCode.NotFound, foreign.status)
        assertEquals("""{"code":"not_found"}""", foreign.bodyAsText())
        val unknown = client.delete("/api/v1/devices/unbekannt") { protocol(); bearerAuth(token1) }
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        assertEquals(HttpStatusCode.OK, client.devices(foreignToken).status)

        assertEquals(HttpStatusCode.OK, client.devices(token2).status)
        val revoke = client.delete("/api/v1/devices/${other.id}") { protocol(); bearerAuth(token1) }
        assertEquals(HttpStatusCode.NoContent, revoke.status)
        assertEquals(HttpStatusCode.Unauthorized, client.devices(token2).status)
        assertEquals(HttpStatusCode.OK, client.devices(token1).status)
    }

    @Test
    fun removingMemberRevokesTheirDevicesForThatHousehold() = testServer { env ->
        val (_, tokenA) = env.user("anna")
        val household = Protocol.json.decodeFromString<HouseholdDto>(client.createHousehold(tokenA).bodyAsText())
        val joined = Protocol.json.decodeFromString<AuthResponse>(
            client.register(client.newInviteCode(tokenA), "ben").bodyAsText(),
        )
        assertEquals(HttpStatusCode.OK, client.devices(joined.token).status)

        // Ein Member darf niemanden entfernen, auch nicht den Owner.
        val ownerId = env.deps.db.tx { c ->
            c.createStatement().use { s ->
                s.executeQuery("SELECT user_id FROM membership WHERE role = 'owner'").use { it.next(); it.getString(1) }
            }
        }
        assertEquals(HttpStatusCode.Forbidden, client.removeMember(joined.token, household.id, ownerId).status)
        assertEquals(HttpStatusCode.OK, client.devices(tokenA).status)

        assertEquals(HttpStatusCode.NoContent, client.removeMember(tokenA, household.id, joined.userId).status)
        assertEquals(HttpStatusCode.Unauthorized, client.devices(joined.token).status)
        assertEquals(HttpStatusCode.OK, client.devices(tokenA).status)
    }

    @Test
    fun deviceTokenIsRejectedOnceMembershipIsGone() = testServer { env ->
        val (userId, token) = env.user("anna")
        val household = Protocol.json.decodeFromString<HouseholdDto>(client.createHousehold(token).bodyAsText())
        assertEquals(HttpStatusCode.OK, client.devices(token).status)
        // Nur die Mitgliedschaft entfernen, das Gerät bleibt unwiderrufen: Regel aus Task 3.
        env.deps.db.tx { c ->
            c.prepareStatement("DELETE FROM membership WHERE user_id = ? AND household_id = ?").use { st ->
                st.setString(1, userId)
                st.setString(2, household.id)
                st.executeUpdate()
            }
        }
        assertEquals(HttpStatusCode.Unauthorized, client.devices(token).status)
    }

    @Test
    fun ownerCannotRemoveThemselvesAndUnknownMemberIsNotFound() = testServer { env ->
        val (userId, token) = env.user("anna")
        val household = Protocol.json.decodeFromString<HouseholdDto>(client.createHousehold(token).bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, client.removeMember(token, household.id, userId).status)
        assertEquals(HttpStatusCode.NotFound, client.removeMember(token, household.id, "unbekannt").status)
    }
}

package de.foody.server

import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.PullResponse
import de.foody.sync.protocol.RecipePayload
import de.foody.sync.protocol.PushStatus
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import de.foody.sync.protocol.TagebuchPayload
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class SyncTest {
    @Test
    fun pushThenPullReturnsRecordsWithIncreasingRevs() = testServer { env ->
        val (token, _) = env.setupHousehold()
        val results = client.pushOk(token, listOf(ingredient("i1"), recipe("r1", listOf("i1")))).results
        assertEquals(listOf(PushStatus.ACCEPTED, PushStatus.ACCEPTED), results.map { it.status })
        assertEquals(listOf(1L, 2L), results.map { it.rev })
        val pulled = client.pull(token, 0)
        assertEquals(listOf("i1", "r1"), pulled.records.map { it.id })
        assertEquals(listOf(1L, 2L), pulled.records.map { it.rev })
        assertTrue(pulled.records.all { it.baseRev == null && !it.deleted })
        assertEquals(2L, pulled.nextCursor)
        assertEquals(false, pulled.hasMore)
    }

    @Test
    fun pullPagesWithLimit() = testServer { env ->
        val (token, _) = env.setupHousehold()
        client.pushOk(token, (1..5).map { ingredient("i$it", "Zutat $it") })
        val p1 = client.pull(token, 0, 2)
        val p2 = client.pull(token, p1.nextCursor, 2)
        val p3 = client.pull(token, p2.nextCursor, 2)
        assertEquals(listOf(2, 2, 1), listOf(p1, p2, p3).map { it.records.size })
        assertEquals(listOf(true, true, false), listOf(p1, p2, p3).map { it.hasMore })
        assertEquals(listOf(2L, 4L, 5L), listOf(p1, p2, p3).map { it.nextCursor })
    }

    @Test
    fun sameRecordTwiceInOnePushLastWins() = testServer { env ->
        val (token, _) = env.setupHousehold()
        val results = client.pushOk(token, listOf(ingredient("i1", "A"), ingredient("i1", "B"))).results
        assertEquals(listOf(1L, 2L), results.map { it.rev })
        assertTrue(results.all { it.status == PushStatus.ACCEPTED })
        val pulled = client.pull(token, 0)
        assertEquals(1, pulled.records.size)
        assertEquals(2L, pulled.records[0].rev)
        assertEquals(JsonPrimitive("B"), pulled.records[0].payload!!["name"])
    }

    @Test
    fun unknownPayloadFieldsArePreserved() = testServer { env ->
        val (token, _) = env.setupHousehold()
        client.pushOk(token, listOf(ingredient("i1", extra = mapOf("futureField" to "x"))))
        val payload = client.pull(token, 0).records.single().payload!!
        assertEquals(JsonPrimitive("x"), payload["futureField"])
    }

    @Test
    fun pullBeyondHeadIsEmpty() = testServer { env ->
        val (token, _) = env.setupHousehold()
        client.pushOk(token, listOf(ingredient("i1"), ingredient("i2", "Salz")))
        val pulled = client.pull(token, 10)
        assertEquals(emptyList(), pulled.records)
        assertEquals(10L, pulled.nextCursor)
        assertEquals(false, pulled.hasMore)
    }

    @Test
    fun deviceWithoutHouseholdGetsNoHousehold() = testServer { env ->
        val userId = env.deps.accounts.createUser("ohnehaus", "geheimgeheim")
        val token = env.deps.accounts.createDevice(userId, null, "Gerät")
        val push = client.push(token, listOf(ingredient("i1")))
        assertEquals(HttpStatusCode.Conflict, push.status)
        assertEquals(ErrorCode.NO_HOUSEHOLD, push.errorCode())
        val pull = client.pullRaw(token, "?since=0")
        assertEquals(HttpStatusCode.Conflict, pull.status)
        assertEquals(ErrorCode.NO_HOUSEHOLD, pull.errorCode())
    }

    @Test
    fun invalidRecordIsRejectedOthersAccepted() = testServer { env ->
        val (token, _) = env.setupHousehold()
        val bad = recipe("r1", listOf("i1"), amount = "1E999999999")
        val results = client.pushOk(token, listOf(ingredient("i1"), bad, ingredient("i2", "Salz"))).results
        assertEquals(listOf(PushStatus.ACCEPTED, PushStatus.REJECTED, PushStatus.ACCEPTED), results.map { it.status })
        assertEquals(ErrorCode.INVALID_PAYLOAD, results[1].code)
        assertNull(results[1].rev)
    }

    @Test
    fun duplicateChildIdsAreRejected() = testServer { env ->
        val (token, _) = env.setupHousehold()
        val dup = recipe("r1", listOf("i1", "i1"))
        val payload = Protocol.json.decodeFromJsonElement<RecipePayload>(dup.payload!!)
        val broken = dup.copy(payload = Protocol.json.encodeToJsonElement(payload.copy(lines = payload.lines.map { it.copy(id = "same") })) as JsonObject)
        val results = client.pushOk(token, listOf(ingredient("i1"), broken)).results
        assertEquals(PushStatus.REJECTED, results[1].status)
        assertEquals(ErrorCode.INVALID_PAYLOAD, results[1].code)
    }

    @Test
    fun malformedPayloadIsRejectedNotServerError() = testServer { env ->
        val (token, _) = env.setupHousehold()
        val broken = SyncRecord("r1", RecordType.RECIPE, updatedAt = 1, payload = JsonObject(mapOf("foo" to JsonPrimitive(1))))
        val results = client.pushOk(token, listOf(broken)).results
        assertEquals(ErrorCode.INVALID_PAYLOAD, results.single().code)
    }

    @Test
    fun missingReferenceIsRejected() = testServer { env ->
        val (token, _) = env.setupHousehold()
        val alone = client.pushOk(token, listOf(recipe("r1", listOf("i1")))).results.single()
        assertEquals(PushStatus.REJECTED, alone.status)
        assertEquals(ErrorCode.MISSING_REFERENCE, alone.code)
        val together = client.pushOk(token, listOf(ingredient("i1"), recipe("r1", listOf("i1")))).results
        assertTrue(together.all { it.status == PushStatus.ACCEPTED })
    }

    @Test
    fun shoppingItemNeedsItsListAndDeletedReferenceIsMissing() = testServer { env ->
        val (token, _) = env.setupHousehold()
        val orphan = client.pushOk(token, listOf(shoppingItem("s1", "l1", checked = false, checkedChangedAt = 0))).results.single()
        assertEquals(ErrorCode.MISSING_REFERENCE, orphan.code)
        client.pushOk(token, listOf(shoppingList("l1")))
        client.pushOk(token, listOf(SyncRecord("l1", RecordType.SHOPPING_LIST, deleted = true, updatedAt = 2)))
        val afterDelete = client.pushOk(token, listOf(shoppingItem("s1", "l1", checked = false, checkedChangedAt = 0))).results.single()
        assertEquals(ErrorCode.MISSING_REFERENCE, afterDelete.code)
    }

    @Test
    fun deleteStoresTombstoneWithoutPayload() = testServer { env ->
        val (token, _) = env.setupHousehold()
        val result = client.pushOk(token, listOf(SyncRecord("never-seen", RecordType.INGREDIENT, deleted = true, updatedAt = 2)))
            .results.single()
        assertEquals(PushStatus.ACCEPTED, result.status)
        val record = client.pull(token, 0).records.single()
        assertTrue(record.deleted)
        assertNull(record.payload)
        assertEquals(result.rev, record.rev)
    }

    @Test
    fun householdsAreIsolated() = testServer { env ->
        val (tokenA, _) = env.setupHousehold("A")
        val (tokenB, _) = env.setupHousehold("B")
        client.pushOk(tokenA, listOf(ingredient("x")))
        assertEquals(emptyList(), client.pull(tokenB, 0).records)
        val result = client.pushOk(tokenB, listOf(recipe("r1", listOf("x")))).results.single()
        assertEquals(ErrorCode.MISSING_REFERENCE, result.code)
    }

    @Test
    fun revsAreCountedPerHousehold() = testServer { env ->
        val (tokenA, _) = env.setupHousehold("A")
        val (tokenB, _) = env.setupHousehold("B")
        assertEquals(1L, client.pushOk(tokenA, listOf(ingredient("x"))).results.single().rev)
        assertEquals(1L, client.pushOk(tokenB, listOf(ingredient("x"))).results.single().rev)
    }

    @Test
    fun tooManyRecordsIsTooLarge() = testServer { env ->
        val (token, _) = env.setupHousehold()
        val response = client.push(token, (1..501).map { ingredient("i$it", "Z$it") })
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals(ErrorCode.TOO_LARGE, response.errorCode())
    }

    @Test
    fun oversizedBodyIsTooLarge() = testServer { env ->
        val (token, _) = env.setupHousehold()
        val response = client.post("/api/v1/sync/push") {
            protocol()
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("{\"records\":[],\"pad\":\"" + "a".repeat(Protocol.MAX_PUSH_BYTES.toInt() + 10) + "\"}")
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals(ErrorCode.TOO_LARGE, response.errorCode())
    }

    @Test
    fun invalidQueryAndBodyAreBadRequests() = testServer { env ->
        val (token, _) = env.setupHousehold()
        for (query in listOf("?since=-1", "?since=abc", "?limit=abc")) {
            val r = client.pullRaw(token, query)
            assertEquals(HttpStatusCode.BadRequest, r.status, query)
            assertEquals(ErrorCode.INVALID_INPUT, r.errorCode(), query)
        }
        val garbage = client.post("/api/v1/sync/push") {
            protocol()
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("{nope")
        }
        assertEquals(HttpStatusCode.BadRequest, garbage.status)
        assertEquals(ErrorCode.INVALID_INPUT, garbage.errorCode())
    }

    @Test
    fun limitIsCoercedIntoRangeAndDefaultsToMax() = testServer { env ->
        val (token, _) = env.setupHousehold()
        client.pushOk(token, listOf(ingredient("i1"), ingredient("i2", "Salz")))
        assertEquals(1, client.pull(token, 0, 0).records.size)
        assertEquals(2, client.pull(token, 0, 100_000).records.size)
        val noParams = client.pullRaw(token, "")
        assertEquals(2, Protocol.json.decodeFromString(PullResponse.serializer(), noParams.bodyAsText()).records.size)
    }

    @Test
    fun tagebuchEintragRoundTrip() = testServer { env ->
        val (token, _) = env.setupHousehold()
        val payload = TagebuchPayload(datum = "2026-10-10", mahlzeit = "SNACK", art = "FREI", name = "Apfel", energieKj = "335")
        val record = SyncRecord(id = "dddddddd-dddd-4ddd-8ddd-dddddddddddd", type = RecordType.TAGEBUCH_EINTRAG, updatedAt = 1, payload = obj(payload))
        assertEquals(listOf(PushStatus.ACCEPTED), client.pushOk(token, listOf(record)).results.map { it.status })
        val pulled = client.pull(token, 0).records.single()
        assertEquals(RecordType.TAGEBUCH_EINTRAG, pulled.type)
        assertEquals(payload, Protocol.json.decodeFromJsonElement<TagebuchPayload>(pulled.payload!!))
    }

    /** Seit dem Tagebuch (Protokoll 2) bekommt eine App ohne Tagebuch-Typ „bitte aktualisieren“ statt eines Dekodierfehlers. */
    @Test
    fun appOhneTagebuchMussAktualisieren() = testServer { env ->
        val (token, _) = env.setupHousehold()
        val alt = client.get("/api/v1/sync/pull?since=0") { protocol(1); bearerAuth(token) }
        assertEquals(io.ktor.http.HttpStatusCode.Conflict, alt.status)
        assertEquals(ErrorCode.PROTOCOL_TOO_OLD, alt.errorCode())
        assertEquals(2, Protocol.MIN_VERSION)
    }
}

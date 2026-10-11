package de.foody.server

import de.foody.server.sync.Compactor
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import io.ktor.http.HttpStatusCode
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompactionTest {
    private fun deletion(id: String) = SyncRecord(id, RecordType.INGREDIENT, deleted = true, updatedAt = 2)

    @Test
    fun oldTombstonesAreRemovedAndCursorExpires() = testServer { env ->
        val (token, _) = env.setupHousehold()
        client.pushOk(token, listOf(ingredient("i1", "Mehl"), ingredient("i2", "Salz")))
        client.pushOk(token, listOf(deletion("i1")))
        env.clock.advance(Duration.ofDays(91))
        assertEquals(1, Compactor(env.deps.db, env.clock).run())

        val expired = client.pullRaw(token, "?since=1")
        assertEquals(HttpStatusCode.Gone, expired.status)
        assertEquals(ErrorCode.CURSOR_EXPIRED, expired.errorCode())
        assertEquals(listOf("i2"), client.pull(token, 0).records.map { it.id })
        assertEquals(HttpStatusCode.OK, client.pullRaw(token, "?since=3").status)
        assertEquals(HttpStatusCode.OK, client.pullRaw(token, "?since=0").status)
    }

    @Test
    fun cursorAtCompactedRevisionStaysValid() = testServer { env ->
        val (token, _) = env.setupHousehold()
        client.pushOk(token, listOf(ingredient("i1", "Mehl")))
        client.pushOk(token, listOf(deletion("i1")))
        env.clock.advance(Duration.ofDays(91))
        assertEquals(1, Compactor(env.deps.db, env.clock).run())
        assertEquals(HttpStatusCode.Gone, client.pullRaw(token, "?since=1").status)
        assertEquals(HttpStatusCode.OK, client.pullRaw(token, "?since=2").status)
    }

    @Test
    fun recentTombstonesStay() = testServer { env ->
        val (token, _) = env.setupHousehold()
        client.pushOk(token, listOf(ingredient("i1", "Mehl")))
        client.pushOk(token, listOf(deletion("i1")))
        env.clock.advance(Duration.ofDays(89))
        assertEquals(0, Compactor(env.deps.db, env.clock).run())
        val records = client.pull(token, 1).records
        assertEquals(1, records.size)
        assertTrue(records.single().deleted)
    }
}

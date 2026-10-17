package de.foody.server

import de.foody.server.sync.RecordStore
import de.foody.api.protocol.SyncRecord
import kotlin.test.Test
import kotlin.test.assertEquals

/** `RecordStore.lebende`: alle nicht gelöschten Datensätze eines Kontos. */
class LebendeTest {
    private val store = RecordStore()

    @Test fun nurLebendeDesEigenenKontos() = testServer { env ->
        val (_, a) = env.setupKonto()
        val (_, b) = env.setupKonto()
        env.deps.sync.push(a, listOf(ingredient("i1", "Mehl"), ingredient("i2", "Zucker"), ingredient("i3", "Salz")))
        env.deps.sync.push(b, listOf(ingredient("x1", "Reis")))
        val geloescht = env.deps.db.tx { c -> store.get(c, a, de.foody.api.protocol.RecordType.INGREDIENT, "i3")!! }
        env.deps.sync.push(a, listOf(SyncRecord(id = "i3", type = geloescht.type, deleted = true, updatedAt = 2, baseRev = geloescht.rev)))
        val hoechste = env.deps.db.tx { c -> store.page(c, a, 0, 100).maxOf { it.rev!! } }

        val (records, rev) = env.deps.db.tx { c -> store.lebende(c, a) }

        assertEquals(setOf("i1", "i2"), records.map { it.id }.toSet())
        assertEquals(hoechste, rev, "die Löschung zählt für die Revision mit")
        assertEquals(rev, env.deps.db.tx { c -> store.hoechsteRevision(c, a) })
    }

    @Test fun leeresKonto() = testServer { env ->
        val (_, a) = env.setupKonto()
        assertEquals(emptyList<SyncRecord>() to 0L, env.deps.db.tx { c -> store.lebende(c, a) })
    }
}

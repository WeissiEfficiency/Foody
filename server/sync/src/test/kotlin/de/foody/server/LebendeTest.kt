package de.foody.server

import de.foody.server.sync.RecordStore
import de.foody.sync.protocol.SyncRecord
import kotlin.test.Test
import kotlin.test.assertEquals

/** `RecordStore.lebende`: Grundlage der Web-Oberfläche – alle nicht gelöschten Datensätze eines Haushalts. */
class LebendeTest {
    private val store = RecordStore()

    @Test fun nurLebendeDesEigenenHaushalts() = testServer { env ->
        val (_, a) = env.setupHousehold("A")
        val (_, b) = env.setupHousehold("B")
        env.deps.sync.push(a, listOf(ingredient("i1", "Mehl"), ingredient("i2", "Zucker"), ingredient("i3", "Salz")))
        env.deps.sync.push(b, listOf(ingredient("x1", "Reis")))
        val geloescht = env.deps.db.tx { c -> store.get(c, a, de.foody.sync.protocol.RecordType.INGREDIENT, "i3")!! }
        env.deps.sync.push(a, listOf(SyncRecord(id = "i3", type = geloescht.type, deleted = true, updatedAt = 2, baseRev = geloescht.rev)))
        val hoechste = env.deps.db.tx { c -> store.page(c, a, 0, 100).maxOf { it.rev!! } }

        val (records, rev) = env.deps.db.tx { c -> store.lebende(c, a) }

        assertEquals(setOf("i1", "i2"), records.map { it.id }.toSet())
        assertEquals(hoechste, rev, "die Löschung zählt für die Revision mit")
        assertEquals(rev, env.deps.db.tx { c -> store.hoechsteRevision(c, a) })
    }

    @Test fun leererHaushalt() = testServer { env ->
        val (_, a) = env.setupHousehold("Leer")
        assertEquals(emptyList<SyncRecord>() to 0L, env.deps.db.tx { c -> store.lebende(c, a) })
    }
}

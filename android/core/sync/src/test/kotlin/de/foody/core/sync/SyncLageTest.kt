package de.foody.core.sync

import de.foody.core.data.db.SyncZustandEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class SyncLageTest {
    @Test
    fun ohneZeileInaktiv() = runTest {
        assertEquals(SyncLage(aktiv = false, laeuft = false, letzterFehler = null), syncLage(flowOf(null), flowOf(false)).first())
    }

    @Test
    fun zustandUndLaufZusammen() = runTest {
        val z = SyncZustandEntity(active = true, lastError = "offline")
        assertEquals(SyncLage(aktiv = true, laeuft = true, letzterFehler = "offline"), syncLage(flowOf(z), flowOf(true)).first())
    }
}

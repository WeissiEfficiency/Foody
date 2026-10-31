package de.foody.core.designsystem

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SolangeAppOffenTest {
    private val offen = MutableStateFlow(false)
    private var laeuft = 0
    private var starts = 0

    /** Zählt Starts und zeigt, ob die Quelle gerade gesammelt wird. */
    private val quelle = flow {
        starts++
        laeuft++
        try {
            emit(starts)
            awaitCancellation()
        } finally {
            laeuft--
        }
    }

    @Test
    fun laeuftOhneZuseherSolangeDieAppOffenIst() = runTest {
        val zustand = quelle.stateIn(backgroundScope, solangeOffen(offen), 0)
        runCurrent()
        assertEquals(0, laeuft, "App zu, niemand sieht zu: nichts")

        offen.value = true
        runCurrent()
        assertEquals(1, laeuft, "App offen: läuft auch ohne Bildschirm")
        assertEquals(1, zustand.value)

        offen.value = false
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(1, laeuft, "kurz im Hintergrund: läuft noch")
        advanceTimeBy(2)
        runCurrent()
        assertEquals(0, laeuft, "nach 5 s im Hintergrund: ruht")
        assertEquals(1, zustand.value, "letzter Wert bleibt stehen")

        offen.value = true
        runCurrent()
        assertEquals(2, starts, "beim Öffnen neu gerechnet")
    }

    @Test
    fun zuseherHaeltDenZustandAuchBeiGeschlossenerApp() = runTest {
        val zustand = quelle.stateIn(backgroundScope, solangeOffen(offen), 0)
        val sieht = launch { zustand.collect {} }
        runCurrent()
        assertEquals(1, laeuft)

        offen.value = true
        runCurrent()
        offen.value = false
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, laeuft, "jemand sieht zu: läuft")
        assertEquals(1, starts, "nicht neu gestartet")
        sieht.cancel()
    }
}

package de.foody.feature.kochen.timer

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.feature.kochen.ZWOELF_STUNDEN_MS
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class KochTimerSpeicherTest {
    private val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("test_timer", Context.MODE_PRIVATE)

    @Before
    fun setUp() = prefs.edit().clear().commit().let { }

    private fun speicher(ms: Long) = KochTimerSpeicher(prefs, Clock.fixed(Instant.ofEpochMilli(ms), ZoneOffset.UTC))

    @Test
    fun startenUndEntfernen() {
        val s = speicher(1_000L)
        val id = s.starten("r1", 2, "Reistopf", "20 Minuten", Duration.ofMinutes(20))
        val t = s.timer.value.single()
        assertEquals(LaufenderTimer(id, "r1", 2, "Reistopf", "20 Minuten", 1_000L + 1_200_000L), t)
        s.entfernen(id)
        assertTrue(s.timer.value.isEmpty())
    }

    @Test
    fun wiederherstellenNachNeustart() {
        val id = speicher(0L).starten("r1", 2, "Reistopf", "5 Min.", Duration.ofMinutes(5))
        assertEquals(listOf(id), speicher(10_000L).timer.value.map { it.id })
    }

    @Test
    fun gemeldetBleibtGemeldet() {
        val s = speicher(0L)
        val id = s.starten("r1", 2, "Reistopf", "1 Minute", Duration.ofMinutes(1))
        s.gemeldet(id)
        assertTrue(speicher(120_000L).timer.value.single().gemeldet)
    }

    @Test
    fun alteAbgelaufeneVerschwinden() {
        speicher(0L).starten("r1", 2, "Reistopf", "1 Sekunde", Duration.ofSeconds(1))
        assertEquals(1, speicher(1_000L + ZWOELF_STUNDEN_MS).timer.value.size)
        assertTrue(speicher(1_001L + ZWOELF_STUNDEN_MS).timer.value.isEmpty())
    }

    @Test
    fun zweiGleicheTimerUnabhaengig() {
        val s = speicher(0L)
        val a = s.starten("r1", 2, "Reistopf", "20 Minuten", Duration.ofMinutes(20))
        val b = s.starten("r1", 2, "Reistopf", "20 Minuten", Duration.ofMinutes(20))
        assertNotEquals(a, b)
        s.entfernen(a)
        assertEquals(listOf(b), s.timer.value.map { it.id })
    }
}

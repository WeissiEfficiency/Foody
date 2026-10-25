package de.foody.feature.kochen

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class KochSitzungTest {
    private val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("test_sitzungen", Context.MODE_PRIVATE)

    private fun speicher(ms: Long) = KochSitzungSpeicher(prefs, Clock.fixed(Instant.ofEpochMilli(ms), ZoneOffset.UTC))

    @Test
    fun schreibenLesen() {
        val s = KochSitzung("r1", 3, 2, setOf(0, 4), 1_000L)
        speicher(1_000L).schreiben(s)
        assertEquals(s, speicher(2_000L).lesen("r1"))
        assertNull(speicher(2_000L).lesen("r2"))
    }

    @Test
    fun abgelaufenNach12Stunden() {
        speicher(0L).schreiben(KochSitzung("r1", 2, 1, emptySet(), 0L))
        assertEquals(1, speicher(ZWOELF_STUNDEN_MS).lesen("r1")?.seite)
        assertNull(speicher(ZWOELF_STUNDEN_MS + 1).lesen("r1"))
        // Einmal abgelaufen, bleibt sie weg – auch für eine Uhr davor
        assertNull(speicher(0L).lesen("r1"))
    }

    @Test
    fun loeschen() {
        speicher(0L).schreiben(KochSitzung("r1", 2, 1, emptySet(), 0L))
        speicher(0L).loeschen("r1")
        assertNull(speicher(0L).lesen("r1"))
    }
}

package de.foody.core.data

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class NichtGegessenSpeicherTest {
    private val kontext = RuntimeEnvironment.getApplication()
    private val prefs = kontext.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)
    private val heute = LocalDate.of(2026, 11, 7)

    @Before
    fun leeren() {
        prefs.edit().clear().commit()
    }

    @Test
    fun ohneEintragLeer() = runTest {
        assertTrue(NichtGegessenSpeicher(kontext).amTag(heute).first().isEmpty())
    }

    @Test
    fun gemerktNurAmSelbenTag() = runTest {
        val speicher = NichtGegessenSpeicher(kontext)
        speicher.merken(heute, "p1")
        speicher.merken(heute, "p2")
        assertEquals(setOf("p1", "p2"), NichtGegessenSpeicher(kontext).amTag(heute).first())
        assertTrue(speicher.amTag(heute.plusDays(1)).first().isEmpty())
    }

    @Test
    fun aeltereTageFallenBeimMerkenWeg() = runTest {
        // Die Liste wächst nicht: Wer heute etwas merkt, räumt gestern und früher auf
        val speicher = NichtGegessenSpeicher(kontext)
        speicher.merken(heute.minusDays(1), "alt")
        speicher.merken(heute, "neu")
        assertTrue(speicher.amTag(heute.minusDays(1)).first().isEmpty())
        assertEquals(setOf("neu"), speicher.amTag(heute).first())
    }
}

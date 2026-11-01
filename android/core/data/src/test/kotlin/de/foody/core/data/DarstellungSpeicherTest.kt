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
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class DarstellungSpeicherTest {
    private val kontext = RuntimeEnvironment.getApplication()
    private val prefs = kontext.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    @Test
    fun ohneWertSystem() = runTest {
        assertEquals(Darstellung.SYSTEM, DarstellungSpeicher(kontext).darstellung().first())
    }

    @Test
    fun v1WerteGelesen() = runTest {
        prefs.edit().putString(Einstellungsschluessel.DARSTELLUNG, "LIGHT").commit()
        assertEquals(Darstellung.HELL, DarstellungSpeicher(kontext).darstellung().first())
        prefs.edit().putString(Einstellungsschluessel.DARSTELLUNG, "DARK").commit()
        assertEquals(Darstellung.DUNKEL, DarstellungSpeicher(kontext).darstellung().first())
    }

    @Test
    fun unbekanntSystem() = runTest {
        prefs.edit().putString(Einstellungsschluessel.DARSTELLUNG, "PINK").commit()
        assertEquals(Darstellung.SYSTEM, DarstellungSpeicher(kontext).darstellung().first())
    }

    @Test
    fun setzenSchreibtV1Wert() {
        DarstellungSpeicher(kontext).setzen(Darstellung.DUNKEL)
        assertEquals("DARK", prefs.getString(Einstellungsschluessel.DARSTELLUNG, null))
        DarstellungSpeicher(kontext).setzen(Darstellung.HELL)
        assertEquals("LIGHT", prefs.getString(Einstellungsschluessel.DARSTELLUNG, null))
    }

    @Test
    fun farbweltOhneWertNull() = runTest {
        val speicher = DarstellungSpeicher(kontext)
        assertEquals(null, speicher.farbwelt().first())
        assertEquals(null, speicher.farbweltAktuell())
    }

    @Test
    fun farbweltGespeichertUndGemeldet() = runTest {
        val speicher = DarstellungSpeicher(kontext)
        speicher.farbweltSetzen("LAGUNE")
        assertEquals("LAGUNE", prefs.getString(Einstellungsschluessel.FARBWELT, null))
        assertEquals("LAGUNE", speicher.farbwelt().first())
        assertEquals("LAGUNE", DarstellungSpeicher(kontext).farbweltAktuell())
    }

    @Test
    fun flussMeldetAenderung() = runTest {
        val speicher = DarstellungSpeicher(kontext)
        assertEquals(Darstellung.SYSTEM, speicher.darstellung().first())
        speicher.setzen(Darstellung.DUNKEL)
        assertEquals(Darstellung.DUNKEL, speicher.darstellung().first())
    }
}

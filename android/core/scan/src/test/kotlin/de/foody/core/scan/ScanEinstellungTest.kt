package de.foody.core.scan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.foody.core.data.Einstellungsschluessel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Einstellung „Online-Produktsuche“: v1-Schlüssel in den Prefs `foody`, Standard an. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class ScanEinstellungTest {
    private val kontext: Context = ApplicationProvider.getApplicationContext()
    private val prefs = kontext.getSharedPreferences("foody", Context.MODE_PRIVATE)

    @Test
    fun standardAn() {
        val e = ScanEinstellung(kontext)
        assertTrue(e.erlaubt())
        assertTrue(e.onlineSuche.value)
    }

    @Test
    fun v1SchluesselWirdGelesen() {
        prefs.edit().putBoolean("onlineProduktsuche", false).commit()
        val e = ScanEinstellung(kontext)
        assertFalse(e.erlaubt())
        assertFalse(e.onlineSuche.value)
    }

    @Test
    fun setzenSchreibt() {
        val e = ScanEinstellung(kontext)
        e.setzen(false)
        assertFalse(e.erlaubt())
        assertFalse(e.onlineSuche.value)
        assertEquals(false, prefs.getBoolean(Einstellungsschluessel.ONLINE_SUCHE, true))
        // Eine neue Instanz (Prozessneustart) liest den geschriebenen Wert
        assertFalse(ScanEinstellung(kontext).erlaubt())
        e.setzen(true)
        assertTrue(ScanEinstellung(kontext).erlaubt())
    }
}

package de.foody.core.data

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class TageszielSpeicherTest {
    private val kontext = RuntimeEnvironment.getApplication()
    private val prefs = kontext.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)

    @Test
    fun ohneWertNull() = runTest {
        assertNull(TageszielSpeicher(kontext).tagesziel().first())
    }

    @Test
    fun gesetzterWert() = runTest {
        prefs.edit().putInt(Einstellungsschluessel.TAGESZIEL, 2000).commit()
        assertEquals(2000, TageszielSpeicher(kontext).tagesziel().first())
    }

    @Test
    fun nullGiltAlsOhneZiel() = runTest {
        prefs.edit().putInt(Einstellungsschluessel.TAGESZIEL, 0).commit()
        assertNull(TageszielSpeicher(kontext).tagesziel().first())
    }

    @Test
    fun setzenUndLesen() = runTest {
        prefs.edit().clear().commit()
        TageszielSpeicher(kontext).setzen(2000)
        assertEquals(2000, TageszielSpeicher(kontext).tagesziel().first())
    }

    @Test
    fun nullEntfernt() = runTest {
        prefs.edit().putInt(Einstellungsschluessel.TAGESZIEL, 2000).commit()
        TageszielSpeicher(kontext).setzen(null)
        assertNull(TageszielSpeicher(kontext).tagesziel().first())
    }

    @Test
    fun grenzen() = runTest {
        val speicher = TageszielSpeicher(kontext)
        speicher.setzen(500)
        assertEquals(500, speicher.tagesziel().first())
        speicher.setzen(10_000)
        assertEquals(10_000, speicher.tagesziel().first())
        assertFailsWith<IllegalArgumentException> { speicher.setzen(499) }
        assertFailsWith<IllegalArgumentException> { speicher.setzen(10_001) }
        assertEquals(10_000, speicher.tagesziel().first())
    }
}

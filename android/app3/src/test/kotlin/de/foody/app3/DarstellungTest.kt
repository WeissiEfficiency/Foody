package de.foody.app3

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.Darstellung
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class DarstellungTest {
    @get:Rule
    val compose = createComposeRule()

    private fun dunkel(d: Darstellung): Boolean {
        var ergebnis: Boolean? = null
        compose.setContent { ergebnis = istDunkel(d) }
        compose.waitForIdle()
        return ergebnis!!
    }

    @Test
    fun hellIstHell() = assertEquals(false, dunkel(Darstellung.HELL))

    @Test
    fun dunkelIstDunkel() = assertEquals(true, dunkel(Darstellung.DUNKEL))

    @Test
    @Config(qualifiers = "night")
    fun systemNachtIstDunkel() = assertEquals(true, dunkel(Darstellung.SYSTEM))

    @Test
    @Config(qualifiers = "notnight")
    fun systemTagIstHell() = assertEquals(false, dunkel(Darstellung.SYSTEM))
}

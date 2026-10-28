package de.foody.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Die Symbole der Statusleiste folgen der gewählten Darstellung, nicht dem Nachtmodus des Systems (wie v1). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "notnight")
class SystemleistenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun helleSymbole(): Boolean {
        val fenster = compose.activity.window
        return WindowCompat.getInsetsController(fenster, fenster.decorView).isAppearanceLightStatusBars
    }

    @Test
    fun dunkelBeiHellemSystemHelleSymbole() {
        var dunkel by mutableStateOf(true)
        compose.setContent { SystemleistenPassend(dunkel) }
        compose.waitForIdle()
        // App dunkel → helle Schrift auf dunkler Leiste, also KEIN „light status bar“
        assertFalse(helleSymbole())
        dunkel = false
        compose.waitForIdle()
        assertTrue(helleSymbole())
    }
}

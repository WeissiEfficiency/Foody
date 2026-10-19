package de.foody.core.designsystem.glas

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.GlasStufe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class GlasFlaecheTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun glasModusJeKombination() {
        assertEquals(GlasModus.UNSCHAERFE, glasModus(unschaerfe = true, hatHaze = true))
        assertEquals(GlasModus.TOENUNG, glasModus(unschaerfe = false, hatHaze = true))
        assertEquals(GlasModus.TOENUNG, glasModus(unschaerfe = true, hatHaze = false))
        assertEquals(GlasModus.TOENUNG, glasModus(unschaerfe = false, hatHaze = false))
    }

    @Test
    fun ohneHintergrundRendertTrotzdem() {
        compose.setContent {
            FoodyTheme { GlasFlaeche(GlasStufe.DICHT) { Text("Tagebuch") } }
        }
        compose.onNodeWithText("Tagebuch").assertExists()
    }

    @Test
    fun ohneUnschaerfeRendertTrotzdem() {
        compose.setContent {
            FoodyTheme(unschaerfe = false) {
                FoodyHintergrund { GlasFlaeche(GlasStufe.LEICHT) { Text("Navigation") } }
            }
        }
        compose.onNodeWithText("Navigation").assertExists()
    }

    @Test
    fun inhaltWirdGezeigt() {
        compose.setContent {
            FoodyTheme(dunkel = true) {
                FoodyHintergrund { GlasFlaeche(GlasStufe.DICHT) { Text("Senf-Krusten-Braten") } }
            }
        }
        compose.onNodeWithText("Senf-Krusten-Braten").assertExists()
    }
}

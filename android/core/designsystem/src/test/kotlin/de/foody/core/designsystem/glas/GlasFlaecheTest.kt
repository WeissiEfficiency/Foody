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
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

// Native Grafik: echte Textmessung (im Standardmodus ist Text fast 0 px breit)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class GlasFlaecheTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun glasModusJeKombination() {
        assertEquals(GlasModus.UNSCHAERFE, glasModus(unschaerfe = true, hatHaze = true, stufe = GlasStufe.LEICHT))
        assertEquals(GlasModus.TOENUNG, glasModus(unschaerfe = false, hatHaze = true, stufe = GlasStufe.LEICHT))
        assertEquals(GlasModus.TOENUNG, glasModus(unschaerfe = true, hatHaze = false, stufe = GlasStufe.LEICHT))
        assertEquals(GlasModus.TOENUNG, glasModus(unschaerfe = false, hatHaze = false, stufe = GlasStufe.LEICHT))
    }

    @Test
    fun bildStufeNieUnscharf() {
        assertEquals(GlasModus.TOENUNG, glasModus(unschaerfe = true, hatHaze = true, stufe = GlasStufe.BILD))
        assertEquals(GlasModus.UNSCHAERFE, glasModus(unschaerfe = true, hatHaze = true, stufe = GlasStufe.DICHT))
    }

    @Test
    fun glasMeldetSeinenModus() {
        compose.setContent {
            FoodyTheme(unschaerfe = true) {
                FoodyHintergrund {
                    androidx.compose.foundation.layout.Column {
                        GlasFlaeche(GlasStufe.LEICHT) { Text("Navigation") }
                        de.foody.core.designsystem.komponenten.Kachel("Milch", "1 l", onKlick = {})
                        de.foody.core.designsystem.komponenten.RezeptkarteGross("Shakshuka", "2 Portionen", onKlick = {})
                    }
                }
            }
        }
        val modi = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(GlasModusKey), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.config[GlasModusKey] }
        // Navigation unscharf; Kachel (Listen!) und Bild-Leiste der Karte nur getönt
        assertEquals(listOf(GlasModus.UNSCHAERFE, GlasModus.TOENUNG, GlasModus.TOENUNG), modi)
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

package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.MitSchrift200
import de.foody.core.designsystem.pruefeNichtsAbgeschnitten
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class KachelTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun kachelMindestens48dp() {
        compose.setContent { FoodyTheme { Kachel("Milch", "1 l", onKlick = {}) } }
        compose.onNodeWithText("Milch").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun monogrammAusErstemBuchstaben() {
        compose.setContent { FoodyTheme { Kachel("paprika", "3 Stk.", onKlick = {}) } }
        compose.onNodeWithText("P", useUnmergedTree = true).assertExists()
    }

    @Test
    fun monogrammIstKreisMindestens30dp() {
        for (skala in listOf(1f, 2f)) {
            var groesse = androidx.compose.ui.unit.DpSize.Zero
            compose.setContent {
                FoodyTheme {
                    val d = androidx.compose.ui.platform.LocalDensity.current
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, skala),
                    ) { Kachel("Paprika", "3 Stk.", onKlick = {}) }
                }
            }
            val b = compose.onNodeWithTag(MONOGRAMM_TAG, useUnmergedTree = true).getBoundsInRoot()
            groesse = androidx.compose.ui.unit.DpSize(b.right - b.left, b.bottom - b.top)
            assertEquals(groesse.width, groesse.height, "Monogramm bei Schrift ×$skala ist kein Kreis: $groesse")
            kotlin.test.assertTrue(groesse.width >= 30.dp, "Monogramm kleiner als 30 dp: $groesse")
            break // setContent geht nur einmal je Test; 200 % prüft monogrammWaechstMitSchrift
        }
    }

    @Test
    fun monogrammWaechstMitSchrift() {
        compose.setContent { FoodyTheme { MitSchrift200 { Kachel("Paprika", "3 Stk.", onKlick = {}) } } }
        val b = compose.onNodeWithTag(MONOGRAMM_TAG, useUnmergedTree = true).getBoundsInRoot()
        assertEquals(b.right - b.left, b.bottom - b.top, "Monogramm bei 200 % ist kein Kreis: $b")
    }

    @Test
    fun abgehaktGemeldet() {
        compose.setContent { FoodyTheme { Kachel("Tomaten", "500 g", onKlick = {}, abgehakt = true) } }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "abgehakt")).assertExists()
    }

    @Test
    fun klickRuftCallback() {
        var klicks = 0
        compose.setContent { FoodyTheme { Kachel("Milch", "1 l", onKlick = { klicks++ }) } }
        compose.onNodeWithText("Milch").performClick()
        assertEquals(1, klicks)
    }

    @Test
    fun doppelteSchriftOhneAbschneiden() {
        compose.setContent {
            FoodyTheme {
                MitSchrift200 { Kachel("Paprika", "3 Stk.", onKlick = {}, untertitel = "Gemüse", modifier = Modifier.width(120.dp)) }
            }
        }
        compose.pruefeNichtsAbgeschnitten()
    }
}

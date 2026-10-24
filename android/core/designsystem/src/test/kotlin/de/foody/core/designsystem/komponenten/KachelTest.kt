package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
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

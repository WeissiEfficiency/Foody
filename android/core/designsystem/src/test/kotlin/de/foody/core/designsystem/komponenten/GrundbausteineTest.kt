package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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

// Native Grafik: echte Textmessung (im Standardmodus ist Text fast 0 px breit)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class GrundbausteineTest {
    @get:Rule
    val compose = createComposeRule()

    private val ziele = listOf(
        NavZiel("Heute", Icons.Outlined.LightMode),
        NavZiel("Rezepte", Icons.AutoMirrored.Outlined.MenuBook),
        NavZiel("Woche", Icons.Outlined.CalendarMonth),
        NavZiel("Einkauf", Icons.Outlined.ShoppingBag),
    )

    @Test
    fun knopfMindestens48dp() {
        compose.setContent {
            FoodyTheme {
                Column {
                    FoodyKnopf("Kochen", onKlick = {})
                    FoodyKnopf("Eintragen", onKlick = {}, art = KnopfArt.UMRISS)
                    FoodyKnopf("Alle ansehen", onKlick = {}, art = KnopfArt.TEXT)
                }
            }
        }
        for (t in listOf("Kochen", "Eintragen", "Alle ansehen")) {
            compose.onNodeWithText(t).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun knopfDeaktiviertMeldetDas() {
        compose.setContent { FoodyTheme { FoodyKnopf("Kochen", onKlick = {}, aktiv = false) } }
        compose.onNodeWithText("Kochen").assertIsNotEnabled()
    }

    @Test
    fun chipMeldetAuswahl() {
        compose.setContent {
            FoodyTheme {
                Column {
                    FoodyChip("Favoriten", ausgewaehlt = true, onKlick = {})
                    FoodyChip("vegetarisch", ausgewaehlt = false, onKlick = {})
                }
            }
        }
        compose.onNodeWithText("Favoriten").assertIsSelected()
        compose.onNodeWithText("vegetarisch").assertIsNotSelected()
        compose.onNodeWithText("vegetarisch").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun abschnittskopfIstUeberschrift() {
        compose.setContent { FoodyTheme { Abschnittskopf("Einkauf", linkText = "6 offen", onLink = {}) } }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
        compose.onNodeWithText("6 offen").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun augenbraueInGrossbuchstaben() {
        compose.setContent { FoodyTheme { Augenbraue("Abendessen · 18:30") } }
        compose.onNodeWithText("ABENDESSEN · 18:30").assertExists()
    }

    @Test
    fun navigationWaehltZiel() {
        var gewaehlt = -1
        compose.setContent {
            FoodyTheme { FoodyNavigationsleiste(ziele, aktiv = 0, onWahl = { gewaehlt = it }) }
        }
        compose.onNodeWithText("Woche", useUnmergedTree = true).performClick()
        assertEquals(2, gewaehlt)
        compose.onNodeWithText("Heute").assertIsSelected()
    }

    @Test
    fun railWaehltZiel() {
        var gewaehlt = -1
        compose.setContent {
            FoodyTheme { FoodyNavigationsRail(ziele, aktiv = 1, onWahl = { gewaehlt = it }) }
        }
        compose.onNodeWithText("Einkauf", useUnmergedTree = true).performClick()
        assertEquals(3, gewaehlt)
    }

    @Test
    fun doppelteSchriftOhneAbschneiden() {
        compose.setContent {
            FoodyTheme {
                MitSchrift200 {
                    Column(Modifier.width(411.dp)) {
                        FoodyKnopf("Kochen", onKlick = {})
                        FoodyChip("Favoriten", ausgewaehlt = true, onKlick = {})
                        Abschnittskopf("Einkauf", linkText = "6 offen", onLink = {})
                        FoodyNavigationsleiste(ziele, aktiv = 0, onWahl = {})
                    }
                }
            }
        }
        compose.pruefeNichtsAbgeschnitten()
    }
}

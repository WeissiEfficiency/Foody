package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
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
class LeererZustandTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun zeileAktionKlickbar() {
        var klicks = 0
        compose.setContent { FoodyTheme { LeererZustandZeile("Noch kein Snack eingetragen", "Eintragen", onAktion = { klicks++ }) } }
        compose.onNodeWithText("Eintragen").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, klicks)
    }

    @Test
    fun flaecheOhneAktion() {
        compose.setContent {
            FoodyTheme {
                LeererZustandFlaeche(Icons.Outlined.ShoppingBag, "Nichts einzukaufen", "Die Liste ist leer.", aktionText = null, onAktion = null)
            }
        }
        compose.onNodeWithText("Nichts einzukaufen").assertExists()
        assertEquals(0, compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun doppelteSchriftOhneAbschneiden() {
        compose.setContent {
            FoodyTheme {
                MitSchrift200 {
                    Column(Modifier.width(360.dp)) {
                        LeererZustandZeile("Noch kein Snack eingetragen", "Eintragen", onAktion = {})
                        LeererZustandFlaeche(Icons.Outlined.ShoppingBag, "Nichts einzukaufen", "Die Liste ist leer.", "Artikel hinzufügen", {})
                    }
                }
            }
        }
        compose.pruefeNichtsAbgeschnitten()
    }
}

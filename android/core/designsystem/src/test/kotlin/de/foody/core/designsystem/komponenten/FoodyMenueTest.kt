package de.foody.core.designsystem.komponenten

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class FoodyMenueTest {
    @get:Rule
    val compose = createComposeRule()

    /** Zwei Punkte, der gewählte mit Haken; ein Tipp ruft nur dessen Aktion, danach ist das Menü zu. */
    @Test
    fun zeigtPunkteUndSchliesst() {
        var offen by mutableStateOf(true)
        val geklickt = mutableListOf<String>()
        compose.setContent {
            FoodyTheme {
                FoodyMenue(offen, onSchliessen = { offen = false }) {
                    FoodyMenuePunkt("Neueste zuerst", { offen = false; geklickt += "neueste" })
                    FoodyMenuePunkt("Name A–Z", { offen = false; geklickt += "name" }, gewaehlt = true)
                }
            }
        }
        compose.onAllNodesWithTag("menue-haken", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("menue-haken", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Name A–Z").assertIsSelected()
        compose.onNodeWithText("Neueste zuerst").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("neueste"), geklickt)
        compose.onNodeWithText("Neueste zuerst").assertDoesNotExist()
        compose.onNodeWithText("Name A–Z").assertDoesNotExist()
    }
}

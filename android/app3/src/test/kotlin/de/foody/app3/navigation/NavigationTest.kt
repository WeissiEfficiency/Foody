package de.foody.app3.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class NavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    private fun tab(text: String) = compose.onNode(hasText(text) and isSelectable())

    private fun starten() {
        compose.setContent {
            nav = rememberNavController()
            FoodyTheme { Hauptgeruest(nav, Bildschirme.platzhalter) }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun handyZeigtLeiste() {
        starten()
        compose.onNodeWithTag(Hauptgeruest.LEISTE).assertExists()
        compose.onNodeWithTag(Hauptgeruest.RAIL).assertDoesNotExist()
        tab("Woche").performClick()
        tab("Woche").assertIsSelected()
        compose.onNodeWithText(Platzhalter.TEXT).assertExists()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun tabletZeigtRail() {
        starten()
        compose.onNodeWithTag(Hauptgeruest.RAIL).assertExists()
        compose.onNodeWithTag(Hauptgeruest.LEISTE).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun zurueckVomTabFuehrtZuHeute() {
        starten()
        tab("Einkauf").performClick()
        tab("Einkauf").assertIsSelected()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        tab("Heute").assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun heuteNachDetailUndTabwechselFuehrtNachHeute() {
        starten()
        compose.runOnUiThread { nav.navigate(RezeptDetail("r1")) }
        compose.onNodeWithText("Rezept r1").assertExists()
        tab("Heute").assertIsSelected()
        tab("Rezepte").performClick()
        tab("Heute").performClick()
        tab("Heute").assertIsSelected()
        compose.onNodeWithText("Rezept r1").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun heuteTabAufDemTagebuchFuehrtNachHeute() {
        starten()
        compose.runOnUiThread { nav.navigate(TagebuchTag("2026-10-24")) }
        compose.onNodeWithText("Tagebuch 2026-10-24").assertExists()
        tab("Heute").performClick()
        compose.onNodeWithText("Tagebuch 2026-10-24").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun aktiverTabErneutFuehrtZurWurzel() {
        starten()
        tab("Rezepte").performClick()
        compose.runOnUiThread { nav.navigate(RezeptDetail("r2")) }
        tab("Rezepte").assertIsSelected()
        tab("Rezepte").performClick()
        compose.onNodeWithText("Rezept r2").assertDoesNotExist()
        compose.onNodeWithText(Platzhalter.TEXT).assertExists()
    }
}

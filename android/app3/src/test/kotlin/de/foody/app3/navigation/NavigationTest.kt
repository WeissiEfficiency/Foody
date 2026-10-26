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
import kotlin.test.assertTrue

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

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun rezepteTabZeigtListe() {
        starten()
        tab("Rezepte").performClick()
        compose.onNodeWithText("Rezeptliste").assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun einkaufTabZeigtListe() {
        starten()
        tab("Einkauf").performClick()
        compose.onNodeWithText("Einkaufsliste").assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun zumEinkaufWechseltTab() {
        // „Ansehen“ nach „Auf die Einkaufsliste“ im Rezeptdetail
        starten()
        tab("Rezepte").performClick()
        compose.runOnUiThread { nav.navigate(RezeptDetail("r1")) }
        compose.onNodeWithText("Rezept r1").assertExists()
        compose.runOnUiThread { nav.zumEinkauf() }
        tab("Einkauf").assertIsSelected()
        compose.onNodeWithText("Einkaufsliste").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        tab("Heute").assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun zumEinkaufAusFruehererListe() {
        // „Zur aktuellen Liste“ auf einer früheren (gelöschten) Liste: zurück an den Anfang des Tabs
        starten()
        tab("Einkauf").performClick()
        compose.runOnUiThread { nav.navigate(EinkaufListe("alt")) }
        compose.onNodeWithText("Liste alt").assertExists()
        compose.runOnUiThread { nav.zumEinkauf() }
        compose.onNodeWithText("Einkaufsliste").assertExists()
        compose.onNodeWithText("Liste alt").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun zumEinkaufNichtAufGemerkteFruehereListe() {
        // „Ansehen“ nach Rezept → Liste: die aktuelle Liste, auch wenn im Einkauf-Tab noch eine frühere offen war
        starten()
        tab("Einkauf").performClick()
        compose.runOnUiThread { nav.navigate(EinkaufListe("alt")) }
        compose.onNodeWithText("Liste alt").assertExists()
        tab("Rezepte").performClick()
        compose.runOnUiThread { nav.navigate(RezeptDetail("r1")) }
        compose.onNodeWithText("Rezept r1").assertExists()
        compose.runOnUiThread { nav.zumEinkauf() }
        tab("Einkauf").assertIsSelected()
        compose.onNodeWithText("Einkaufsliste").assertExists()
        compose.onNodeWithText("Liste alt").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun einstellungenZumServer() {
        starten()
        compose.runOnUiThread { nav.navigate(Einstellungen) }
        compose.onNodeWithText("Einstellungen").assertExists()
        compose.runOnUiThread { nav.navigate(ServerVerbinden) }
        compose.onNodeWithText("Server verbinden").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Server verbinden").assertDoesNotExist()
        compose.onNodeWithText("Einstellungen").assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun einstellungenZumZutatenkatalog() {
        starten()
        compose.runOnUiThread { nav.navigate(Einstellungen) }
        compose.runOnUiThread { nav.navigate(Zutaten) }
        compose.onNodeWithText("Zutatenkatalog").assertExists()
        compose.runOnUiThread { nav.navigate(Zutat("z1")) }
        compose.onNodeWithText("Zutat z1").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Zutat z1").assertDoesNotExist()
        compose.onNodeWithText("Zutatenkatalog").assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun wocheTabZeigtPlan() {
        starten()
        tab("Woche").performClick()
        compose.onNodeWithText("Wochenplan").assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun kochenOhneLeiste() {
        starten()
        compose.runOnUiThread { nav.navigate(Kochen("r1", 2)) }
        compose.onNodeWithText("Rezept r1 · 2 Portionen").assertExists()
        compose.onNodeWithTag(Hauptgeruest.LEISTE).assertDoesNotExist()
        compose.runOnUiThread { nav.popBackStack() }
        compose.onNodeWithTag(Hauptgeruest.LEISTE).assertExists()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun kochenOhneRail() {
        starten()
        compose.runOnUiThread { nav.navigate(Kochen("r1", 2)) }
        compose.onNodeWithTag(Hauptgeruest.RAIL).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun kochenZuRezeptAnpassen() {
        starten()
        compose.runOnUiThread {
            nav.navigate(RezeptDetail("r1"))
            nav.navigate(Kochen("r1", 2))
            nav.zuRezeptAnpassen("r1", 2)
        }
        compose.onNodeWithText("Rezept r1 bearbeiten · gekocht mit 2").assertExists()
        compose.runOnUiThread {
            assertTrue(runCatching { nav.getBackStackEntry<Kochen>() }.isFailure, "Kochen liegt nicht mehr im Stapel")
            nav.popBackStack()
        }
        compose.onNodeWithText("Rezept r1").assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun rezeptNeuUndBearbeiten() {
        starten()
        compose.runOnUiThread { nav.navigate(RezeptNeu) }
        compose.onNodeWithText("Neues Rezept").assertExists()
        compose.runOnUiThread { nav.navigate(RezeptBearbeiten("r1")) }
        compose.onNodeWithText("Rezept r1 bearbeiten").assertExists()
    }
}

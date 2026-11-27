package de.foody.feature.kochen.stil

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.grauen.GRAUEN_SIEGEL_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Umblättern beim Kochen im Grauen: „Zurück“ und „Weiter“ (auch „Beginnen“, „Vollenden“) sind ein gleiches Paar aus
 * Wort und gezeichnetem Pfeil, kein Siegel neben bloßem Text.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class KochKnoepfeGrauenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun pruefen(variante: StilVariante, seite: Int, wort: String) {
        var neueSeite = -1
        compose.setContent {
            FoodyTheme(stil = FoodyStil.GRAUEN, variante = variante) {
                KochKnoepfeStil(kochArt(FoodyStil.GRAUEN), seite, 3, onSeite = { neueSeite = it }, onFertig = {})
            }
        }
        compose.onAllNodesWithTag(GRAUEN_SIEGEL_TAG, useUnmergedTree = true).fetchSemanticsNodes().let {
            assertEquals(0, it.size, "$variante Seite $seite: kein Siegel beim Umblättern")
        }
        compose.onNodeWithText(wort, useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Zurück").assertHasClickAction()
        if (seite == 1) {
            compose.onNodeWithContentDescription("Weiter").performClick()
            assertEquals(2, neueSeite)
        }
    }

    @Test fun beginnenImBuch() = pruefen(StilVariante.IMMERSIV, 0, "Beginnen")

    @Test fun weiterImBuch() = pruefen(StilVariante.IMMERSIV, 1, "Weiter")

    @Test fun vollendenOminoes() = pruefen(StilVariante.RUHIG, 2, "Vollenden")
}

package de.foody.core.designsystem.stil

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyNavigationsleiste
import de.foody.core.designsystem.komponenten.NavZiel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StilTexteTest {
    @get:Rule
    val compose = createComposeRule()

    private fun wort(stil: FoodyStil, variante: StilVariante = StilVariante.IMMERSIV) {
        compose.setContent {
            FoodyTheme(stil = stil, variante = variante) {
                Text(stilText("Heute", sage = "Chronik", anime = "Heute!", bayrisch = "Heid", grauen = "Das Auge", kreuzer = "Tagessystem"))
            }
        }
    }

    @Test
    fun jederStilSeinWort() {
        wort(FoodyStil.BAYRISCH)
        compose.onNodeWithText("Heid").assertExists()
    }

    @Test
    fun grauenSeinWort() {
        wort(FoodyStil.GRAUEN)
        compose.onNodeWithText("Das Auge").assertExists()
    }

    @Test
    fun ruhigBehaeltNormalesWort() {
        wort(FoodyStil.KREUZER, StilVariante.RUHIG)
        compose.onNodeWithText("Heute").assertExists()
    }

    @Test
    fun ohneStilwortNormal() {
        compose.setContent { FoodyTheme(stil = FoodyStil.EDO) { Text(stilText("Woche", bayrisch = "Wochn")) } }
        compose.onNodeWithText("Woche").assertExists()
    }

    @Test
    fun navigationLiestNormalenNamen() {
        compose.setContent {
            FoodyTheme(stil = FoodyStil.KREUZER) {
                FoodyNavigationsleiste(listOf(NavZiel("Brücke", Icons.Outlined.LightMode, beschreibung = "Heute")), 0, {})
            }
        }
        compose.onNodeWithText("Brücke", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Heute").assertExists()
    }
}

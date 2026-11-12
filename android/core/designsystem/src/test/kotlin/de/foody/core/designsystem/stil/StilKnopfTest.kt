package de.foody.core.designsystem.stil

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** Der gemeinsame Knopf in den Stilen R–V (Immersiv): Knopf des Stils, 48 dp, inaktiv nicht klickbar. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class StilKnopfTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun jederStilJedeArtKlicktUndHat48dp() {
        val stile = listOf(FoodyStil.ANIME, FoodyStil.EDO, FoodyStil.BAYRISCH, FoodyStil.GRAUEN, FoodyStil.KREUZER)
        var stil by mutableStateOf(stile.first())
        var klicks = 0
        compose.setContent {
            FoodyTheme(stil = stil, variante = StilVariante.IMMERSIV) {
                Column {
                    FoodyKnopf("Primär", { klicks++ })
                    FoodyKnopf("Umriss", { klicks++ }, art = KnopfArt.UMRISS)
                    FoodyKnopf("Text", { klicks++ }, art = KnopfArt.TEXT)
                }
            }
        }
        stile.forEach { s ->
            stil = s
            compose.waitForIdle()
            listOf("Primär", "Umriss", "Text").forEach { t ->
                compose.onNodeWithText(t).assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
            }
        }
        assertEquals(stile.size * 3, klicks)
    }

    @Test
    fun inaktivNichtKlickbar() {
        var klicks = 0
        compose.setContent {
            FoodyTheme(stil = FoodyStil.KREUZER, variante = StilVariante.IMMERSIV) { FoodyKnopf("Übernehmen", { klicks++ }, aktiv = false) }
        }
        compose.onNodeWithText("Übernehmen").assertIsNotEnabled().performClick()
        assertEquals(0, klicks)
    }
}

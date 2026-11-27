package de.foody.core.designsystem.stil.grauen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.RezeptPlatzhalter
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * Der Bildplatzhalter (Editor, Rezept ohne Foto) ist im Necronomicon eine gezeichnete Bildtafel auf dem Pergament,
 * kein brauner Farbblock mit Besteck.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class GrauenBildtafelTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun platzhalterImBuchIstTafelAufPergament() {
        compose.setContent {
            FoodyTheme(stil = FoodyStil.GRAUEN, variante = StilVariante.IMMERSIV) {
                Box(Modifier.size(240.dp, 140.dp).background(FoodyTheme.farben.grund).testTag("tafel")) {
                    RezeptPlatzhalter(Modifier.size(240.dp, 140.dp))
                }
            }
        }
        val pixel = compose.onNodeWithTag("tafel").captureToImage().toPixelMap()
        var hell = 0
        var alle = 0
        for (y in 0 until pixel.height step 3) {
            for (x in 0 until pixel.width step 3) {
                alle++
                if (pixel[x, y].luminance() > 0.35f) hell++
            }
        }
        val anteil = hell.toFloat() / alle
        assertTrue(anteil > 0.8f, "Platzhalter im Buch nur zu $anteil hell (Farbblock statt Tafel?)")
        assertTrue(Color(pixel[pixel.width / 2, pixel.height / 2].value).alpha > 0f)
    }
}

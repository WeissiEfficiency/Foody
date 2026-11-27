package de.foody.feature.rezepte.liste.stil

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * Das Regalbrett der Bibliothek (Plan „Zwei Gesichter“, Schritt 5): im Necronomicon ein helles Brett mit Federstrichen
 * statt eines schwarzen Balkens auf dem Pergament; in Ominös bleibt es dunkles Holz.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class BrettFederTest {
    /** Anteil der dunklen Pixel (Leuchtdichte unter 0,1) im Brett, 12 px am unteren Rand eines 100 × 40 Felds. */
    private fun dunkelAnteil(feder: Boolean): Float {
        val bild = ImageBitmap(100, 48)
        val leinwand = Canvas(bild)
        leinwand.drawRect(0f, 0f, 100f, 48f, Paint().apply { color = Color(0xFFCDBB93) })
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, leinwand, Size(100f, 40f)) { brettMalen(feder) }
        val pixel = bild.toPixelMap()
        var dunkel = 0
        for (y in 28 until 40) for (x in 0 until 100) if (pixel[x, y].luminance() < 0.1f) dunkel++
        return dunkel / 1200f
    }

    @Test
    fun brettImBuchIstKeinSchwarzerBalken() {
        val anteil = dunkelAnteil(feder = true)
        assertTrue(anteil < 0.3f, "Brett im Buch zu $anteil dunkel")
    }

    @Test
    fun brettInOminoesBleibtDunklesHolz() {
        val anteil = dunkelAnteil(feder = false)
        assertTrue(anteil > 0.8f, "Brett in Ominös nur zu $anteil dunkel")
    }
}

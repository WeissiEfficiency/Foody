package de.foody.feature.woche.stil

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import kotlin.math.hypot
import kotlin.test.assertTrue

/**
 * Die Monde der Woche im Necronomicon (Plan „Zwei Gesichter“, Schritt 5): Federzeichnungen auf Pergament, keine
 * schwarzen Scheiben. Ein Neumond ist im Buch zu weniger als einem Viertel dunkel; in Ominös bleibt er Nacht.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class MondFederTest {
    private val pergament = Color(0xFFCDBB93)
    private val blut = Color(0xFF5C2318)

    /** Anteil der dunklen Pixel (Leuchtdichte unter 0,1) innerhalb der Mondscheibe. */
    private fun dunkelAnteil(anteil: Float, feder: Boolean): Float {
        val bild = ImageBitmap(44, 44)
        val leinwand = Canvas(bild)
        leinwand.drawRect(0f, 0f, 44f, 44f, androidx.compose.ui.graphics.Paint().apply { color = pergament })
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, leinwand, Size(44f, 44f)) {
            mondMalen(anteil, leer = anteil == 0f, heute = false, an = false, glut = blut, rand = Color(0xFF33291B), feder = feder)
        }
        val pixel = bild.toPixelMap()
        var innen = 0
        var dunkel = 0
        for (y in 0 until 44) {
            for (x in 0 until 44) {
                if (hypot(x - 22f + 0.5f, y - 22f + 0.5f) > 16f) continue
                innen++
                if (pixel[x, y].luminance() < 0.1f) dunkel++
            }
        }
        return dunkel.toFloat() / innen
    }

    @Test
    fun neumondIstImBuchKeineSchwarzeScheibe() {
        val anteil = dunkelAnteil(0f, feder = true)
        assertTrue(anteil < 0.25f, "Neumond im Buch zu $anteil dunkel")
    }

    @Test
    fun halbmondIstImBuchSchraffiert() {
        val anteil = dunkelAnteil(0.5f, feder = true)
        assertTrue(anteil < 0.25f, "Halbmond im Buch zu $anteil dunkel")
    }

    @Test
    fun neumondBleibtInOminoesNacht() {
        val anteil = dunkelAnteil(0f, feder = false)
        assertTrue(anteil > 0.8f, "Neumond in Ominös nur zu $anteil dunkel")
    }
}

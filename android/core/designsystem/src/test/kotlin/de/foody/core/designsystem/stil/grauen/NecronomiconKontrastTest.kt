package de.foody.core.designsystem.stil.grauen

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.R
import de.foody.core.designsystem.komponenten.LEDER
import de.foody.core.designsystem.komponenten.LEDER_NEBEN
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.stilWerte
import de.foody.core.designsystem.token.kontrast
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * Kontrast im Necronomicon über `KontrastTest` hinaus (Plan „Zwei Gesichter“, Schritt 4): Text auf dem dunkelsten
 * Fleck des Pergaments (ohne den verkohlten Rand, auf dem nie Text liegt), Messing auf Leder und auf dem Siegel.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class NecronomiconKontrastTest {
    private val buch = stilWerte(FoodyStil.GRAUEN, StilVariante.IMMERSIV)

    @Test
    fun textHaeltAufDemDunkelstenFleck() {
        val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val bild = BitmapFactory.decodeResource(res, R.drawable.grauen_pergament, BitmapFactory.Options().apply { inScaled = false })
        // Text hält 16 dp Abstand zum Rand, das sind gut 4 % der Breite; geprüft wird ab 4 %
        val rand = (bild.width * 0.04f).toInt()
        var dunkelster = Color.White
        for (y in rand until bild.height - rand step 2) {
            for (x in rand until bild.width - rand step 2) {
                val c = Color(bild.getPixel(x, y))
                if (kontrast(c, Color.Black) < kontrast(dunkelster, Color.Black)) dunkelster = c
            }
        }
        listOf("Text" to buch.farben.text, "Nebentext" to buch.farben.nebentext, "Zier" to buch.zier).forEach { (name, farbe) ->
            val k = kontrast(farbe, dunkelster)
            assertTrue(k >= 4.5, "$name auf dem dunkelsten Fleck $dunkelster: $k")
        }
    }

    @Test
    fun messingAufLederUndSiegel() {
        val leder = LEDER + Color(0xFF1C1915)
        leder.forEach { grund ->
            assertTrue(kontrast(Grauen.messing, grund) >= 4.5, "Messing auf $grund")
            assertTrue(kontrast(LEDER_NEBEN, grund) >= 4.5, "Inaktiv auf $grund")
        }
    }
}

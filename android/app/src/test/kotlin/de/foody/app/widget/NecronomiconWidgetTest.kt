package de.foody.app.widget

import android.app.Application
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.StilWahl
import de.foody.core.designsystem.R
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.grauen.Grauen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Widgets im Necronomicon (Plan „Zwei Gesichter“, Schritt 6): Pergament mit Tinte und Blut statt der grünen Tafel;
 * Ominös bleibt dunkel mit Glut.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class NecronomiconWidgetTest {
    private val kontext = ApplicationProvider.getApplicationContext<Application>()
    private val buch = StilWahl(FoodyStil.GRAUEN.name, "IMMERSIV")
    private val ominoes = StilWahl(FoodyStil.GRAUEN.name, "RUHIG")

    @Test
    fun karteImBuchAufPergament() {
        for (zettel in listOf(false, true)) {
            val k = kartenAussehen(kontext, buch, zettel)
            assertEquals(R.drawable.grauen_pergament, k.flaeche.bild, "Zettel=$zettel: Pergament")
            assertTrue(ColorUtils.calculateLuminance(k.flaeche.farbe) > 0.4, "Zettel=$zettel: helle Fläche")
            assertEquals(Grauen.blut.toArgb(), k.akzent, "Zettel=$zettel: Blut als Akzent")
            assertTrue(ColorUtils.calculateContrast(k.akzent, k.flaeche.farbe) >= 4.5, "Zettel=$zettel: Blut lesbar")
        }
    }

    @Test
    fun ominoesBleibtDunkel() {
        val k = kartenAussehen(kontext, ominoes)
        assertNull(k.flaeche.bild)
        assertTrue(ColorUtils.calculateLuminance(k.flaeche.farbe) < 0.1, "dunkle Tafel")
    }

    @Test
    fun knopfImBuchAufPergament() {
        val a = assertNotNull(knopfAussehen(buch, KnopfArt.SCAN))
        assertEquals(R.drawable.grauen_pergament, a.bild)
        assertEquals(Grauen.blut.toArgb(), a.symbol)
        assertTrue(ColorUtils.calculateContrast(a.inhalt, a.flaeche) >= 4.5, "Schrift auf dem Knopf")
        assertNull(assertNotNull(knopfAussehen(ominoes, KnopfArt.SCAN)).bild)
    }
}

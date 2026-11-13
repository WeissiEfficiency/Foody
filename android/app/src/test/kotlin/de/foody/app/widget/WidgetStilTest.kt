package de.foody.app.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.res.ColorStateList
import android.graphics.Outline
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.R
import de.foody.core.data.DarstellungSpeicher
import de.foody.core.data.StilWahl
import de.foody.core.designsystem.stil.FoodyStil
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Knopf-Widgets im gewählten Stil: Milchglas bleibt wie bisher, die Stile setzen Fläche, Schrift, Rand, Schatten und in
 * Immersiv das Wort des Entwurfs; ein Wechsel des Stils zeichnet vorhandene Widgets neu.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class WidgetStilTest {
    private val kontext = ApplicationProvider.getApplicationContext<Application>()
    private val darstellung = DarstellungSpeicher(kontext)

    private fun waehlen(stil: FoodyStil, variante: String = "IMMERSIV") {
        darstellung.stilSetzen(stil.name)
        darstellung.stilVarianteSetzen(variante)
    }

    private fun widget(): View {
        val manager = shadowOf(AppWidgetManager.getInstance(kontext))
        return manager.getViewFor(manager.createWidget(ScanWidget::class.java, R.layout.widget_scan))
    }

    private fun View.text() = findViewById<TextView>(R.id.widget_text).text.toString()

    private fun View.sichtbar(id: Int) = findViewById<View>(id).visibility == View.VISIBLE

    @Test
    fun milchglasWieBisher() {
        waehlen(FoodyStil.MILCHGLAS)
        val w = widget()
        assertEquals("Scannen", w.text())
        assertEquals(false, w.sichtbar(R.id.widget_schatten))
        assertEquals(false, w.sichtbar(R.id.widget_rand))
        assertNull(knopfAussehen(StilWahl(FoodyStil.MILCHGLAS.name), KnopfArt.SCAN))
    }

    @Test
    fun animeImmersivMitSchattenUndWort() {
        waehlen(FoodyStil.ANIME)
        val w = widget()
        assertEquals("SCAN!", w.text())
        assertEquals(true, w.sichtbar(R.id.widget_schatten))
        assertEquals(true, w.sichtbar(R.id.widget_rand))
        val flaeche = assertNotNull(w.findViewById<View>(R.id.widget_knopf).backgroundTintList)
        assertEquals(ColorStateList.valueOf(0xFF3EC6E0.toInt()).defaultColor, flaeche.defaultColor)
        // Rundung über den Umriss: 18 dp außen, innen um den Rand (2,5 dp) kleiner
        val knopf = w.findViewById<View>(R.id.widget_knopf)
        knopf.layout(0, 0, 400, 120)
        val umriss = Outline()
        knopf.outlineProvider.getOutline(knopf, umriss)
        assertEquals(15.5f * kontext.resources.displayMetrics.density, umriss.radius, 0.01f)
        assertEquals(true, knopf.clipToOutline)
    }

    @Test
    fun ruhigOhneSchattenUndOhneStilwort() {
        waehlen(FoodyStil.ANIME, "RUHIG")
        val w = widget()
        assertEquals("Scannen", w.text())
        assertEquals(false, w.sichtbar(R.id.widget_schatten))
        assertEquals(true, w.sichtbar(R.id.widget_rand))
    }

    @Test
    fun woerterUndRaender() {
        assertEquals("DEUTEN", knopfAussehen(StilWahl("SAGE", "IMMERSIV"), KnopfArt.FOTO)?.text)
        assertEquals("FOTO!", knopfAussehen(StilWahl("ANIME", "IMMERSIV"), KnopfArt.FOTO)?.text)
        for (stil in listOf(FoodyStil.EDO, FoodyStil.BAYRISCH, FoodyStil.GRAUEN, FoodyStil.KREUZER)) {
            val a = assertNotNull(knopfAussehen(StilWahl(stil.name, "IMMERSIV"), KnopfArt.SCAN))
            assertNull(a.text, "${stil.name}: normales Wort")
            assertEquals(false, a.schatten)
        }
        // Bayrisch nimmt den gewählten Akzent als Fläche
        val gruen = FoodyStil.BAYRISCH.akzente[1]
        assertEquals(
            androidx.compose.ui.graphics.Color(0xFF2E5E3A).value,
            androidx.compose.ui.graphics.Color(knopfAussehen(StilWahl("BAYRISCH", "IMMERSIV", gruen.name), KnopfArt.SCAN)!!.flaeche).value,
        )
    }

    @Test
    fun wechselZeichnetNeu() {
        waehlen(FoodyStil.MILCHGLAS)
        val manager = shadowOf(AppWidgetManager.getInstance(kontext))
        val id = manager.createWidget(ScanWidget::class.java, R.layout.widget_scan)
        waehlen(FoodyStil.ANIME)
        alleAktualisieren(kontext)
        assertEquals("SCAN!", manager.getViewFor(id).text())
    }
}

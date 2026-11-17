package de.foody.app.widget

import android.app.Application
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Jedes Widget passt mit seiner Mindestgröße in die Zielzellen (`targetCellWidth`/`-Height`), nach der Faustregel
 * 70 × n − 30 dp je Richtung. Sonst legt ein Launcher es größer an: Im Pixel-Launcher war „Woche“ mit 80 dp
 * Mindesthöhe 4 × 2 statt 4 × 1 und ließ sich nicht kleiner ziehen (Canvas-Abgleich 2026-11-17).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class WidgetGroesseTest {
    private val kontext = ApplicationProvider.getApplicationContext<Application>()

    private val widgets = mapOf(
        "Einkauf" to R.xml.einkauf_widget_info,
        "Eintragen" to R.xml.eintragen_widget_info,
        "Foto" to R.xml.foto_widget_info,
        "Heute" to R.xml.heute_widget_info,
        "Kalorien" to R.xml.kalorien_widget_info,
        "Scan" to R.xml.scan_widget_info,
        "Woche" to R.xml.woche_widget_info,
    )

    /** Attribute des `appwidget-provider`-Elements, Maße in dp. */
    private fun werte(id: Int): Map<String, Float> {
        val p: XmlResourceParser = kontext.resources.getXml(id)
        do p.next() while (p.eventType != XmlResourceParser.START_TAG || p.name != "appwidget-provider")
        return (0 until p.attributeCount).mapNotNull { i ->
            p.getAttributeValue(i).removeSuffix("dip").removeSuffix("dp").toFloatOrNull()?.let { p.getAttributeName(i) to it }
        }.toMap()
    }

    private fun hoechstens(zellen: Float) = 70f * zellen - 30f

    @Test
    fun mindestgroessePasstInZielzellen() {
        widgets.forEach { (name, id) ->
            val w = werte(id)
            val breit = w.getValue("targetCellWidth")
            val hoch = w.getValue("targetCellHeight")
            listOf("minWidth" to breit, "minResizeWidth" to breit, "minHeight" to hoch, "minResizeHeight" to hoch).forEach { (attr, zellen) ->
                val dp = w.getValue(attr)
                assertTrue(dp <= hoechstens(zellen), "$name: $attr = $dp dp passt nicht in ${zellen.toInt()} Zellen (höchstens ${hoechstens(zellen)} dp)")
            }
        }
    }
}

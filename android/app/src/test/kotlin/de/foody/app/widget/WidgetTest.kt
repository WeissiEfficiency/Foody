package de.foody.app.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.MainActivity
import de.foody.app.R
import de.foody.app.navigation.FOTO_LINK
import de.foody.app.navigation.SCAN_LINK
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Widgets „Scannen“, „KI-Foto“, „Einkauf“ und „Schnell eintragen“: im Manifest angemeldet (für fremde Launcher sichtbar). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class WidgetTest {
    private val kontext = ApplicationProvider.getApplicationContext<Application>()

    private fun imManifest(widget: Class<out AppWidgetProvider>, info: Int) {
        val empfaenger = kontext.packageManager.queryBroadcastReceivers(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(kontext.packageName), PackageManager.GET_META_DATA,
        )
        val gefunden = assertNotNull(empfaenger.firstOrNull { it.activityInfo.name == widget.name }, "${widget.simpleName} fehlt")
        assertEquals(info, gefunden.activityInfo.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER))
        // Hersteller-Launcher lesen die Widget-Liste teils selbst über den PackageManager: nicht exportiert fehlte es dort
        assertTrue(gefunden.activityInfo.exported, "für fremde Launcher sichtbar")
    }

    private fun tippStartet(widget: Class<out AppWidgetProvider>, layout: Int, link: String) {
        val manager = shadowOf(AppWidgetManager.getInstance(kontext))
        val id = manager.createWidget(widget, layout)
        manager.getViewFor(id).findViewById<android.view.View>(android.R.id.background).performClick()
        val gestartet = assertNotNull(shadowOf(kontext).nextStartedActivity, "nichts gestartet")
        assertEquals(link, gestartet.dataString)
        assertEquals(Intent.ACTION_VIEW, gestartet.action)
        assertEquals(ComponentName(kontext, MainActivity::class.java), gestartet.component)
    }

    @Test
    fun scanImManifest() = imManifest(ScanWidget::class.java, R.xml.scan_widget_info)

    @Test
    fun fotoImManifest() = imManifest(FotoWidget::class.java, R.xml.foto_widget_info)

    @Test
    fun einkaufImManifest() = imManifest(EinkaufWidget::class.java, R.xml.einkauf_widget_info)

    @Test
    fun heuteImManifest() = imManifest(HeuteWidget::class.java, R.xml.heute_widget_info)

    @Test
    fun kalorienImManifest() = imManifest(KalorienWidget::class.java, R.xml.kalorien_widget_info)

    @Test
    fun eintragenImManifest() = imManifest(EintragenWidget::class.java, R.xml.eintragen_widget_info)

    @Test
    fun scanTippStartetScan() = tippStartet(ScanWidget::class.java, R.layout.widget_scan, SCAN_LINK)

    @Test
    fun fotoTippStartetKamera() = tippStartet(FotoWidget::class.java, R.layout.widget_foto, FOTO_LINK)
}

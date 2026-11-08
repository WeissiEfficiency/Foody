package de.foody.app.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.MainActivity
import de.foody.app.R
import de.foody.app.navigation.SCAN_LINK
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Widget „Scannen“: im Manifest angemeldet, der Tipp startet die App mit [SCAN_LINK]. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class ScanWidgetTest {
    private val kontext = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun imManifestAngemeldet() {
        val empfaenger = kontext.packageManager.queryBroadcastReceivers(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(kontext.packageName), PackageManager.GET_META_DATA,
        )
        val widget = assertNotNull(empfaenger.firstOrNull { it.activityInfo.name == ScanWidget::class.java.name }, "ScanWidget fehlt")
        assertEquals(R.xml.scan_widget_info, widget.activityInfo.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER))
        // Hersteller-Launcher lesen die Widget-Liste teils selbst über den PackageManager: nicht exportiert fehlte es dort
        assertTrue(widget.activityInfo.exported, "für fremde Launcher sichtbar")
    }

    @Test
    fun tippStartetScan() {
        val manager = shadowOf(AppWidgetManager.getInstance(kontext))
        val id = manager.createWidget(ScanWidget::class.java, R.layout.widget_scan)
        manager.getViewFor(id).findViewById<android.view.View>(android.R.id.background).performClick()
        val gestartet = assertNotNull(shadowOf(kontext).nextStartedActivity, "nichts gestartet")
        assertEquals(SCAN_LINK, gestartet.dataString)
        assertEquals(Intent.ACTION_VIEW, gestartet.action)
        assertEquals(ComponentName(kontext, MainActivity::class.java), gestartet.component)
        assertTrue(gestartet.dataString!!.startsWith("foody://"))
    }
}

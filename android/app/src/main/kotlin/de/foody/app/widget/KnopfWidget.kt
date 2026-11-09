package de.foody.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import de.foody.app.MainActivity
import de.foody.app.R
import de.foody.app.navigation.FOTO_LINK
import de.foody.app.navigation.SCAN_LINK

/**
 * Homescreen-Widget als ein Knopf: Der Tipp öffnet Foody mit [link] (explizit an [MainActivity] gebunden). Kein Inhalt,
 * der sich ändert: kein regelmäßiges Update.
 */
abstract class KnopfWidget(@param:LayoutRes private val layout: Int, private val link: String) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, ansicht(context))
    }

    fun ansicht(context: Context): RemoteViews = RemoteViews(context.packageName, layout).apply {
        setOnClickPendingIntent(android.R.id.background, oeffnen(context))
    }

    // Die Links unterscheiden die PendingIntents (andere Daten), derselbe requestCode stört nicht
    private fun oeffnen(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(Intent.ACTION_VIEW, Uri.parse(link), context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Widget „Scannen“: Foody auf „Heute“, Eintragen-Blatt in „Frei“, gleich der Strichcode-Scan ([SCAN_LINK]). */
class ScanWidget : KnopfWidget(R.layout.widget_scan, SCAN_LINK)

/** Widget „KI-Foto“: Foody auf „Heute“, Eintragen-Blatt in „Frei“, gleich die Kamera für die Fotoerkennung ([FOTO_LINK]). */
class FotoWidget : KnopfWidget(R.layout.widget_foto, FOTO_LINK)

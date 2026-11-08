package de.foody.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import de.foody.app.MainActivity
import de.foody.app.R
import de.foody.app.navigation.SCAN_LINK

/**
 * Homescreen-Widget „Scannen“: ein Knopf, der Foody auf „Heute“ öffnet, das Eintragen-Blatt in „Frei“ zeigt und
 * gleich den Strichcode-Scan startet ([SCAN_LINK]). Kein Inhalt, der sich ändert: kein regelmäßiges Update.
 */
class ScanWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, ansicht(context))
    }

    companion object {
        fun ansicht(context: Context): RemoteViews = RemoteViews(context.packageName, R.layout.widget_scan).apply {
            setOnClickPendingIntent(android.R.id.background, scanStarten(context))
        }

        private fun scanStarten(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(Intent.ACTION_VIEW, Uri.parse(SCAN_LINK), context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

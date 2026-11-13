package de.foody.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import de.foody.app.MainActivity
import de.foody.app.R
import de.foody.app.navigation.FOTO_LINK
import de.foody.app.navigation.SCAN_LINK
import de.foody.core.data.DarstellungSpeicher

/**
 * Homescreen-Widget als ein Knopf: Der Tipp öffnet Foody mit [link] (explizit an [MainActivity] gebunden). Farbe, Form
 * und Wort folgen dem gewählten Stil ([knopfAussehen]); ändert er sich, ruft die App [alleAktualisieren]. Sonst kein
 * regelmäßiges Update.
 */
abstract class KnopfWidget(@param:LayoutRes private val layout: Int, private val link: String, private val art: KnopfArt) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, ansicht(context))
    }

    fun ansicht(context: Context): RemoteViews = RemoteViews(context.packageName, layout).apply {
        setOnClickPendingIntent(android.R.id.background, oeffnen(context))
        knopfAussehen(DarstellungSpeicher(context).stilAktuell(), art)?.let { stilAnwenden(it) }
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
class ScanWidget : KnopfWidget(R.layout.widget_scan, SCAN_LINK, KnopfArt.SCAN)

/** Widget „KI-Foto“: Foody auf „Heute“, Eintragen-Blatt in „Frei“, gleich die Kamera für die Fotoerkennung ([FOTO_LINK]). */
class FotoWidget : KnopfWidget(R.layout.widget_foto, FOTO_LINK, KnopfArt.FOTO)

/**
 * Zeichnet alle Widgets auf dem Homescreen neu, etwa nach einem Wechsel des Stils: Knöpfe und „Schnell eintragen“
 * sofort, die Daten-Widgets über ihr eigenes Update (sie laden die Daten im Hintergrund).
 */
fun alleAktualisieren(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    for (widget in listOf(ScanWidget(), FotoWidget())) {
        val ids = manager.getAppWidgetIds(ComponentName(context, widget::class.java))
        if (ids.isNotEmpty()) manager.updateAppWidget(ids, widget.ansicht(context))
    }
    val eintragen = context.widgetIds(EintragenWidget::class.java)
    if (eintragen.isNotEmpty()) manager.updateAppWidget(eintragen, eintragenAnsicht(context, DarstellungSpeicher(context).stilAktuell()))
    for (widget in listOf(EinkaufWidget::class.java)) {
        val ids = context.widgetIds(widget)
        if (ids.isEmpty()) continue
        context.sendBroadcast(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setComponent(ComponentName(context, widget))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
        )
    }
}

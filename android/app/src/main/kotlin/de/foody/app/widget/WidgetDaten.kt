package de.foody.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.foody.app.MainActivity
import de.foody.core.data.DarstellungSpeicher
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.heute.OffenerEinkauf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Was die Daten-Widgets zeigen, aus den Repositories der App (nur lesen). */
class WidgetDatenQuelle @Inject constructor(private val heute: HeuteRepository) {
    /** Offene Artikel der jüngsten Einkaufsliste; `null` = nichts offen. */
    fun einkauf(): Flow<OffenerEinkauf?> = heute.offenerEinkauf()
}

/** Zugang zur [WidgetDatenQuelle] für die Widget-Empfänger (sie werden vom System angelegt, nicht von Hilt). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEinstieg {
    fun widgetDaten(): WidgetDatenQuelle
}

internal fun Context.widgetDaten(): WidgetDatenQuelle =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEinstieg::class.java).widgetDaten()

/** Die IDs der Widgets von [widget] auf dem Homescreen (leer, wenn keins liegt). */
internal fun Context.widgetIds(widget: Class<out AppWidgetProvider>): IntArray =
    AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, widget))

/** Öffnet Foody mit [link] (`null` = einfach die App), explizit an [MainActivity]; [nummer] trennt die Widgets. */
internal fun oeffnen(context: Context, link: String?, nummer: Int): PendingIntent = PendingIntent.getActivity(
    context,
    nummer,
    Intent(Intent.ACTION_VIEW, link?.let(Uri::parse), context, MainActivity::class.java),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/** Lädt in [onUpdate] auf IO und gibt den Empfänger erst danach frei (`goAsync`). */
internal fun AppWidgetProvider.imHintergrund(arbeit: suspend () -> Unit) {
    val ende = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            arbeit()
        } finally {
            ende.finish()
        }
    }
}

/**
 * Hält die Daten-Widgets aktuell, solange der Prozess lebt: Jede Änderung der Einkaufsliste (in der App oder über den
 * Sync) zeichnet das Widget „Einkauf“ neu, nur wenn eins auf dem Homescreen liegt. Sonst gilt das Update des Systems
 * (alle 30 Minuten).
 */
class WidgetBeobachter @Inject constructor(private val quelle: WidgetDatenQuelle, private val darstellung: DarstellungSpeicher) {
    fun starten(context: Context, bereich: CoroutineScope) {
        val app = context.applicationContext
        bereich.launch {
            quelle.einkauf().distinctUntilChanged().conflate().collect { einkauf ->
                val ids = app.widgetIds(EinkaufWidget::class.java)
                if (ids.isNotEmpty()) AppWidgetManager.getInstance(app).updateAppWidget(ids, einkaufAnsicht(app, einkauf, darstellung.stilAktuell()))
            }
        }
    }
}

/** Einmal laden, etwa für [AppWidgetProvider.onUpdate]. */
internal suspend fun WidgetDatenQuelle.einkaufJetzt(): OffenerEinkauf? = einkauf().first()

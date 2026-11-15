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
import de.foody.core.data.TageszielSpeicher
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.heute.OffenerEinkauf
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.domain.EintragWerte
import de.foody.domain.Mahlzeit
import de.foody.domain.Naehrwerte
import de.foody.domain.Tagebuch
import de.foody.feature.heute.NaechsteMahlzeit
import de.foody.feature.heute.naechsteMahlzeit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Was „Heute“ und „Kalorien“ zeigen: die nächste geplante Mahlzeit, kcal des Tages und das Tagesziel (`null` = keins). */
data class HeuteDaten(val datum: LocalDate, val naechste: NaechsteMahlzeit?, val kcal: Int, val ziel: Int?)

/** Ein Tag im Widget „Woche“: [gericht] ist das Hauptgericht des Tages (Abendessen vor Mittag …), `null` = nichts geplant. */
data class WochenTagKurz(val datum: LocalDate, val gericht: String?)

/** Die Woche ab Montag für das Widget „Woche“, [heute] hervorgehoben. */
data class WocheDaten(val heute: LocalDate, val tage: List<WochenTagKurz>)

/** Reihenfolge, in der ein Gericht des Tages ins Widget „Woche“ kommt: Abendessen, Mittag, Frühstück, Snack, Eigenes. */
private fun vorrang(slot: String): Int = when (Mahlzeit.ausText(slot)) {
    Mahlzeit.ABENDESSEN -> 0
    Mahlzeit.MITTAGESSEN -> 1
    Mahlzeit.FRUEHSTUECK -> 2
    Mahlzeit.SNACK -> 3
    null -> 4
}

/** Die sieben Tage ab [montag] mit dem Hauptgericht je Tag (nur Rezepte, die es noch gibt). */
internal fun wochenTage(montag: LocalDate, plan: List<PlanEintragEntity>, namen: Map<String, String>): List<WochenTagKurz> =
    (0L..6L).map { i ->
        val datum = montag.plusDays(i)
        val gericht = plan.filter { it.date == datum && it.recipeId in namen }.minByOrNull { vorrang(it.slotType) }?.let { namen.getValue(it.recipeId) }
        WochenTagKurz(datum, gericht)
    }

/** Abstand der Uhr für die Widgets: Die nächste Mahlzeit wechselt mit ihrem Zeitfenster, der Tag um Mitternacht. */
private const val UHR_TAKT_MS = 5 * 60_000L

/** Was die Daten-Widgets zeigen, aus den Repositories der App (nur lesen). */
class WidgetDatenQuelle @Inject constructor(
    private val db: FoodyDatenbank,
    private val heute: HeuteRepository,
    private val tagebuch: TagebuchRepository,
    private val ziel: TageszielSpeicher,
) {
    /** Offene Artikel der jüngsten Einkaufsliste; `null` = nichts offen. */
    fun einkauf(): Flow<OffenerEinkauf?> = heute.offenerEinkauf()

    /** Minutengenaue Uhrzeit, alle fünf Minuten neu. */
    private fun uhr(): Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES))
            delay(UHR_TAKT_MS)
        }
    }

    /** Heute: Plan, Tagebuch und Ziel; wechselt um Mitternacht auf den neuen Tag. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun heute(): Flow<HeuteDaten> {
        val uhr = uhr()
        return uhr.map { it.toLocalDate() }.distinctUntilChanged().flatMapLatest { datum ->
            combine(heute.planHeuteUndMorgen(datum), tagebuch.amTag(datum), ziel.tagesziel(), uhr) { plan, eintraege, z, jetzt ->
                HeuteDaten(datum, naechsteMahlzeit(plan, jetzt), kcalSumme(eintraege), z)
            }
        }
    }

    /** Plan dieser Woche (Montag bis Sonntag); wechselt um Mitternacht. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun woche(): Flow<WocheDaten> = uhr().map { it.toLocalDate() }.distinctUntilChanged().flatMapLatest { tag ->
        val montag = tag.with(DayOfWeek.MONDAY)
        combine(db.planEintragDao().zwischen(montag, montag.plusDays(6)), db.rezeptDao().aktive()) { plan, rezepte ->
            WocheDaten(tag, wochenTage(montag, plan, rezepte.associate { it.id to it.name }))
        }
    }
}

/** kcal aller Einträge des Tages wie im Tagebuch. */
internal fun kcalSumme(eintraege: List<TagebuchEintragEntity>): Int = Tagebuch.bilanz(
    eintraege.map { EintragWerte(Mahlzeit.SNACK, Naehrwerte(it.energieKj, it.eiweiss, it.kohlenhydrate, it.fett, it.vollstaendig)) },
).tag.kcal

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
 * Hält die Daten-Widgets aktuell, solange der Prozess lebt: Jede Änderung (in der App oder über den Sync) zeichnet
 * „Einkauf“, „Heute“, „Kalorien“ und „Woche“ neu, nur wenn welche auf dem Homescreen liegen. Sonst gilt das Update des Systems
 * (alle 30 Minuten).
 */
class WidgetBeobachter @Inject constructor(private val quelle: WidgetDatenQuelle, private val darstellung: DarstellungSpeicher) {
    fun starten(context: Context, bereich: CoroutineScope) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        bereich.launch {
            quelle.einkauf().distinctUntilChanged().conflate().collect { einkauf ->
                val ids = app.widgetIds(EinkaufWidget::class.java)
                if (ids.isNotEmpty()) manager.updateAppWidget(ids, einkaufAnsicht(app, einkauf, darstellung.stilAktuell()))
            }
        }
        bereich.launch {
            quelle.woche().distinctUntilChanged().conflate().collect { daten ->
                val ids = app.widgetIds(WocheWidget::class.java)
                if (ids.isNotEmpty()) manager.updateAppWidget(ids, wocheAnsicht(app, daten, darstellung.stilAktuell()))
            }
        }
        bereich.launch {
            quelle.heute().distinctUntilChanged().conflate().collect { daten ->
                val wahl = darstellung.stilAktuell()
                val heute = app.widgetIds(HeuteWidget::class.java)
                if (heute.isNotEmpty()) manager.updateAppWidget(heute, heuteAnsicht(app, daten, wahl))
                val kalorien = app.widgetIds(KalorienWidget::class.java)
                if (kalorien.isNotEmpty()) manager.updateAppWidget(kalorien, kalorienAnsicht(app, daten, wahl))
            }
        }
    }
}

/** Einmal laden, etwa für [AppWidgetProvider.onUpdate]. */
internal suspend fun WidgetDatenQuelle.einkaufJetzt(): OffenerEinkauf? = einkauf().first()

internal suspend fun WidgetDatenQuelle.heuteJetzt(): HeuteDaten = heute().first()

internal suspend fun WidgetDatenQuelle.wocheJetzt(): WocheDaten = woche().first()

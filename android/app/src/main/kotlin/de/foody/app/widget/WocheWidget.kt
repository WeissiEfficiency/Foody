package de.foody.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import de.foody.app.R
import de.foody.app.navigation.SCHEMA
import de.foody.core.data.DarstellungSpeicher
import de.foody.core.data.StilWahl
import de.foody.core.designsystem.stil.FoodyStil
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Widget „Woche“ (4×1): sieben Tage ab Montag mit dem Hauptgericht je Tag; der Tipp öffnet „Woche“. */
class WocheWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = imHintergrund {
        manager.updateAppWidget(ids, wocheAnsicht(context, context.widgetDaten().wocheJetzt(), DarstellungSpeicher(context).stilAktuell()))
    }
}

internal const val WIDGET_WOCHE = 16

private val TAGE = intArrayOf(R.id.widget_tag1, R.id.widget_tag2, R.id.widget_tag3, R.id.widget_tag4, R.id.widget_tag5, R.id.widget_tag6, R.id.widget_tag7)
private val MARKEN = intArrayOf(
    R.id.widget_marke1, R.id.widget_marke2, R.id.widget_marke3, R.id.widget_marke4, R.id.widget_marke5, R.id.widget_marke6, R.id.widget_marke7,
)
private val GERICHTE = intArrayOf(
    R.id.widget_gericht1, R.id.widget_gericht2, R.id.widget_gericht3, R.id.widget_gericht4, R.id.widget_gericht5, R.id.widget_gericht6,
    R.id.widget_gericht7,
)

private const val EDO_TAGE = "月火水木金土日"

/** Kürzel des Wochentags: Edo (Immersiv) mit Kanji, Sage, Anime und Kreuzer in Versalien, sonst „Mo“. */
internal fun tagKuerzel(stil: FoodyStil, immersiv: Boolean, datum: LocalDate): String {
    val i = datum.dayOfWeek.value - 1
    if (stil == FoodyStil.EDO && immersiv) return EDO_TAGE[i].toString()
    val kurz = datum.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).removeSuffix(".").take(2)
    return if (stil == FoodyStil.SAGE || stil == FoodyStil.ANIME || stil == FoodyStil.KREUZER) kurz.uppercase(Locale.GERMAN) else kurz
}

/** Gericht in der schmalen Spalte: erstes Wort, lange Wörter gekürzt („Shakshuka“ → „Shaksh.“), ohne Plan „—“. */
internal fun gerichtKurz(name: String?): String {
    val wort = name?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() } ?: return "—"
    return if (wort.length > 7) wort.take(6) + "." else wort
}

/** Was TalkBack liest: „Woche: Montag Curry, Dienstag nichts geplant, …“. */
internal fun wocheBeschreibung(d: WocheDaten): String = "Woche: " + d.tage.joinToString(", ") { t ->
    val tag = t.datum.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN)
    "${if (t.datum == d.heute) "heute, " else ""}$tag ${t.gericht ?: "nichts geplant"}"
}

/** Form der Tagesmarke. */
internal enum class MarkenForm { KREIS, RAUTE, BOX, WIMPEL }

/**
 * Eine Tagesmarke: Form und Größe (dp), Füllung und Rand (`null` = keine), Text darin, Schein (Leuchten) um sie.
 * [scheinPlatz] hält den Platz für den Schein frei, auch ohne Schein, damit alle Spalten gleich hoch stehen.
 */
internal data class Marke(
    val form: MarkenForm,
    val breite: Float,
    val hoehe: Float,
    @ColorInt val fuellung: Int?,
    @ColorInt val rand: Int?,
    val randBreite: Float,
    val text: String? = null,
    @ColorInt val textFarbe: Int = 0,
    @ColorInt val schein: Int? = null,
    val serif: Boolean = false,
    val scheinPlatz: Boolean = schein != null,
)

/** Zustand eines Tages für die Marke. */
private data class TagLage(val heute: Boolean, val geplant: Boolean, val vorbei: Boolean, val index: Int)

private val TINTE = 0xFF1B1A2E.toInt()
private val HOLO = 0xFF5CE1FF.toInt()

/** Die Marke eines Tages im Stil wie in den Widget-Entwürfen. */
private fun marke(k: KartenAussehen, lage: TagLage, kuerzel: String): Marke {
    val (heute, geplant, vorbei, i) = lage
    return when (k.stil) {
        FoodyStil.MILCHGLAS -> Marke(
            MarkenForm.KREIS, 12f, 12f, if (heute) k.text else if (geplant) ColorUtils.setAlphaComponent(k.text, 0x8C) else null,
            ColorUtils.setAlphaComponent(k.text, 0x66), 1.5f,
        )
        FoodyStil.SAGE -> Marke(
            MarkenForm.RAUTE, 12f, 12f, if (geplant || heute) k.akzent else null, k.akzent, 1.2f, schein = if (heute) k.akzent else null, scheinPlatz = true,
        )
        FoodyStil.ANIME -> Marke(
            MarkenForm.BOX, 38f, 46f, if (heute) k.akzent else if (vorbei) 0xFFF2EEF4.toInt() else 0xFFFFFFFF.toInt(), TINTE, 2f, kuerzel, TINTE,
        )
        FoodyStil.EDO -> Marke(MarkenForm.KREIS, 22f, 22f, if (heute) k.akzent else if (geplant) k.text else null, k.text, 1.5f)
        FoodyStil.BAYRISCH -> {
            val stoff = when {
                heute -> 0xFFB8322A.toInt()
                !geplant -> 0xFFFBF6EA.toInt()
                i % 2 == 0 -> k.akzent
                else -> 0xFFFFFFFF.toInt()
            }
            val hell = stoff == 0xFFFFFFFF.toInt() || stoff == 0xFFFBF6EA.toInt()
            Marke(MarkenForm.WIMPEL, 34f, 44f, stoff, k.text, 1.5f, kuerzel, if (hell) k.text else 0xFFFFFFFF.toInt(), serif = true)
        }
        FoodyStil.GRAUEN -> Marke(
            MarkenForm.KREIS, 18f, 18f, if (geplant || heute) 0xFFD9DEC8.toInt() else null, if (heute) k.akzent else k.neben, if (heute) 2f else 1f,
            schein = if (heute) k.akzent else null, scheinPlatz = true,
        )
        FoodyStil.KREUZER -> Marke(
            MarkenForm.RAUTE, 14f, 14f, if (heute) k.akzent else if (geplant) HOLO else null, if (geplant || heute) null else k.neben, 1f,
        )
    }
}

/** Zeichnet [m] in der Dichte des Geräts; Platz für den Schein bleibt rundum. */
internal fun markenBild(context: Context, m: Marke): Bitmap {
    val d = context.resources.displayMetrics.density
    val rand = if (m.scheinPlatz) 6 * d else 0f
    val w = m.breite * d
    val h = m.hoehe * d
    val bild = createBitmap((w + 2 * rand).toInt().coerceAtLeast(1), (h + 2 * rand).toInt().coerceAtLeast(1))
    val c = Canvas(bild)
    c.translate(rand, rand)
    val s = m.randBreite * d
    val form = Path().apply {
        when (m.form) {
            MarkenForm.KREIS -> addOval(RectF(s / 2, s / 2, w - s / 2, h - s / 2), Path.Direction.CW)
            MarkenForm.RAUTE -> {
                moveTo(w / 2, s)
                lineTo(w - s, h / 2)
                lineTo(w / 2, h - s)
                lineTo(s, h / 2)
                close()
            }
            MarkenForm.BOX -> addRoundRect(RectF(s / 2, s / 2, w - s / 2, h - s / 2), 10 * d, 10 * d, Path.Direction.CW)
            MarkenForm.WIMPEL -> {
                moveTo(s / 2, s / 2)
                lineTo(w - s / 2, s / 2)
                lineTo(w / 2, h - s)
                close()
            }
        }
    }
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    m.fuellung?.let { f ->
        p.style = Paint.Style.FILL
        p.color = f
        m.schein?.let { p.setShadowLayer(6 * d, 0f, 0f, it) }
        c.drawPath(form, p)
        p.clearShadowLayer()
    }
    m.rand?.let { r ->
        p.style = Paint.Style.STROKE
        p.strokeWidth = s
        p.color = r
        if (m.fuellung == null) m.schein?.let { p.setShadowLayer(6 * d, 0f, 0f, it) }
        c.drawPath(form, p)
        p.clearShadowLayer()
    }
    m.text?.let { t ->
        val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = m.textFarbe
            textSize = 12 * d
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(if (m.serif) Typeface.SERIF else Typeface.DEFAULT, Typeface.BOLD)
        }
        // Wimpel: Text oben im breiten Teil; Box: mittig
        val y = if (m.form == MarkenForm.WIMPEL) 6 * d - schrift.ascent() else h / 2 - (schrift.ascent() + schrift.descent()) / 2
        c.drawText(t, w / 2, y, schrift)
    }
    return bild
}

/** Ansicht des Widgets „Woche“ für [d] im Stil [wahl]. */
fun wocheAnsicht(context: Context, d: WocheDaten, wahl: StilWahl): RemoteViews = RemoteViews(context.packageName, R.layout.widget_woche).apply {
    val k = kartenAussehen(context, wahl)
    if (k.stil != FoodyStil.MILCHGLAS) flaecheAnwenden(k.flaeche)
    // Anime und Bayrisch tragen das Kürzel in der Marke, Sage und Anime zeigen keine Gerichte (wie im Entwurf)
    val kuerzelInMarke = k.stil == FoodyStil.ANIME || k.stil == FoodyStil.BAYRISCH
    val ohneGericht = k.stil == FoodyStil.SAGE || k.stil == FoodyStil.ANIME
    d.tage.take(7).forEachIndexed { i, t ->
        val heute = t.datum == d.heute
        val kuerzel = tagKuerzel(k.stil, k.immersiv, t.datum)
        val lage = TagLage(heute, t.gericht != null, t.datum < d.heute, i)
        if (kuerzelInMarke) {
            setViewVisibility(TAGE[i], View.GONE)
        } else {
            val tagFarbe = when {
                heute && k.stil != FoodyStil.MILCHGLAS -> k.akzent
                k.stil == FoodyStil.SAGE || k.stil == FoodyStil.KREUZER -> k.neben
                else -> k.text
            }
            setTextViewText(TAGE[i], schrift(kuerzel, if (k.stil == FoodyStil.KREUZER) k.nebenSchrift else k.titelSchrift, fett = heute))
            setTextColor(TAGE[i], tagFarbe)
        }
        setImageViewBitmap(MARKEN[i], markenBild(context, marke(k, lage, kuerzel)))
        if (ohneGericht) {
            setViewVisibility(GERICHTE[i], View.GONE)
        } else {
            setTextViewText(GERICHTE[i], schrift(gerichtKurz(t.gericht), if (k.stil == FoodyStil.GRAUEN) k.nebenSchrift else null))
            setTextColor(GERICHTE[i], k.neben)
        }
    }
    setContentDescription(android.R.id.background, wocheBeschreibung(d))
    setOnClickPendingIntent(android.R.id.background, oeffnen(context, "${SCHEMA}woche", WIDGET_WOCHE))
}

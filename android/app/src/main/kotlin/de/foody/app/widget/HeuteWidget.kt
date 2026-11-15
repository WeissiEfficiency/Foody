package de.foody.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.res.ColorStateList
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import de.foody.app.R
import de.foody.app.navigation.EINTRAGEN_LINK
import de.foody.app.navigation.SCHEMA
import de.foody.core.data.DarstellungSpeicher
import de.foody.core.data.StilWahl
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.NaechsteMahlzeit
import de.foody.feature.heute.slotName
import java.util.Locale

/** Widget „Heute“ (4×2): nächste geplante Mahlzeit und kcal des Tages; Fläche öffnet die App. */
class HeuteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = imHintergrund {
        manager.updateAppWidget(ids, heuteAnsicht(context, context.widgetDaten().heuteJetzt(), DarstellungSpeicher(context).stilAktuell()))
    }
}

/** Widget „Kalorien“ (2×2): Ring mit dem Rest des Tagesziels; der Tipp öffnet das Tagebuch von heute. */
class KalorienWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = imHintergrund {
        manager.updateAppWidget(ids, kalorienAnsicht(context, context.widgetDaten().heuteJetzt(), DarstellungSpeicher(context).stilAktuell()))
    }
}

internal const val WIDGET_HEUTE = 12
internal const val WIDGET_HEUTE_KOCHEN = 13
internal const val WIDGET_HEUTE_EINTRAGEN = 14
internal const val WIDGET_KALORIEN = 15

/** Ganze Zahl mit Tausenderpunkt („2.000“). */
internal fun zahl(n: Int): String = String.format(Locale.GERMAN, "%,d", n)

/** „860 / 2.000 kcal“, ohne Ziel „860 kcal“. */
internal fun kcalText(d: HeuteDaten): String = d.ziel?.let { "${zahl(d.kcal)} / ${zahl(it)} kcal" } ?: "${zahl(d.kcal)} kcal"

/** Anteil am Ziel für Ring, Balken und Tank; ohne Ziel leer. */
internal fun zielAnteil(d: HeuteDaten): Float = d.ziel?.takeIf { it > 0 }?.let { d.kcal.toFloat() / it } ?: 0f

/** Marke über dem Gericht: „HEUTE · ABENDESSEN“, in Immersiv das Wort des Stils („ABENDMAHL“, „NÄCHSTE MISSION“ …). */
internal fun heuteMarke(stil: FoodyStil, immersiv: Boolean, n: NaechsteMahlzeit?): String {
    val grund = if (n == null) "Heute" else "${if (n.morgen) "Morgen" else "Heute"} · ${slotName(n.mahlzeit.slot)}"
    val abend = n != null && !n.morgen && Mahlzeit.ausText(n.mahlzeit.slot) == Mahlzeit.ABENDESSEN
    if (!immersiv) return grund.uppercase(Locale.GERMAN)
    return when (stil) {
        FoodyStil.SAGE -> if (abend) "ABENDMAHL" else grund.uppercase(Locale.GERMAN)
        FoodyStil.BAYRISCH -> if (abend) "Heid auf d’Nacht" else grund
        FoodyStil.GRAUEN -> if (abend) "Das Ritual des Abends" else grund
        FoodyStil.KREUZER -> "NÄCHSTE MISSION"
        else -> grund.uppercase(Locale.GERMAN)
    }
}

/** Zeit und kcal je Portion des Gerichts („45 Min. · 540 kcal“), `null` ohne Angaben. */
internal fun gerichtInfo(n: NaechsteMahlzeit?): String? {
    val m = n?.mahlzeit ?: return null
    return listOfNotNull(m.zeitMinuten?.let { "$it Min." }, m.kcalJePortion?.let { "${zahl(it)} kcal" }).joinToString(" · ").ifEmpty { null }
}

/** Was TalkBack am Widget „Heute“ liest. */
internal fun heuteBeschreibung(d: HeuteDaten): String {
    val kcal = d.ziel?.let { "${zahl(d.kcal)} von ${zahl(it)} kcal" } ?: "${zahl(d.kcal)} kcal"
    val n = d.naechste ?: return "Heute: $kcal. Nichts geplant."
    val wann = "${if (n.morgen) "Morgen" else "Heute"}, ${slotName(n.mahlzeit.slot)}"
    return "Heute: $kcal. $wann: ${n.mahlzeit.titel}" + (n.mahlzeit.zeitMinuten?.let { ", $it Minuten" } ?: "") + "."
}

/** Rest des Ziels für „Kalorien“: Zahl und Wort darunter („1.140 übrig“, „120 drüber“, ohne Ziel „860 kcal heute“). */
internal fun kalorienRest(d: HeuteDaten): Pair<String, String> {
    val ziel = d.ziel ?: return zahl(d.kcal) to "kcal heute"
    val rest = ziel - d.kcal
    return if (rest >= 0) zahl(rest) to "übrig" else zahl(-rest) to "drüber"
}

/** Ring je Stil: Spur, Farbe und Strichstärke wie in den Entwürfen. */
private data class RingFarben(@ColorInt val spur: Int, @ColorInt val farbe: Int, val breite: Float)

private val HOLO = 0xFF5CE1FF.toInt()

private fun ringFarben(k: KartenAussehen): RingFarben = when (k.stil) {
    FoodyStil.MILCHGLAS -> RingFarben(ColorUtils.setAlphaComponent(k.text, 0x33), k.text, 10f)
    FoodyStil.SAGE -> RingFarben(0xFF5A4F38.toInt(), k.akzent, 5f)
    FoodyStil.ANIME -> RingFarben(0xFFF2EEF4.toInt(), k.akzent, 12f)
    FoodyStil.EDO -> RingFarben(0xFFE2D6BC.toInt(), k.akzent, 9f)
    FoodyStil.BAYRISCH -> RingFarben(0xFFEDE4D0.toInt(), k.akzent, 10f)
    FoodyStil.GRAUEN -> RingFarben(k.linie, k.akzent, 6f)
    FoodyStil.KREUZER -> RingFarben(k.linie, HOLO, 8f)
}

private fun Context.px(dp: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

/** Ring mit Zahl und Wort darunter. */
private fun RemoteViews.ring(context: Context, k: KartenAussehen, groesse: Float, anteil: Float, zahl: String, unter: String) {
    val f = ringFarben(k)
    setViewVisibility(R.id.widget_ring_rahmen, View.VISIBLE)
    setImageViewBitmap(R.id.widget_ring, ringBild(context, groesse, anteil, f.spur, f.farbe, f.breite))
    setTextViewText(R.id.widget_ring_zahl, schrift(zahl, k.titelSchrift, fett = true))
    setTextColor(R.id.widget_ring_zahl, k.text)
    setTextViewText(R.id.widget_ring_unter, schrift(unter, k.nebenSchrift))
    setTextColor(R.id.widget_ring_unter, k.neben)
}

/** Knopf im Heute-Widget: Rahmen ([rand] `null` = wie die Füllung) mit [breite], Füllung, Schrift, Rundung. */
private fun RemoteViews.knopf(
    context: Context,
    name: Int,
    rahmen: Int,
    text: CharSequence,
    @ColorInt fuellung: Int,
    @ColorInt schrift: Int,
    @ColorInt rand: Int?,
    breite: Float,
    radius: Float,
) {
    val dp = TypedValue.COMPLEX_UNIT_DIP
    setTextViewText(name, text)
    setTextColor(name, schrift)
    setColorStateList(rahmen, "setBackgroundTintList", ColorStateList.valueOf(rand ?: fuellung))
    setColorStateList(name, "setBackgroundTintList", ColorStateList.valueOf(fuellung))
    val r = if (rand != null) context.px(breite) else 0
    setViewPadding(rahmen, r, r, r, r)
    setViewOutlinePreferredRadius(rahmen, radius, dp)
    setViewOutlinePreferredRadius(name, (radius - if (rand != null) breite else 0f).coerceAtLeast(0f), dp)
}

/** Ansicht des Widgets „Heute“ für [d] im Stil [wahl]. */
fun heuteAnsicht(context: Context, d: HeuteDaten, wahl: StilWahl): RemoteViews = RemoteViews(context.packageName, R.layout.widget_heute).apply {
    val k = kartenAussehen(context, wahl)
    val n = d.naechste
    val imm = k.immersiv
    when (k.stil) {
        FoodyStil.MILCHGLAS -> Unit
        FoodyStil.ANIME -> flaecheAnwenden(k.flaeche.copy(farbe = k.akzent))
        else -> flaecheAnwenden(k.flaeche)
    }
    val marke = heuteMarke(k.stil, imm, n)
    // Grauen: Marke in der Serife wie der Titel (im Entwurf Kapitälchen), sonst wie der Nebentext
    setTextViewText(R.id.widget_marke, schrift(marke, if (k.stil == FoodyStil.GRAUEN) k.titelSchrift else k.nebenSchrift, fett = true))
    setTextColor(R.id.widget_marke, k.neben)
    setTextViewText(R.id.widget_titel, schrift(n?.mahlzeit?.titel ?: "Nichts geplant", k.titelSchrift, fett = true))
    setTextColor(R.id.widget_titel, k.text)
    setTextColor(R.id.widget_info, k.neben)
    val info = gerichtInfo(n)
    when (k.stil) {
        FoodyStil.EDO, FoodyStil.BAYRISCH, FoodyStil.GRAUEN -> {
            ring(context, k, 104f, zielAnteil(d), zahl(d.kcal), d.ziel?.let { "/ ${zahl(it)}" } ?: "kcal")
            setTextColor(R.id.widget_marke, k.akzent)
            setTextViewText(R.id.widget_info, schrift(info.orEmpty(), k.nebenSchrift))
            if (k.stil == FoodyStil.BAYRISCH && imm) {
                setViewVisibility(R.id.widget_streifen, View.VISIBLE)
                setImageViewBitmap(R.id.widget_streifen, rautenBild(context, 480f, 12f, k.akzent, k.text))
            }
            if (k.stil == FoodyStil.GRAUEN) {
                setViewVisibility(R.id.widget_gross, View.VISIBLE)
                setTextViewText(R.id.widget_gross, schrift(kcalText(d), k.nebenSchrift))
                setTextViewTextSize(R.id.widget_gross, TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(R.id.widget_gross, k.neben)
            }
        }
        FoodyStil.KREUZER -> {
            setViewVisibility(R.id.widget_ring_rahmen, View.GONE)
            setViewVisibility(R.id.widget_tank, View.VISIBLE)
            setImageViewBitmap(R.id.widget_tank, tankBild(context, 26f, 140f, zielAnteil(d), k.linie, HOLO))
            setViewVisibility(R.id.widget_gross, View.VISIBLE)
            val gross = SpannableStringBuilder(zahl(d.kcal)).apply {
                val start = length
                append(d.ziel?.let { "  / ${zahl(it)} kcal" } ?: "  kcal")
                setSpan(RelativeSizeSpan(0.4f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(TypefaceSpan("monospace"), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(k.neben), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            setTextViewText(R.id.widget_gross, gross)
            setTextColor(R.id.widget_gross, k.text)
            setTextColor(R.id.widget_marke, kreuzerAmber(wahl))
            setTextViewText(R.id.widget_info, schrift(info.orEmpty().uppercase(Locale.GERMAN), k.nebenSchrift))
        }
        else -> {
            // Milchglas, Sage, Anime: ohne Ring, mit Angaben, Balken und den Knöpfen
            setViewVisibility(R.id.widget_ring_rahmen, View.GONE)
            setViewLayoutMargin(R.id.widget_spalte, RemoteViews.MARGIN_START, 0f, TypedValue.COMPLEX_UNIT_DIP)
            val zeile = if (k.stil == FoodyStil.SAGE && imm) "Vitalität ${kcalText(d)}" else kcalText(d)
            setTextViewText(R.id.widget_info, listOfNotNull(zeile, n?.mahlzeit?.zeitMinuten?.let { "$it Min." }).joinToString(" · "))
            if (k.stil != FoodyStil.ANIME) {
                setViewVisibility(R.id.widget_balken, View.VISIBLE)
                val (spur, rand) = if (k.stil == FoodyStil.SAGE) k.flaeche.farbe to 0xFF5A4F38.toInt() else ColorUtils.setAlphaComponent(k.text, 0x33) to null
                setImageViewBitmap(R.id.widget_balken, balkenBild(context, 240f, 6f, zielAnteil(d), spur, k.akzent, rand))
            } else {
                // Anime: Marke als schwarze Pille
                setInt(R.id.widget_marke, "setBackgroundResource", R.drawable.widget_form)
                setColorStateList(R.id.widget_marke, "setBackgroundTintList", ColorStateList.valueOf(k.text))
                setViewOutlinePreferredRadius(R.id.widget_marke, 99f, TypedValue.COMPLEX_UNIT_DIP)
                setViewPadding(R.id.widget_marke, context.px(10f), context.px(2f), context.px(10f), context.px(2f))
                setTextColor(R.id.widget_marke, 0xFFFFFFFF.toInt())
            }
            knoepfe(context, k, n)
        }
    }
    setContentDescription(android.R.id.background, heuteBeschreibung(d))
    setOnClickPendingIntent(android.R.id.background, oeffnen(context, null, WIDGET_HEUTE))
}

/** Amber des Kreuzers aus dem gewählten Akzent. */
private fun kreuzerAmber(wahl: StilWahl): Int = akzentVon(wahl) ?: 0xFFFFB000.toInt()

/** Wörter und Farben der beiden Knöpfe im Heute-Widget. */
private data class Art(
    val kochen: String,
    val eintragen: String,
    val fuellK: Int,
    val fuellE: Int,
    val schrift: Int,
    val randK: Int?,
    val randE: Int?,
    val breite: Float,
    val radius: Float,
)

/** „Kochen“ (nur mit geplantem Gericht) und „Eintragen“ im Aussehen des Stils. */
private fun RemoteViews.knoepfe(context: Context, k: KartenAussehen, n: NaechsteMahlzeit?) {
    setViewVisibility(R.id.widget_knoepfe, View.VISIBLE)
    val imm = k.immersiv
    val tinte = 0xFF1B1A2E.toInt()
    val a = when (k.stil) {
        FoodyStil.SAGE -> Art(
            if (imm) "KOCHEN" else "Kochen", if (imm) "EINTRAGEN" else "Eintragen", k.flaeche.farbe, k.flaeche.farbe, k.text,
            k.akzent, 0xFF5A4F38.toInt(), 1f, 0f,
        )
        FoodyStil.ANIME -> Art(
            if (imm) "Kochen ▶" else "Kochen", if (imm) "+ Eintragen" else "Eintragen", 0xFFFFE45C.toInt(), 0xFFFFFFFF.toInt(), tinte,
            tinte, tinte, 2.5f, 18f,
        )
        else -> Art("Kochen", "Eintragen", k.text, k.text, k.flaeche.farbe, null, null, 0f, 18f)
    }
    val familie = if (k.stil == FoodyStil.SAGE) "serif" else null
    if (n != null) {
        knopf(context, R.id.widget_kochen, R.id.widget_kochen_rand, schrift(a.kochen, familie, fett = true), a.fuellK, a.schrift, a.randK, a.breite, a.radius)
        setContentDescription(R.id.widget_kochen, "Kochen: ${n.mahlzeit.titel}")
        val m = n.mahlzeit
        setOnClickPendingIntent(R.id.widget_kochen, oeffnen(context, "${SCHEMA}kochen/${m.rezeptId}?portionen=${m.portionen}", WIDGET_HEUTE_KOCHEN))
    } else {
        setViewVisibility(R.id.widget_kochen_rand, View.GONE)
    }
    knopf(
        context, R.id.widget_eintragen, R.id.widget_eintragen_rand, schrift(a.eintragen, familie, fett = true), a.fuellE, a.schrift, a.randE,
        a.breite, a.radius,
    )
    setContentDescription(R.id.widget_eintragen, "Eintragen")
    setOnClickPendingIntent(R.id.widget_eintragen, oeffnen(context, EINTRAGEN_LINK, WIDGET_HEUTE_EINTRAGEN))
}

/** Ansicht des Widgets „Kalorien“ für [d] im Stil [wahl]. */
fun kalorienAnsicht(context: Context, d: HeuteDaten, wahl: StilWahl): RemoteViews = RemoteViews(context.packageName, R.layout.widget_kalorien).apply {
    val k = kartenAussehen(context, wahl)
    if (k.stil != FoodyStil.MILCHGLAS) flaecheAnwenden(k.flaeche)
    val (zahl, wort) = kalorienRest(d)
    ring(context, k, 110f, zielAnteil(d), zahl, wort)
    setContentDescription(android.R.id.background, "Kalorien: ${kcalText(d).replace(" / ", " von ")}" + d.ziel?.let { ", $zahl $wort" }.orEmpty())
    setOnClickPendingIntent(android.R.id.background, oeffnen(context, "${SCHEMA}tagebuch/${d.datum}", WIDGET_KALORIEN))
}

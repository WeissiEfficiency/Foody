package de.foody.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import de.foody.app.R
import de.foody.app.navigation.EINTRAGEN_LINK
import de.foody.core.data.DarstellungSpeicher
import de.foody.core.data.StilWahl
import de.foody.core.designsystem.stil.FoodyStil

/** Widget „Schnell eintragen“ (2×2): großes „+“, der Tipp öffnet Eintragen mit Erfassen ([EINTRAGEN_LINK]). */
class EintragenWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.frischZeigen(ids, eintragenAnsicht(context, DarstellungSpeicher(context).stilAktuell()))
    }
}

/**
 * Aussehen von „Schnell eintragen“ wie in den Entwürfen S–V: eigene Fläche (Tusche, Himmelblau, Amber …), der Kreis
 * mit Rand ([kreisRand] `null` = keiner) und Füllung, das „+“, [eckig] beim Kreuzer. [titel] in Immersiv mit Stilwort.
 */
internal data class EintragenAussehen(
    val flaeche: Flaeche?,
    @ColorInt val text: Int,
    @ColorInt val neben: Int,
    @ColorInt val kreisRand: Int?,
    val kreisRandBreite: Float,
    @ColorInt val kreisFuellung: Int,
    @ColorInt val plus: Int,
    val eckig: Boolean,
    val titel: String,
    val fuss: String,
    val schrift: String?,
)

internal fun eintragenAussehen(context: Context, wahl: StilWahl): EintragenAussehen {
    val k = kartenAussehen(context, wahl)
    val imm = k.immersiv
    // Schmale Leerzeichen um den Punkt: In engen 2×2-Zellen (etwa 123 dp) passt die Zeile so noch in eine Zeile
    val fuss = context.getString(R.string.widget_eintragen_fuss)
    val eintragen = context.getString(R.string.widget_eintragen_titel)
    fun titel(wort: String) = if (imm) wort else eintragen
    return when (k.stil) {
        FoodyStil.MILCHGLAS -> EintragenAussehen(null, k.text, k.neben, null, 0f, k.text, k.flaeche.farbe, false, eintragen, fuss, null)
        FoodyStil.SAGE -> EintragenAussehen(
            k.flaeche, k.text, k.neben, k.akzent, 1.5f, k.flaeche.farbe, k.akzent, false, titel("EINTRAGEN"), fuss, "serif",
        )
        FoodyStil.ANIME -> EintragenAussehen(
            k.flaeche.copy(farbe = ANIME_GELB), TINTE, TINTE, TINTE, 2.5f, Color.WHITE, TINTE, false,
            titel("EINTRAGEN!"), fuss, "sans-serif-black",
        )
        FoodyStil.EDO -> EintragenAussehen(
            Flaeche(Entwuerfe.edo.text, null, 0f, 18f, false), Entwuerfe.edo.glas, 0xFFB5A88E.toInt(), null, 0f, k.akzent,
            Color.WHITE, false, eintragen, fuss, "serif",
        )
        FoodyStil.BAYRISCH -> EintragenAussehen(
            Flaeche(k.akzent, Entwuerfe.bayrisch.text, 2f, 22f, false), Color.WHITE, 0xE6FFFFFF.toInt(), Entwuerfe.bayrisch.text, 2f,
            Color.WHITE, k.akzent, false, titel("Eitragn"), fuss, "serif",
        )
        FoodyStil.GRAUEN -> EintragenAussehen(
            k.flaeche, k.text, k.neben, k.akzent, 1f, k.flaeche.farbe, k.akzent, false, titel("Notieren"), fuss, "serif",
        )
        FoodyStil.KREUZER -> EintragenAussehen(
            Flaeche(k.akzent, null, 0f, 22f, false), AUF_AMBER, AUF_AMBER, AUF_AMBER, 2f, k.akzent,
            AUF_AMBER, true, titel("Proviant erfassen"), if (imm) fuss.uppercase() else fuss, "sans-serif-condensed",
        )
    }
}

/** Ansicht von „Schnell eintragen“ im Stil [wahl]. */
fun eintragenAnsicht(context: Context, wahl: StilWahl): RemoteViews = RemoteViews(context.packageName, R.layout.widget_eintragen).apply {
    val a = eintragenAussehen(context, wahl)
    a.flaeche?.let { flaecheAnwenden(it) }
    setTextViewText(R.id.widget_titel, schrift(a.titel, a.schrift, fett = true))
    setTextColor(R.id.widget_titel, a.text)
    setTextViewText(R.id.widget_fuss, schrift(a.fuss, a.schrift))
    setTextColor(R.id.widget_fuss, a.neben)
    val form = if (a.eckig) R.drawable.widget_form else R.drawable.widget_kreis
    setInt(R.id.widget_kreis_rand, "setBackgroundResource", form)
    setInt(R.id.widget_plus, "setBackgroundResource", form)
    setColorStateList(R.id.widget_kreis_rand, "setBackgroundTintList", ColorStateList.valueOf(a.kreisRand ?: a.kreisFuellung))
    setColorStateList(R.id.widget_plus, "setBackgroundTintList", ColorStateList.valueOf(a.kreisFuellung))
    val rand = if (a.kreisRand != null) a.kreisRandBreite else 0f
    val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, rand, context.resources.displayMetrics).toInt()
    setViewPadding(R.id.widget_kreis_rand, px, px, px, px)
    setTextColor(R.id.widget_plus, a.plus)
    setOnClickPendingIntent(android.R.id.background, oeffnen(context, EINTRAGEN_LINK, WIDGET_EINTRAGEN))
}

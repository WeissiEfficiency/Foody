package de.foody.app.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import de.foody.app.R
import de.foody.core.data.StilWahl
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante

/**
 * Fläche eines Widgets: [farbe], Rand ([rand] `null` = keiner) mit [randBreite], Rundung [radius] und der
 * Versatzschatten (Anime, Immersiv). Maße in dp.
 */
data class Flaeche(@ColorInt val farbe: Int, @ColorInt val rand: Int?, val randBreite: Float, val radius: Float, val schatten: Boolean)

/**
 * Setzt [f] auf ein Widget-Layout mit den Flächen `widget_schatten`, `widget_rand` und `widget_knopf` übereinander:
 * eingefärbt über die Tönung, gerundet über den Umriss. `RemoteViews` kennen keine Szenen, nur diese Flächen.
 */
internal fun RemoteViews.flaecheAnwenden(f: Flaeche) {
    val dp = TypedValue.COMPLEX_UNIT_DIP
    val versatz = if (f.schatten) SCHATTEN_VERSATZ else 0f
    fun flaeche(id: Int, farbe: Int, radius: Float) {
        setViewVisibility(id, View.VISIBLE)
        setInt(id, "setBackgroundResource", R.drawable.widget_form)
        setColorStateList(id, "setBackgroundTintList", ColorStateList.valueOf(farbe))
        setViewOutlinePreferredRadius(id, radius, dp)
    }
    fun abstand(id: Int, links: Float, oben: Float, rechts: Float, unten: Float) {
        setViewLayoutMargin(id, RemoteViews.MARGIN_LEFT, links, dp)
        setViewLayoutMargin(id, RemoteViews.MARGIN_TOP, oben, dp)
        setViewLayoutMargin(id, RemoteViews.MARGIN_RIGHT, rechts, dp)
        setViewLayoutMargin(id, RemoteViews.MARGIN_BOTTOM, unten, dp)
    }
    if (f.schatten) {
        flaeche(R.id.widget_schatten, f.rand ?: f.farbe, f.radius)
        abstand(R.id.widget_schatten, versatz, versatz, 0f, 0f)
    }
    if (f.rand != null) {
        flaeche(R.id.widget_rand, f.rand, f.radius)
        abstand(R.id.widget_rand, 0f, 0f, versatz, versatz)
    }
    val rand = if (f.rand != null) f.randBreite else 0f
    flaeche(R.id.widget_knopf, f.farbe, (f.radius - rand).coerceAtLeast(0f))
    abstand(R.id.widget_knopf, rand, rand, rand + versatz, rand + versatz)
}

/** Linie unter den Zeilen einer Liste: keine, gepunktet, gestrichelt, durchgezogen oder jede Zeile als Pille (Anime). */
enum class Linie { KEINE, PUNKTE, STRICHE, VOLL, PILLE }

/**
 * Aussehen einer Daten-Karte (Einkauf, Woche, Heute) wie in den Widget-Entwürfen: [flaeche], Schrift [text], [neben]
 * für Nebentext, [akzent], Linienfarbe [linie] und -art, Schriftfamilien für Titel und Nebentext (Systemschriften:
 * `serif`, `sans-serif`, `monospace`). [immersiv]: die Wörter der Stile gelten.
 */
data class KartenAussehen(
    val stil: FoodyStil,
    val immersiv: Boolean,
    val flaeche: Flaeche,
    @ColorInt val text: Int,
    @ColorInt val neben: Int,
    @ColorInt val akzent: Int,
    @ColorInt val linie: Int,
    val linienArt: Linie,
    val titelSchrift: String?,
    val nebenSchrift: String?,
)

private const val TINTE = 0xFF1B1A2E.toInt()

private fun argb(wert: Long) = wert.toInt()

/** Akzent des gewählten Stils als ARGB; Milchglas hat keinen. */
internal fun akzentVon(wahl: StilWahl): Int? = FoodyStil.ausName(wahl.stil).akzent(wahl.akzent)?.farbe?.toArgb()

/**
 * Karte im Stil [wahl]. Milchglas nimmt die Farben des Hintergrundbilds (Material You, hell oder dunkel) und die
 * Rundung des Systems. [zettel]: Grauen zeigt den Einkauf auf vergilbtem Papier statt auf der dunklen Tafel.
 */
fun kartenAussehen(context: Context, wahl: StilWahl, zettel: Boolean = false): KartenAussehen {
    val stil = FoodyStil.ausName(wahl.stil)
    val immersiv = StilVariante.ausName(wahl.variante) == StilVariante.IMMERSIV
    val akzent = akzentVon(wahl)
    if (stil == FoodyStil.MILCHGLAS || akzent == null) {
        val text = context.getColor(R.color.widget_inhalt)
        val radius = context.resources.getDimension(android.R.dimen.system_app_widget_background_radius) / context.resources.displayMetrics.density
        return KartenAussehen(
            FoodyStil.MILCHGLAS, false, Flaeche(context.getColor(R.color.widget_flaeche), null, 0f, radius, false),
            text, ColorUtils.setAlphaComponent(text, 0xC0), text, ColorUtils.setAlphaComponent(text, 0x40), Linie.KEINE, null, null,
        )
    }
    return when (stil) {
        FoodyStil.SAGE -> KartenAussehen(
            stil, immersiv, Flaeche(argb(0xFF1E1B16), argb(0xFF5A4F38), 1f, 2f, false),
            argb(0xFFF4EEDD), argb(0xFF9A9079), akzent, argb(0xFF5A4F38), Linie.KEINE, "serif", null,
        )
        FoodyStil.ANIME -> KartenAussehen(
            stil, immersiv, Flaeche(argb(0xFFFFFFFF), TINTE, 3f, 22f, immersiv),
            TINTE, ColorUtils.setAlphaComponent(TINTE, 0xC0), akzent, TINTE, if (immersiv) Linie.PILLE else Linie.KEINE, "sans-serif-black", null,
        )
        FoodyStil.EDO -> KartenAussehen(
            stil, immersiv, Flaeche(argb(0xFFF6EFDF), argb(0xFFC9B98F), 1f, 18f, false),
            argb(0xFF231F1A), argb(0xFF5A5145), akzent, argb(0xFFC9B98F), Linie.PUNKTE, "serif", null,
        )
        FoodyStil.BAYRISCH -> KartenAussehen(
            stil, immersiv, Flaeche(argb(0xFFFFFFFF), argb(0xFF2B1E14), 2f, 22f, false),
            argb(0xFF2B1E14), argb(0xFF5C4A3A), akzent, argb(0xFFCDBB9C), Linie.STRICHE, "serif", null,
        )
        FoodyStil.GRAUEN -> if (zettel) {
            KartenAussehen(
                stil, immersiv, Flaeche(argb(0xFFDCCB98), argb(0xFF8C7A4E), 1f, 22f, false),
                argb(0xFF2A2418), argb(0xFF5A4C34), argb(0xFF2F5A43), ColorUtils.setAlphaComponent(argb(0xFF2A2418), 0x4D), Linie.PUNKTE,
                "serif", "monospace",
            )
        } else {
            KartenAussehen(
                stil, immersiv, Flaeche(argb(0xFF0C1210), akzent, 1f, 22f, false),
                argb(0xFFCFE0D6), argb(0xFF7F948A), akzent, argb(0xFF24352E), Linie.VOLL, "serif", "monospace",
            )
        }
        else -> KartenAussehen(
            stil, immersiv, Flaeche(argb(0xFF070C18), ColorUtils.setAlphaComponent(argb(0xFF5CE1FF), 0x59), 1f, 22f, false),
            argb(0xFFE9EEF2), argb(0xFF8A97AD), akzent, argb(0xFF1C2A44), Linie.VOLL, "sans-serif-condensed", "monospace",
        )
    }
}

/** [text] in der Schriftfamilie [familie] (`null` = Standard), wahlweise fett – `RemoteViews` nehmen Spans mit. */
internal fun schrift(text: String, familie: String?, fett: Boolean = false): CharSequence {
    if (familie == null && !fett) return text
    return SpannableString(text).apply {
        familie?.let { setSpan(TypefaceSpan(it), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        if (fett) setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

/** Hintergrund einer Listenzeile für [art], eingefärbt mit [farbe]; der Abstand innen steckt im Bild. */
internal fun RemoteViews.zeilenLinie(id: Int, art: Linie, @ColorInt farbe: Int) {
    val bild = when (art) {
        Linie.KEINE -> return
        Linie.PUNKTE -> R.drawable.widget_linie_punkte
        Linie.STRICHE -> R.drawable.widget_linie_striche
        Linie.VOLL -> R.drawable.widget_linie_voll
        Linie.PILLE -> R.drawable.widget_pille
    }
    setInt(id, "setBackgroundResource", bild)
    setColorStateList(id, "setBackgroundTintList", ColorStateList.valueOf(farbe))
}

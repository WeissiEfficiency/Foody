package de.foody.app.widget

import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import de.foody.app.R
import de.foody.core.data.StilWahl
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante

/** Welcher Knopf: „Scannen“ oder „KI-Foto“. */
enum class KnopfArt { SCAN, FOTO }

/**
 * Aussehen eines Knopf-Widgets im gewählten Stil, wie in den Widget-Entwürfen: Fläche, Schrift- und Symbolfarbe, Rand
 * ([rand] `null` = keiner) mit [randBreite], Rundung [radius] und der Versatzschatten (Anime, Immersiv). [text] ersetzt
 * das Wort des Knopfs (Sage „SIEGEL“, Anime „SCAN!“), `null` = „Scannen“ bzw. „Foto“. Farben als ARGB, Maße in dp.
 */
data class KnopfAussehen(
    val flaeche: Int,
    val inhalt: Int,
    val symbol: Int,
    val rand: Int?,
    val randBreite: Float,
    val radius: Float,
    val schatten: Boolean,
    val text: String?,
)

private const val TINTE = 0xFF1B1A2E.toInt()

/** Versatz des Schattens (Anime) in dp. */
internal const val SCHATTEN_VERSATZ = 4f

/**
 * Aussehen für [wahl]; `null` bei Milchglas – dann bleibt der Knopf in den Farben des Hintergrundbilds. Die Wörter der
 * Stile gelten nur in Immersiv; Ruhig behält Farben und Form, aber ohne Schatten.
 */
fun knopfAussehen(wahl: StilWahl, art: KnopfArt): KnopfAussehen? {
    val stil = FoodyStil.ausName(wahl.stil)
    val immersiv = StilVariante.ausName(wahl.variante) == StilVariante.IMMERSIV
    val akzent = stil.akzent(wahl.akzent)?.farbe?.toArgb() ?: return null
    val scan = art == KnopfArt.SCAN
    return when (stil) {
        FoodyStil.MILCHGLAS -> null
        // Q: dunkle Fläche mit feinem Goldrand, Cinzel-Wörter des Entwurfs
        FoodyStil.SAGE -> KnopfAussehen(
            0xFF1E1B16.toInt(), 0xFFF4EEDD.toInt(), akzent, 0xFF5A4F38.toInt(), 1f, 4f, false,
            if (immersiv) (if (scan) "SIEGEL" else "DEUTEN") else null,
        )
        // R: Türkis und Gelb mit Tintenrand und Versatzschatten
        FoodyStil.ANIME -> KnopfAussehen(
            if (scan) 0xFF3EC6E0.toInt() else 0xFFFFE45C.toInt(), TINTE, TINTE, TINTE, 2.5f, 18f, immersiv,
            if (immersiv) (if (scan) "SCAN!" else "FOTO!") else null,
        )
        // S: Tusche-Fläche mit Papierschrift, Symbol im Zinnober
        FoodyStil.EDO -> KnopfAussehen(0xFF231F1A.toInt(), 0xFFF6EFDF.toInt(), akzent, null, 0f, 18f, false, null)
        // T: Himmelblau mit dunklem Rand, weiße Schrift
        FoodyStil.BAYRISCH -> KnopfAussehen(akzent, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF2B1E14.toInt(), 2f, 22f, false, null)
        // U: dunkle Karte mit Glutrand
        FoodyStil.GRAUEN -> KnopfAussehen(0xFF0C1210.toInt(), 0xFFCFE0D6.toInt(), akzent, akzent, 1f, 22f, false, null)
        // V: Amberfläche mit dunkler Schrift
        FoodyStil.KREUZER -> KnopfAussehen(akzent, 0xFF1A1206.toInt(), 0xFF1A1206.toInt(), null, 0f, 22f, false, null)
    }
}

/** Setzt [a] auf die Ansicht eines Knopf-Widgets (Layout mit Schatten, Rand und Knopf übereinander). */
internal fun RemoteViews.stilAnwenden(a: KnopfAussehen) {
    flaecheAnwenden(Flaeche(a.flaeche, a.rand, a.randBreite, a.radius, a.schatten))
    setTextColor(R.id.widget_text, a.inhalt)
    setInt(R.id.widget_symbol, "setColorFilter", a.symbol)
    a.text?.let { setTextViewText(R.id.widget_text, it) }
}

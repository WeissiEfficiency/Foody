package de.foody.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.RemoteViews
import de.foody.app.R
import de.foody.app.navigation.SCHEMA
import de.foody.core.data.DarstellungSpeicher
import de.foody.core.data.StilWahl
import de.foody.core.data.heute.OffenerEinkauf
import de.foody.core.designsystem.stil.FoodyStil

/** Höchstens so viele Artikel zeigt das Widget „Einkauf“ einzeln. */
internal const val EINKAUF_ZEILEN = 4

private val ZEILEN = intArrayOf(R.id.widget_zeile1, R.id.widget_zeile2, R.id.widget_zeile3, R.id.widget_zeile4)

/** Widget „Einkauf“ (2×2): offene Artikel der jüngsten Liste; der Tipp öffnet sie. */
class EinkaufWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = imHintergrund {
        val einkauf = context.widgetDaten().einkaufJetzt()
        manager.updateAppWidget(ids, einkaufAnsicht(context, einkauf, DarstellungSpeicher(context).stilAktuell()))
    }
}

/** Wörter des Widgets „Einkauf“: Titel, Anzahl im Kopf, Zeile unten; je Stil in Immersiv wie im Entwurf. */
internal data class EinkaufTexte(val titel: String, val zahl: String?, val fuss: String?, val stempel: String?, val vorne: String)

internal fun einkaufTexte(stil: FoodyStil, immersiv: Boolean, anzahl: Int): EinkaufTexte {
    val rest = (anzahl - EINKAUF_ZEILEN).coerceAtLeast(0)
    val weitere = if (rest > 0) "+ $rest weitere" else null
    if (anzahl == 0) return EinkaufTexte(if (immersiv) einkaufTitel(stil) else "Einkauf", null, "Alles erledigt", null, "")
    if (!immersiv) return EinkaufTexte("Einkauf", "$anzahl offen", weitere, null, "")
    return when (stil) {
        FoodyStil.SAGE -> EinkaufTexte("DER HÄNDLER", "$anzahl", weitere, null, "◇  ")
        FoodyStil.ANIME -> EinkaufTexte("QUEST!", "$anzahl offen", weitere, null, "○ ")
        FoodyStil.EDO -> EinkaufTexte("Einkauf", null, "$anzahl Dinge offen", "籠", "")
        FoodyStil.BAYRISCH -> EinkaufTexte("Eikaufn", null, "no $anzahl offn", null, "")
        FoodyStil.GRAUEN -> EinkaufTexte("Expeditionsliste", null, "noch $anzahl offen", null, "")
        FoodyStil.KREUZER -> EinkaufTexte("Fracht", null, "$anzahl OFFEN", null, "")
        FoodyStil.MILCHGLAS -> EinkaufTexte("Einkauf", "$anzahl offen", weitere, null, "")
    }
}

private fun einkaufTitel(stil: FoodyStil) = when (stil) {
    FoodyStil.SAGE -> "DER HÄNDLER"
    FoodyStil.ANIME -> "QUEST!"
    FoodyStil.BAYRISCH -> "Eikaufn"
    FoodyStil.GRAUEN -> "Expeditionsliste"
    FoodyStil.KREUZER -> "Fracht"
    else -> "Einkauf"
}

/** Was TalkBack liest: „Einkauf: 9 offen: Paprika, Feta, …“ bzw. „Einkauf: alles erledigt“. */
internal fun einkaufBeschreibung(einkauf: OffenerEinkauf?): String {
    if (einkauf == null || einkauf.anzahl == 0) return "Einkauf: alles erledigt"
    return "Einkauf: ${einkauf.anzahl} offen: " + einkauf.artikel.take(EINKAUF_ZEILEN).joinToString(", ") { it.name } +
        if (einkauf.anzahl > EINKAUF_ZEILEN) " und ${einkauf.anzahl - EINKAUF_ZEILEN} weitere" else ""
}

/** Ansicht des Widgets „Einkauf“ für [einkauf] im Stil [wahl]. */
fun einkaufAnsicht(context: Context, einkauf: OffenerEinkauf?, wahl: StilWahl): RemoteViews = RemoteViews(context.packageName, R.layout.widget_einkauf).apply {
    val a = kartenAussehen(context, wahl, zettel = true)
    val anzahl = einkauf?.anzahl ?: 0
    val t = einkaufTexte(a.stil, a.immersiv, anzahl)
    if (a.stil != FoodyStil.MILCHGLAS) flaecheAnwenden(a.flaeche)
    setTextViewText(R.id.widget_titel, schrift(t.titel, a.titelSchrift, fett = true))
    setTextColor(R.id.widget_titel, a.text)
    setTextViewText(R.id.widget_zahl, t.zahl.orEmpty())
    setTextColor(R.id.widget_zahl, a.neben)
    if (t.stempel != null) {
        setViewVisibility(R.id.widget_stempel, View.VISIBLE)
        setTextViewText(R.id.widget_stempel, t.stempel)
        setColorStateList(R.id.widget_stempel, "setBackgroundTintList", ColorStateList.valueOf(a.akzent))
    }
    val artikel = einkauf?.artikel.orEmpty().take(EINKAUF_ZEILEN)
    artikel.forEachIndexed { i, x ->
        val id = ZEILEN[i]
        setViewVisibility(id, View.VISIBLE)
        setTextViewText(id, schrift(t.vorne + x.name, if (a.stil == FoodyStil.GRAUEN) a.nebenSchrift else null))
        setTextColor(id, a.text)
        zeilenLinie(id, a.linienArt, a.linie)
    }
    setTextViewText(R.id.widget_fuss, schrift(t.fuss.orEmpty(), a.nebenSchrift))
    setTextColor(R.id.widget_fuss, a.neben)
    setContentDescription(android.R.id.background, einkaufBeschreibung(einkauf))
    setOnClickPendingIntent(android.R.id.background, oeffnen(context, einkauf?.let { "${SCHEMA}einkauf/${it.listeId}" }, WIDGET_EINKAUF))
}

/** requestCodes der PendingIntents: je Widget eigene, damit die Links sich nicht überschreiben. */
internal const val WIDGET_EINKAUF = 10
internal const val WIDGET_EINTRAGEN = 11

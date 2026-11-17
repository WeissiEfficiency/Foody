package de.foody.feature.rezepte.liste.stil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.edo.EdoKnopf
import de.foody.core.designsystem.stil.edo.edoShu
import de.foody.core.designsystem.stil.szenenWort
import de.foody.core.designsystem.stil.titelGroesse
import kotlin.math.min

/*
 * „Rezepte“ im Edo-Stil (S) in Immersiv (Entwurf `S-Rezepte`): „FÄCHER · BLATT ANTIPPEN“ über „Rezepte“, die Treffer
 * der Seite als Fächer aus Papierblättern (das gewählte in Zinnober), darunter das gewählte Rezept als Papierkarte mit
 * Art, Favorit, Name, Zeit und kcal je Portion, Zutatensatz und „Öffnen“.
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EdoKopf(n: Int, neu: @Composable () -> Unit) {
    val f = FoodyTheme.farben
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.align(Alignment.Bottom).padding(end = 8.dp)) {
            // Ruhig wie im Entwurf ohne den Fächer: „6 REZEPTE“
            val unter = if (FoodyTheme.stil.inszeniert) "FÄCHER" else "REZEPTE"
            Text(
                if (n > 0) szenenWort("FÄCHER · $n REZEPTE", "$n REZEPTE") else unter,
                style = Edo.kopf(),
                color = f.nebentext,
            )
            // Der Titel wächst mit der Systemschrift nur bis 130 %
            Text("Rezepte", Modifier.semantics { heading() }, style = Edo.titel(titelGroesse(32f)), color = f.text)
        }
        Box(Modifier.align(Alignment.Bottom)) { neu() }
    }
}

/** Die Treffer der Seite als Fächer: Blätter, leicht gegeneinander gedreht, um ihren Fuß; das gewählte in Zinnober. */
@Composable
internal fun EdoFaecher(liste: List<RezeptKarte>, gewaehlt: String, onWahl: (String) -> Unit) {
    if (!FoodyTheme.stil.inszeniert) {
        EdoListe(liste, gewaehlt, onWahl)
        return
    }
    val mitte = (liste.size - 1) / 2f
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).drawBehind {
            // Der Holzgriff, in dem die Blätter stecken
            val y = size.height - 4.dp.toPx()
            drawLine(Edo.holz, Offset(size.width * 0.3f, y), Offset(size.width * 0.7f, y), 8.dp.toPx())
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        liste.forEachIndexed { i, r ->
            Blatt(r, i, an = r.id == gewaehlt, drehung = (i - mitte) * 8f, Modifier.width(54.dp)) { onWahl(r.id) }
        }
    }
}

/** Ruhig wie im Entwurf: statt des Fächers die Treffer als Knöpfe zu zweit, das gewählte im Zinnober. */
@Composable
private fun EdoListe(liste: List<RezeptKarte>, gewaehlt: String, onWahl: (String) -> Unit) {
    val f = FoodyTheme.farben
    val shu = edoShu()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        liste.chunked(2).forEachIndexed { reihe, paar ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                paar.forEachIndexed { j, r ->
                    val an = r.id == gewaehlt
                    val grund = when {
                        an -> shu
                        (reihe * 2 + j) % 2 == 0 -> Edo.papier
                        else -> BLATT_DUNKEL
                    }
                    Box(
                        Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).background(grund, Edo.form).border(1.5.dp, f.text, Edo.form)
                            .rezeptWahl(r, an) { onWahl(r.id) }.padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(edoKurz(r.name), style = Edo.text(15f), color = if (an) Color.White else f.text, textAlign = TextAlign.Center)
                    }
                }
                if (paar.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Kurzname eines Rezepts für die Knöpfe: „Ofengemüse mit Feta …“ → „Ofengemüse“. */
internal fun edoKurz(name: String): String =
    name.substringBefore(" mit ").substringBefore(" und ").substringBefore(",").trim().ifEmpty { name }

/** Ein Fächerblatt: oben breiter als unten, mit Anfangsbuchstabe und Punkt für Favoriten. */
private val BLATT = GenericShape { s, _ ->
    val schmal = s.width * 0.18f
    moveTo(0f, 0f)
    lineTo(s.width, 0f)
    lineTo(s.width - schmal, s.height)
    lineTo(schmal, s.height)
    close()
}

/** Das dunklere Papier jedes zweiten Blatts. */
private val BLATT_DUNKEL = Color(0xFFEADFC4)

@Composable
private fun Blatt(r: RezeptKarte, i: Int, an: Boolean, drehung: Float, modifier: Modifier, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    val shu = edoShu()
    val grund = when {
        an -> shu
        i % 2 == 0 -> Edo.papier
        else -> BLATT_DUNKEL
    }
    Column(
        modifier.heightIn(min = 128.dp)
            .graphicsLayer {
                rotationZ = drehung
                transformOrigin = TransformOrigin(0.5f, 1f)
                translationY = if (an) -10.dp.toPx() else 0f
            }
            .background(grund, BLATT).border(1.dp, if (an) shu else Edo.papierRand, BLATT)
            .rezeptWahl(r, an) { onKlick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(anfang(r), style = Edo.titel(22f), color = if (an) Color.White else f.text)
        if (r.favorit) Box(Modifier.size(6.dp).background(if (an) Color.White else shu, BLATT))
    }
}

/** Die Art wie im Entwurf: erster Tag, sonst „AUS DER KÜCHE“. */
internal fun edoArt(r: RezeptKarte): String = (r.tags.firstOrNull() ?: "Aus der Küche").uppercase(java.util.Locale.GERMAN)

/** Das gewählte Rezept als Papierkarte mit „Öffnen“. */
@Composable
internal fun EdoBlatt(r: RezeptKarte, onOeffnen: (String) -> Unit) {
    val f = FoodyTheme.farben
    val shu = edoShu()
    Column(
        Modifier.fillMaxWidth().background(Edo.papier, Edo.form).border(1.dp, Edo.papierRand, Edo.form)
            .drawBehind {
                // Zinnober-Kante links wie ein Stempelrand
                drawLine(shu, Offset(2.dp.toPx(), 0f), Offset(2.dp.toPx(), size.height), 4.dp.toPx())
            }
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(edoArt(r), Modifier.weight(1f), style = Edo.kopf(), color = f.nebentext)
            if (r.favorit) Text("● Favorit", style = Edo.text(13f, FontWeight.Bold), color = f.primaer)
        }
        Text("${r.name} ›", style = Edo.titel(22f), color = f.text)
        zeitUndKcal(r)?.let { Text(if (r.kcalJePortion != null) "$it je Portion" else it, style = Edo.text(14f), color = f.nebentext) }
        zutatenSatz(r)?.let {
            Text(it, Modifier.padding(top = 2.dp), style = Edo.text(15f), color = f.text, textAlign = TextAlign.Start)
        }
        EdoKnopf("Öffnen", { onOeffnen(r.id) }, Modifier.fillMaxWidth().padding(top = 6.dp), beschreibung = "Öffnen: ${r.name}")
    }
}

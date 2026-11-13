package de.foody.feature.rezepte.liste.stil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.bayrisch.BayrischKnopf
import de.foody.core.designsystem.stil.bayrisch.bayrischBlau
import kotlin.math.min

/*
 * „Rezepte“ im Bayrischen Stil (T) in Immersiv (Entwurf `T-Rezepte`): „Rezepte – Vom Lebkuchenstand“, die Treffer
 * der Seite als Lebkuchenherzen zu dritt an Holzstangen (das gewählte schräg, mit rotem Band), darunter das gewählte
 * Rezept als Karte mit doppeltem Rahmen, Art, Herz für Favoriten, Zeit, kcal pro Portion und „Pack ma's!“.
 */

/** Teigtöne der Herzen wie im Entwurf. */
private val TEIG = listOf(Color(0xFFA8622E), Color(0xFF9A5526), Color(0xFFB36A34))
private val HOLZ = Color(0xFF7A4E2C)
private val ROT = Color(0xFFE04A5A)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BayrischKopf(n: Int, neu: @Composable () -> Unit) {
    val f = FoodyTheme.farben
    val k = LocalDensity.current.fontScale
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.align(Alignment.Bottom).padding(end = 8.dp)) {
            // Der Titel wächst mit der Systemschrift nur bis 130 %
            Text("Rezepte", Modifier.semantics { heading() }, style = Bayrisch.fraktur(32f * min(k, 1.3f) / k), color = f.text)
            Text(if (n > 0) "Vom Lebkuchenstand · $n Herzen" else "Vom Lebkuchenstand", style = Bayrisch.text(15f), color = f.nebentext)
        }
        Box(Modifier.align(Alignment.Bottom)) { neu() }
    }
}

/** Herzform für die Lebkuchen. */
private val HERZ = GenericShape { s, _ ->
    val w = s.width
    val h = s.height
    moveTo(w / 2, h)
    cubicTo(w * 0.05f, h * 0.62f, -w * 0.05f, h * 0.2f, w * 0.25f, h * 0.06f)
    cubicTo(w * 0.4f, 0f, w * 0.48f, h * 0.08f, w / 2, h * 0.2f)
    cubicTo(w * 0.52f, h * 0.08f, w * 0.6f, 0f, w * 0.75f, h * 0.06f)
    cubicTo(w * 1.05f, h * 0.2f, w * 0.95f, h * 0.62f, w / 2, h)
    close()
}

/** Die Treffer der Seite als Herzen zu dritt; über jeder Reihe eine Holzstange mit den Schnüren. */
@Composable
internal fun BayrischHerzen(liste: List<RezeptKarte>, gewaehlt: String, onWahl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        liste.chunked(3).forEachIndexed { reihe, drei ->
            Row(
                Modifier.fillMaxWidth().drawBehind {
                    drawLine(HOLZ, Offset(0f, 3.dp.toPx()), Offset(size.width, 3.dp.toPx()), 6.dp.toPx())
                }.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                drei.forEachIndexed { spalte, r ->
                    Herz(r, reihe * 3 + spalte, an = r.id == gewaehlt, Modifier.weight(1f)) { onWahl(r.id) }
                }
                repeat(3 - drei.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Herz(r: RezeptKarte, i: Int, an: Boolean, modifier: Modifier, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    val band = if (an) ROT else bayrischBlau()
    Column(
        modifier.rezeptWahl(r, an, onKlick).padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.padding(top = 14.dp).fillMaxWidth(0.92f).aspectRatio(1.05f).rotate(if (an) -6f else 0f)
                .drawBehind {
                    // Schnur zur Stange und das Band
                    drawLine(band, Offset(size.width / 2, -14.dp.toPx()), Offset(size.width / 2, size.height * 0.18f), 3.dp.toPx())
                }
                .background(TEIG[i % TEIG.size], HERZ)
                .drawBehind {
                    // Zuckerguss-Rand, gestrichelt
                    val rand = HERZ.createOutline(
                        size.copy(width = size.width - 12.dp.toPx(), height = size.height - 12.dp.toPx()), layoutDirection, this,
                    )
                    translate(6.dp.toPx(), 6.dp.toPx()) {
                        drawOutline(rand, Color.White, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
                    }
                }
                .then(if (an) Modifier.border(2.dp, band, HERZ) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(anfang(r), Modifier.padding(bottom = 10.dp), style = Bayrisch.titel(26f), color = Color.White)
        }
        Text(r.name, style = Bayrisch.text(13.5f, if (an) FontWeight.Bold else FontWeight.Normal), color = f.text, textAlign = TextAlign.Center)
    }
}

/** Die Art in Mundart wie im Entwurf: erster Tag, sonst „AUS DA KUCHL“. */
internal fun bayrischArt(r: RezeptKarte): String = (r.tags.firstOrNull() ?: "Aus da Kuchl").uppercase(java.util.Locale.GERMAN)

/** Das gewählte Rezept als Karte mit doppeltem Rahmen und „Pack ma's!“. */
@Composable
internal fun BayrischKarte(r: RezeptKarte, onOeffnen: (String) -> Unit) {
    val f = FoodyTheme.farben
    val blau = bayrischBlau()
    val form = Bayrisch.kartenForm
    Box(Modifier.fillMaxWidth().background(Color.White, form).border(2.dp, blau, form).padding(4.dp)) {
        Column(
            Modifier.fillMaxWidth().border(1.dp, f.balkenSpur, form).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(bayrischArt(r), Modifier.weight(1f), style = Bayrisch.text(13f, FontWeight.Bold), color = f.nebentext)
                Text(if (r.favorit) "♥" else "♡", style = Bayrisch.titel(20f), color = if (r.favorit) Color(0xFFB8322A) else f.nebentext)
            }
            Text(r.name, style = Bayrisch.titel(22f), color = f.text)
            zeitUndKcal(r)?.let { Text(if (r.kcalJePortion != null) "$it pro Portion" else it, style = Bayrisch.text(15f), color = f.nebentext) }
            zutatenSatz(r)?.let { Text(it, style = Bayrisch.text(15f), color = f.text) }
            BayrischKnopf("Pack ma's!", { onOeffnen(r.id) }, Modifier.fillMaxWidth().padding(top = 6.dp), beschreibung = "Öffnen: ${r.name}")
        }
    }
}

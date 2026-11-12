package de.foody.feature.rezepte.liste.stil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.anime.animeSchatten
import kotlin.math.min

/*
 * „Rezepte“ im Anime-Stil (R) in Immersiv (Entwurf `R-Rezepte`): schräges „REZEPTE“ mit Schatten im Akzent und
 * gelbem „+ Neu“, darunter die Treffer als Comic-Karten zu zweit: farbiger Kopf mit Speedlines und Anfangsbuchstabe,
 * Sterne für „gekocht“, Marke FAV/FIX/NEU, Name und „40 Min. · 553 kcal“. Antippen öffnet das Rezept.
 */

/** Kopffarben der Karten wie im Entwurf; die erste nimmt den Akzent. */
private val KOEPFE = listOf(Color(0xFFFFE45C), Color(0xFF3EC6E0), Color(0xFFFFB23E), Color(0xFFA98BFF), Color(0xFF7EE0A8))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnimeKopf(n: Int, neu: @Composable () -> Unit) {
    val f = FoodyTheme.farben
    val d = LocalDensity.current
    val k = d.fontScale
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.align(Alignment.CenterVertically).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Der große Titel wächst mit der Systemschrift nur bis 130 %
            Text(
                "REZEPTE",
                Modifier.semantics {
                    heading()
                    contentDescription = "Rezepte"
                },
                style = Anime.titel(36f * min(k, 1.3f) / k, 0f).copy(
                    textGeometricTransform = TextGeometricTransform(skewX = -0.14f),
                    shadow = Shadow(FoodyTheme.stil.zier, Offset(3f * d.density, 3f * d.density), 0f),
                ),
                color = f.text,
            )
            if (n > 0) {
                Text(
                    "$n", Modifier.padding(start = 8.dp).rotate(4f).background(f.text, Anime.pille).padding(horizontal = 8.dp, vertical = 2.dp)
                        .semantics { contentDescription = "$n Rezepte" },
                    style = Anime.text(13f, FontWeight.ExtraBold), color = Color.White,
                )
            }
        }
        Box(Modifier.align(Alignment.CenterVertically)) { neu() }
    }
}

/** Die Treffer der Seite als Comic-Karten zu zweit; die Reihe wächst mit der Schrift. */
@Composable
internal fun AnimeRaster(liste: List<RezeptKarte>, onOeffnen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        liste.chunked(2).forEachIndexed { reihe, paar ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                paar.forEachIndexed { spalte, r ->
                    val i = reihe * 2 + spalte
                    Karte(r, i, Modifier.weight(1f).fillMaxHeight()) { onOeffnen(r.id) }
                }
                if (paar.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Marke oben rechts: Favorit, schnell (≤ 30 Min.) oder neu. */
internal fun animeMarke(r: RezeptKarte): String = when {
    r.favorit -> "FAV"
    (r.zeitMinuten ?: Int.MAX_VALUE) <= 30 -> "FIX"
    else -> "NEU"
}

/** „★★☆“: bis zu drei Sterne, je öfter gekocht, desto mehr. */
internal fun animeSterne(gekocht: Int): String {
    val voll = gekocht.coerceIn(0, 3)
    return "★".repeat(voll) + "☆".repeat(3 - voll)
}

@Composable
private fun Karte(r: RezeptKarte, i: Int, modifier: Modifier, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    val form = RoundedCornerShape(14.dp)
    val kopf = if (i == 0) FoodyTheme.stil.zier else KOEPFE[i % KOEPFE.size]
    Column(
        modifier.padding(end = 4.dp, bottom = 4.dp).rotate(if (i % 2 == 0) -0.8f else 0.8f)
            .animeSchatten(form, f.text, 4.dp).clip(form).background(Color.White).border(2.5.dp, f.text, form)
            .clickable(role = Role.Button, onClick = onKlick)
            .semantics(mergeDescendants = true) { contentDescription = "Öffnen: ${rezeptBeschreibung(r)}" },
    ) {
        Box(Modifier.fillMaxWidth().height(86.dp).background(kopf)) {
            Speedlines()
            Text(anfang(r), Modifier.align(Alignment.Center), style = Anime.titel(40f, 0f), color = f.text)
            Text(
                animeMarke(r), Modifier.align(Alignment.TopEnd).padding(6.dp).background(f.text, Anime.pille).padding(horizontal = 7.dp, vertical = 1.dp),
                style = Anime.text(11f, FontWeight.ExtraBold), color = Color.White,
            )
            Text(animeSterne(r.gekocht), Modifier.align(Alignment.BottomStart).padding(6.dp), style = Anime.text(13f), color = f.text)
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.name, style = Anime.text(15f, FontWeight.ExtraBold), color = f.text)
            zeitUndKcal(r)?.let { Text(it, style = Anime.text(12.5f), color = f.nebentext) }
        }
    }
}

/** Weiße Speedlines im Kartenkopf; stumm. */
@Composable
private fun Speedlines() {
    Canvas(Modifier.fillMaxWidth().fillMaxHeight().clearAndSetSemantics { }) {
        val mitte = Offset(size.width * 0.5f, size.height * 0.5f)
        val enden = listOf(-0.1f to -0.2f, 0.3f to -0.4f, 0.8f to -0.4f, 1.1f to 0.1f, 1.1f to 0.9f, 0.7f to 1.3f, 0.2f to 1.3f, -0.1f to 0.8f)
        enden.forEach { (x, y) ->
            drawLine(Color.White.copy(alpha = 0.35f), mitte, Offset(size.width * x, size.height * y), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

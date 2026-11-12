package de.foody.feature.woche.stil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.data.woche.WochenTag
import de.foody.core.data.wochentagKurz
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.anime.AnimeKnopf
import de.foody.core.designsystem.stil.anime.animeSchatten
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.min

/**
 * „Woche“ im Anime-Stil (R) in Immersiv (Entwurf `R-Woche`): schräges „WOCHE!“ mit gelbem Abzeichen „KW 43 · 6/7“,
 * die sieben Tage als Comic-Panels in drei Reihen (heute im Akzent mit Speedlines und groß, leere Tage gestrichelt
 * mit „?!“), darunter die Karte des gewählten Tages mit seinen Gerichten, „+ Mahlzeit“ und „Tage füllen“.
 */
@Composable
internal fun WocheAnime(w: WocheDaten, aktionen: WocheSageAktionen, unten: Dp) {
    var gewaehlt by rememberGewaehlt(w)
    WocheRahmen(unten) {
        Kopf(w)
        Blaettern(w, aktionen, Anime.text(14f, FontWeight.Bold))
        if (w.tage.size == 7) Panels(w, gewaehlt) { gewaehlt = it }
        w.tage.getOrNull(gewaehlt)?.let { Tageskarte(it, it.datum == w.heute, aktionen) }
    }
}

/** Füllfarben der Panels Montag bis Sonntag wie im Entwurf; heute nimmt den Akzent. */
private val PANEL = listOf(Color.White, Color(0xFF7EE0A8), Color.White, Color(0xFFA98BFF), Color(0xFFFFB23E), Color.White, Color.White)
private val DREHUNG = listOf(-1f, 1f, -0.5f, 0.5f, -1f, -0.5f, 1.5f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kopf(w: WocheDaten) {
    val f = FoodyTheme.farben
    val d = LocalDensity.current
    val k = d.fontScale
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Der große Titel wächst mit der Systemschrift nur bis 130 %
        Text(
            "WOCHE!",
            Modifier.align(Alignment.CenterVertically).padding(end = 8.dp).semantics {
                heading()
                contentDescription = "Woche"
            },
            style = Anime.titel(40f * min(k, 1.3f) / k, 0f).copy(
                textGeometricTransform = TextGeometricTransform(skewX = -0.14f),
                shadow = Shadow(FoodyTheme.stil.zier, Offset(3f * d.density, 3f * d.density), 0f),
            ),
            color = f.text,
        )
        w.woche?.let { woche ->
            val kw = woche.montag.get(WeekFields.ISO.weekOfWeekBasedYear())
            val form = RoundedCornerShape(12.dp)
            Text(
                "KW $kw · ${w.geplant}/${w.tage.size}",
                Modifier.align(Alignment.CenterVertically).rotate(3f)
                    .semantics { contentDescription = "KW $kw, ${w.geplant} von ${w.tage.size} Tagen geplant" }
                    .background(Anime.gelb, form).border(2.5.dp, f.text, form).padding(horizontal = 10.dp, vertical = 4.dp),
                style = Anime.text(13f, FontWeight.ExtraBold), color = f.text,
            )
        }
    }
}

/** Drei Reihen Panels: Mo Di Mi, Do Fr, Sa So (Breiten wie im Entwurf); die Reihe wächst mit der Schrift. */
@Composable
private fun Panels(w: WocheDaten, gewaehlt: Int, onWahl: (Int) -> Unit) {
    val reihen = listOf(listOf(0 to 2f, 1 to 2f, 2 to 2f), listOf(3 to 3f, 4 to 3f), listOf(5 to 4f, 6 to 2f))
    val hoehen = listOf(104.dp, 104.dp, 132.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        reihen.forEachIndexed { r, reihe ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                reihe.forEach { (i, gewicht) ->
                    Panel(
                        w.tage[i], i, heute = i == w.heuteIndex, an = i == gewaehlt,
                        Modifier.weight(gewicht).fillMaxHeight().heightIn(min = hoehen[r]),
                    ) { onWahl(i) }
                }
            }
        }
    }
}

@Composable
private fun Panel(t: WochenTag, i: Int, heute: Boolean, an: Boolean, modifier: Modifier, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    val leer = t.eintraege.isEmpty()
    val form = RoundedCornerShape(4.dp)
    val grund = when {
        heute -> FoodyTheme.stil.zier
        leer -> Color.White
        else -> PANEL[i]
    }
    Box(
        modifier.padding(end = 6.dp, bottom = 6.dp).rotate(DREHUNG[i])
            .animeSchatten(form, f.text, if (an) 6.dp else 3.dp)
            .clip(form).background(grund)
            .then(
                if (leer && !an) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            f.text, cornerRadius = CornerRadius(4.dp.toPx()),
                            style = Stroke(2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))),
                        )
                    }
                } else {
                    Modifier.border(if (an) 3.5.dp else 2.5.dp, f.text, form)
                },
            )
            .tagWahl(t, heute, an, onKlick),
    ) {
        if (heute) Speedlines()
        Column(Modifier.fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(wochentagKurz(t.datum).uppercase(Locale.GERMAN), style = Anime.titel(18f, 0f), color = f.text)
            Text(
                if (leer) "?!" else t.eintraege.joinToString(" + ") { it.rezeptName },
                style = when {
                    leer -> Anime.titel(28f, 0f)
                    heute -> Anime.text(18f, FontWeight.ExtraBold)
                    else -> Anime.text(13f, FontWeight.ExtraBold)
                },
                color = f.text,
            )
        }
    }
}

/** Weiße Speedlines hinter dem Panel von heute; stumm. */
@Composable
private fun Speedlines() {
    Canvas(Modifier.fillMaxWidth().fillMaxHeight().clearAndSetSemantics { }) {
        val mitte = Offset(size.width * 0.7f, size.height * 0.5f)
        val enden = listOf(1.2f to -0.3f, 1.2f to 0.3f, 1.2f to 0.8f, 1.1f to 1.3f, 0.8f to -0.4f, 0.5f to -0.4f, 0.4f to 1.4f)
        enden.forEach { (x, y) ->
            drawLine(Color.White.copy(alpha = 0.3f), mitte, Offset(size.width * x, size.height * y), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun Tageskarte(t: WochenTag, heute: Boolean, aktionen: WocheSageAktionen) {
    val f = FoodyTheme.farben
    val form = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 4.dp).animeSchatten(form, f.text, 4.dp)
            .background(Color.White, form).border(2.5.dp, f.text, form).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            tagLangText(t.datum) + if (heute) " · heute!" else "",
            Modifier.background(f.text, Anime.pille).padding(horizontal = 10.dp, vertical = 2.dp),
            style = Anime.text(12f, FontWeight.ExtraBold), color = Color.White,
        )
        if (t.eintraege.isEmpty()) {
            Text("Noch nichts geplant", style = Anime.titel(19f, 0f), color = f.text)
            Text("Nächste Folge noch offen …", style = Anime.text(14f), color = f.text)
        } else {
            t.eintraege.forEach { e ->
                Eintragszeile(
                    e, { aktionen.onEintrag(e) },
                    oben = Anime.text(12f, FontWeight.ExtraBold), gericht = Anime.titel(19f, 0f), stand = Anime.text(14f, FontWeight.Bold),
                )
            }
            tagKcal(t)?.let { Text(it, style = Anime.text(14f, FontWeight.Bold), color = f.text) }
        }
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimeKnopf("+ Mahlzeit", { aktionen.onPlus(t.datum) }, Modifier.weight(1f), beschreibung = "Mahlzeit hinzufügen")
            AnimeKnopf("Tage füllen", aktionen.onFuellen, Modifier.weight(1f), beschreibung = "Leere Tage füllen", voll = false)
        }
    }
}

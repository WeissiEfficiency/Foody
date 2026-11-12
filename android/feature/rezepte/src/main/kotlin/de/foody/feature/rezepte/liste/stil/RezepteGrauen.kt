package de.foody.feature.rezepte.liste.stil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.grauen.GrauenKnopf
import de.foody.core.designsystem.stil.grauen.grauenHell
import kotlin.math.min

/*
 * „Rezepte“ im Kosmischen Grauen (U) in Immersiv (Entwurf `U-Rezepte`): „Die Bibliothek – Ein Regal, das niemand
 * aufgestellt hat“, die Treffer der Seite als Bände auf einem Regalbrett (der gewählte herausgezogen und glühend),
 * darunter seine aufgeschlagene Pergamentseite mit Band, Name, Zeit, kcal, Favorit, dem Zutatensatz kursiv und
 * „Aufschlagen“.
 */

/** Einbände und Höhen der Bände wie im Entwurf. */
private val EINBAND = listOf(Color(0xFF2E3B2C), Color(0xFF4A2228), Color(0xFF24324A), Color(0xFF5A3A1C), Color(0xFF3A2A48), Color(0xFF1E3A36))
private val HOEHE = listOf(128, 118, 134, 112, 124, 132)
private val PERGAMENT = Color(0xFFD9CDAE)
private val TINTE = Color(0xFF2A2418)
private val TINTE_NEBEN = Color(0xFF4A4030)
private val BRETT = Color(0xFF2B2117)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GrauenKopf(n: Int, neu: @Composable () -> Unit) {
    val f = FoodyTheme.farben
    val k = LocalDensity.current.fontScale
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.align(Alignment.Bottom).padding(end = 8.dp)) {
            Text("Ein Regal, das niemand aufgestellt hat", style = Grauen.text(14f, kursiv = true), color = f.nebentext)
            // Der Titel wächst mit der Systemschrift nur bis 130 %
            Text(
                "Die Bibliothek", Modifier.semantics { heading() },
                style = Grauen.titel(30f * min(k, 1.3f) / k), color = grauenHell(),
            )
            if (n > 0) Text("$n Bände", style = Grauen.text(14f), color = f.nebentext)
        }
        Box(Modifier.align(Alignment.Bottom)) { neu() }
    }
}

/** Die Treffer der Seite als Bände auf einem Brett; der gewählte steht ein Stück heraus und glüht. */
@Composable
internal fun GrauenRegal(liste: List<RezeptKarte>, gewaehlt: String, onWahl: (String) -> Unit) {
    val glut = FoodyTheme.stil.zier
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp).drawBehind {
            drawLine(BRETT, Offset(0f, size.height - 5.dp.toPx()), Offset(size.width, size.height - 5.dp.toPx()), 10.dp.toPx())
        }.padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        liste.forEachIndexed { i, r ->
            val an = r.id == gewaehlt
            val form = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
            Column(
                Modifier.width(48.dp).offset(y = if (an) (-16).dp else 0.dp)
                    .then(if (an) Modifier.shadow(14.dp, form, ambientColor = glut, spotColor = glut) else Modifier)
                    .heightIn(min = HOEHE[i % HOEHE.size].dp).background(EINBAND[i % EINBAND.size], form)
                    .border(if (an) 1.5.dp else 1.dp, if (an) glut else Color.Black.copy(alpha = 0.4f), form)
                    .rezeptWahl(r, an) { onWahl(r.id) }
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Goldene Bünde am Rücken
                Box(Modifier.fillMaxWidth(0.7f).height(2.dp).background(Color(0xFFB59A5A).copy(alpha = 0.7f)))
                Text(anfang(r), Modifier.padding(vertical = 8.dp), style = Grauen.titel(20f), color = if (an) glut else Color(0xFFE6DCC2))
                Text(
                    r.kcalJePortion?.toString() ?: "–", style = Grauen.maschine(10f),
                    color = Color(0xFFE6DCC2).copy(alpha = 0.8f), textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Die aufgeschlagene Seite des gewählten Bandes; „Aufschlagen“ darunter im Dunkel. */
@Composable
internal fun GrauenSeite(r: RezeptKarte, onOeffnen: (String) -> Unit) {
    val glut = FoodyTheme.stil.zier
    val form = RoundedCornerShape(2.dp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().shadow(18.dp, form, ambientColor = glut, spotColor = glut).background(PERGAMENT, form)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(grauenBand(r), style = Grauen.titel(12f, 0.14f), color = TINTE_NEBEN)
            Text(r.name, style = Grauen.titel(22f), color = TINTE)
            val werte = listOfNotNull(zeitUndKcal(r)?.let { if (r.kcalJePortion != null) "$it je Portion" else it }, if (r.favorit) "♦ Favorit" else null)
            if (werte.isNotEmpty()) Text(werte.joinToString(" · "), style = Grauen.text(15f), color = TINTE_NEBEN)
            zutatenSatz(r)?.let { Text(it, style = Grauen.text(16f, kursiv = true), color = TINTE) }
        }
        GrauenKnopf("Aufschlagen", { onOeffnen(r.id) }, Modifier.fillMaxWidth(), beschreibung = "Öffnen: ${r.name}")
    }
}

/** „BAND · OFENGERICHT“ bzw. „BAND · OHNE ORDNUNG“: der erste Tag als Titel des Bandes. */
internal fun grauenBand(r: RezeptKarte): String = "Band · ${r.tags.firstOrNull() ?: "ohne Ordnung"}".uppercase(java.util.Locale.GERMAN)

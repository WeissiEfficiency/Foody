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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.grauen.GrauenKnopf
import de.foody.core.designsystem.stil.grauen.grauenHell
import de.foody.core.designsystem.stil.szenenWort
import de.foody.core.designsystem.stil.titelGroesse
import kotlin.math.min

/*
 * „Rezepte“ im Kosmischen Grauen (U) in Immersiv (Entwurf `U-Rezepte`): „Die Bibliothek – Ein Regal, das niemand
 * aufgestellt hat“, die Treffer der Seite als Bände auf einem Regalbrett (der gewählte herausgezogen und glühend),
 * darunter seine aufgeschlagene Pergamentseite mit Band, Name, Zeit, kcal, Favorit, dem Zutatensatz kursiv und
 * „Aufschlagen“. In Ruhig wie im Entwurf: „Rezepte“, die Treffer als Zeilen mit Name und kcal, eine helle Seite ohne
 * Schein, „Öffnen“.
 */

/** Einbände und Höhen der Bände wie im Entwurf. */
private val EINBAND = listOf(Color(0xFF2E3B2C), Color(0xFF4A2228), Color(0xFF24324A), Color(0xFF5A3A1C), Color(0xFF3A2A48), Color(0xFF1E3A36))
private val HOEHE = listOf(128, 118, 134, 112, 124, 132)
private val PERGAMENT = Grauen.pergament
private val TINTE = Grauen.tinte
private val TINTE_NEBEN = Grauen.tinteNeben
private val BRETT = Color(0xFF2B2117)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GrauenKopf(n: Int, neu: @Composable () -> Unit) {
    val f = FoodyTheme.farben
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.align(Alignment.Bottom).padding(end = 8.dp)) {
            if (FoodyTheme.stil.inszeniert) Text("Ein Regal, das niemand aufgestellt hat", style = Grauen.text(14f, kursiv = true), color = f.nebentext)
            // Der Titel wächst mit der Systemschrift nur bis 130 %
            Text(
                szenenWort("Die Bibliothek", "Rezepte"), Modifier.semantics { heading() },
                style = Grauen.titel(titelGroesse(30f)), color = grauenHell(),
            )
            if (n > 0) Text(szenenWort("$n Bände", "$n Rezepte"), style = Grauen.text(14f), color = f.nebentext)
        }
        Box(Modifier.align(Alignment.Bottom)) { neu() }
    }
}

/** Die Treffer der Seite als Bände auf einem Brett; der gewählte steht ein Stück heraus und glüht. */
@Composable
internal fun GrauenRegal(liste: List<RezeptKarte>, gewaehlt: String, onWahl: (String) -> Unit) {
    if (!FoodyTheme.stil.inszeniert) {
        GrauenListe(liste, gewaehlt, onWahl)
        return
    }
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

/** Ruhig wie im Entwurf: die Treffer als Zeilen mit Name und kcal, die gewählte mit einem Hauch Akzent. */
@Composable
private fun GrauenListe(liste: List<RezeptKarte>, gewaehlt: String, onWahl: (String) -> Unit) {
    val f = FoodyTheme.farben
    val akzent = FoodyTheme.stil.zier
    Column(Modifier.fillMaxWidth().drawBehind { drawLine(f.lichtkante, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }) {
        liste.forEach { r ->
            val an = r.id == gewaehlt
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).background(if (an) akzent.copy(alpha = 0.12f) else Color.Transparent)
                    .drawBehind { drawLine(f.lichtkante, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .rezeptWahl(r, an) { onWahl(r.id) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(r.name, Modifier.weight(1f), style = Grauen.name(17f), color = f.text)
                r.kcalJePortion?.let { Text("$it kcal", style = Grauen.maschine(13f), color = f.nebentext) }
            }
        }
    }
}

/** Die aufgeschlagene Seite des gewählten Bandes; „Aufschlagen“ darunter im Dunkel. */
@Composable
internal fun GrauenSeite(r: RezeptKarte, onOeffnen: (String) -> Unit) {
    val glut = FoodyTheme.stil.zier
    val inszeniert = FoodyTheme.stil.inszeniert
    val form = RoundedCornerShape(2.dp)
    val blatt = if (inszeniert) {
        Modifier.shadow(18.dp, form, ambientColor = glut, spotColor = glut).background(PERGAMENT, form)
    } else {
        Modifier.background(Color.White.copy(alpha = 0.4f), form).border(1.dp, FoodyTheme.farben.lichtkante, form)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().then(blatt)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val band = if (inszeniert) grauenBand(r) else r.tags.firstOrNull()?.uppercase(java.util.Locale.GERMAN)
            band?.let { Text(it, style = Grauen.titel(12f, 0.14f), color = TINTE_NEBEN) }
            Text(r.name, style = Grauen.name(22f), color = TINTE)
            val werte = listOfNotNull(zeitUndKcal(r)?.let { if (r.kcalJePortion != null) "$it je Portion" else it }, if (r.favorit) "♦ Favorit" else null)
            if (werte.isNotEmpty()) Text(werte.joinToString(" · "), style = Grauen.text(15f), color = TINTE_NEBEN)
            zutatenSatz(r)?.let { Text(it, style = Grauen.text(16f, kursiv = true), color = TINTE) }
        }
        GrauenKnopf(szenenWort("Aufschlagen", "Öffnen"), { onOeffnen(r.id) }, Modifier.fillMaxWidth(), beschreibung = "Öffnen: ${r.name}")
    }
}

/** „BAND · OFENGERICHT“ bzw. „BAND · OHNE ORDNUNG“: der erste Tag als Titel des Bandes. */
internal fun grauenBand(r: RezeptKarte): String = "Band · ${r.tags.firstOrNull() ?: "ohne Ordnung"}".uppercase(java.util.Locale.GERMAN)

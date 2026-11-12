package de.foody.feature.einkauf.stil

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.data.einkauf.EinkaufsAnsicht
import de.foody.core.data.einkauf.EinkaufsArtikel
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.animationenErlaubt
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.grauen.GrauenKnopf
import java.time.temporal.IsoFields
import kotlin.math.min

/**
 * „Einkauf“ im Kosmischen Grauen (U) in Immersiv (Entwurf `U-Einkauf`): „Vor dem Aufbruch zu besorgen“ über der
 * „Expeditionsliste“, rechts „9 / 12“ und „noch 3 offen“, daneben „Archiv“ (frühere Listen). Die Posten stehen
 * nummeriert in Schreibmaschine auf einem leicht schiefen Pergament, nach Abteilungen („Aus der Erde“); gekaufte
 * tragen den roten Stempel „Besorgt“. Unten flüstert die Liste, wie viel noch fehlt. TalkBack hört die normalen Wörter.
 */
internal fun LazyGridScope.grauenKopf(ansicht: EinkaufsAnsicht?, onListen: () -> Unit, menue: @Composable () -> Unit) {
    ganzeBreite("grauen-kopf") { Kopf(ansicht, onListen, menue) }
}

internal fun LazyGridScope.grauenPosten(ansicht: EinkaufsAnsicht?, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    val gruppen = ansicht.gruppiert()
    if (gruppen.isEmpty()) return
    ganzeBreite("grauen-pergament") { Pergament(ansicht, gruppen, onUmschalten, onDetails) }
}

/** Namen der Abteilungen für die Expedition; andere bleiben, wie sie sind. */
internal fun grauenAbteilung(name: String): String = when (name) {
    "Obst & Gemüse" -> "Aus der Erde"
    "Milch & Käse", "Kühlregal" -> "Aus der Kälte"
    "Getreideprodukte", "Trockenwaren" -> "Aus dem Gewölbe"
    "Fleisch & Fisch" -> "Aus der Tiefe"
    "Fertig- & Tiefkühlprodukte" -> "Aus dem ewigen Eis"
    "Konserven" -> "Versiegelt"
    "Eigene Artikel" -> "Persönliche Habe"
    else -> name
}

/** Was die Liste flüstert, je nach Zahl der offenen Posten. */
internal fun grauenFluestern(offen: Int): String = when (offen) {
    0 -> "Alles beisammen. Die Expedition kann beginnen."
    1 -> "Ein Posten fehlt noch. Ohne ihn kehrt niemand zurück."
    else -> "Noch $offen Posten. Man sagt, der Markt schließe vor Einbruch der Dunkelheit."
}

private val TINTE = Color(0xFF2A2418)
private val TINTE_BLASS = Color(0xFF4A3E2A)
private val MOOS = Color(0xFF24473A)
private val STEMPEL = Color(0xFF8E2A22)
private val PERGAMENT = listOf(Color(0xFFE4D6A8), Color(0xFFD6C48E), Color(0xFFC9B47A))
private val PERGAMENT_RAND = Color(0xFF8C7A4E)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Kopf(ansicht: EinkaufsAnsicht?, onListen: () -> Unit, menue: @Composable () -> Unit) {
    val f = FoodyTheme.farben
    val k = LocalDensity.current.fontScale
    val gesamt = ansicht.gesamt
    val erledigt = ansicht.erledigt
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.align(Alignment.Bottom).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Vor dem Aufbruch zu besorgen", style = Grauen.maschine(12.5f), color = f.nebentext)
            // Der Titel wächst mit der Systemschrift nur bis 130 %
            Text(
                "Expeditionsliste",
                Modifier.semantics {
                    heading()
                    contentDescription = "Einkauf"
                },
                style = Grauen.titel(32f * min(k, 1.3f) / k, 0.02f), color = f.text,
            )
        }
        Row(Modifier.align(Alignment.Bottom), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (gesamt > 0) {
                Column(
                    Modifier.padding(end = 4.dp).semantics(mergeDescendants = true) {
                        contentDescription = "$erledigt von $gesamt erledigt, ${gesamt - erledigt} offen"
                    },
                    horizontalAlignment = Alignment.End,
                ) {
                    Text("$erledigt / $gesamt", style = Grauen.maschine(14f), color = f.text, textAlign = TextAlign.End)
                    Text(
                        if (gesamt == erledigt) "vollständig" else "noch ${gesamt - erledigt} offen",
                        style = Grauen.maschine(12f), color = f.nebentext, textAlign = TextAlign.End,
                    )
                }
            }
            GrauenKnopf("Archiv", onListen, beschreibung = "Frühere Listen", umriss = true)
            menue()
        }
    }
}

@Composable
private fun Pergament(ansicht: EinkaufsAnsicht?, gruppen: List<PostenGruppe>, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    val glut = FoodyTheme.stil.zier
    val nummer = ansicht?.von?.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    var nr = 0
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).rotate(-0.6f)
            .drawBehind {
                // Schwaches Leuchten der Glut um das Blatt
                drawRect(Brush.radialGradient(listOf(glut.copy(alpha = 0.12f), Color.Transparent), center, size.maxDimension * 0.7f))
            }
            .background(Brush.verticalGradient(PERGAMENT))
            .border(1.dp, PERGAMENT_RAND)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics { }
                .drawBehind { drawLine(TINTE.copy(alpha = 0.35f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                .padding(bottom = 6.dp),
        ) {
            Text("EXPEDITIONSKÜCHE · AUSRÜSTUNG", Modifier.weight(1f), style = Grauen.maschine(12f), color = TINTE_BLASS)
            if (nummer != null) Text("Nr. $nummer", style = Grauen.maschine(12f), color = TINTE_BLASS)
        }
        gruppen.forEach { g ->
            Column {
                Text(
                    grauenAbteilung(g.name),
                    Modifier.padding(top = 4.dp, bottom = 2.dp).semantics {
                        heading()
                        contentDescription = g.name
                    },
                    style = Grauen.titel(15f, 0.06f), color = MOOS,
                )
                g.artikel.forEach { a ->
                    nr++
                    Zeile(a, nr, onUmschalten, onDetails)
                }
            }
        }
        Text(grauenFluestern(gesamtOffen(ansicht)), Modifier.padding(top = 6.dp), style = Grauen.text(14.5f, kursiv = true), color = TINTE_BLASS)
    }
}

private fun gesamtOffen(ansicht: EinkaufsAnsicht?): Int = ansicht.gesamt - ansicht.erledigt

@Composable
private fun Zeile(a: EinkaufsArtikel, nr: Int, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .drawBehind {
                    drawLine(
                        TINTE.copy(alpha = 0.3f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 2.dp.toPx())),
                    )
                }
                .posten(a, onUmschalten, onDetails).padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(nr.toString().padStart(2, '0'), Modifier.width(22.dp * LocalDensity.current.fontScale), style = Grauen.maschine(15f), color = TINTE_BLASS)
            Text(a.name, Modifier.weight(1f), style = Grauen.maschine(15f), color = if (a.gekauft) TINTE_BLASS else TINTE)
            a.menge?.let { Text(it, style = Grauen.maschine(13f), color = TINTE_BLASS) }
        }
        if (a.gekauft) Stempel(Modifier.align(Alignment.CenterEnd).padding(end = 64.dp))
    }
}

/** Roter Stempel „Besorgt“, schräg; drückt sich auf, wenn Animationen erlaubt sind. Für TalkBack stumm. */
@Composable
private fun Stempel(modifier: Modifier) {
    val k = LocalDensity.current.fontScale
    val animiert = animationenErlaubt()
    val groesse = remember { Animatable(if (animiert) 1.6f else 1f) }
    LaunchedEffect(Unit) { if (animiert) groesse.animateTo(1f, tween(350)) }
    Text(
        "BESORGT",
        modifier.clearAndSetSemantics { }.rotate(-14f).scale(groesse.value).alpha(0.85f)
            .border(2.dp, STEMPEL).padding(horizontal = 6.dp, vertical = 1.dp),
        style = Grauen.titel(13f / k, 0.12f), color = STEMPEL,
    )
}

package de.foody.feature.einkauf.stil

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.foody.core.data.einkauf.EinkaufsArtikel
import de.foody.core.designsystem.animationenErlaubt
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.core.designsystem.stil.svgPfad
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Die Posten des Händlers in Immersiv als Papierrolle: oben und unten Walzen mit Holzknäufen, dazwischen Pergament mit
 * Tinte. Beim ersten Zeigen rollt sie sich nach unten aus (einmal je Bildschirm, ohne Animationen sofort offen). Auf dem
 * Handy eine Spalte, ab 560 dp zwei. Antippen hakt ab (und wieder auf), Langdruck öffnet die Details.
 */
@Composable
internal fun HaendlerRolle(posten: List<EinkaufsArtikel>, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    val bewegt = animationenErlaubt()
    var ausgerollt by rememberSaveable { mutableStateOf(false) }
    val anteil = remember { Animatable(if (ausgerollt || !bewegt) 1f else 0f) }
    LaunchedEffect(Unit) {
        anteil.animateTo(1f, tween(ROLLEN_MS, easing = FastOutSlowInEasing))
        ausgerollt = true
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Walze()
        BoxWithConstraints(
            Modifier.fillMaxWidth().clipToBounds().layout { messbar, grenzen ->
                val p = messbar.measure(grenzen)
                val h = (p.height * anteil.value).roundToInt()
                layout(p.width, h) { p.place(0, 0) }
            },
        ) {
            val spalten = if (maxWidth >= 560.dp) 2 else 1
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp).drawBehind { pergament() }.padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text(
                    "VERZEICHNIS DER WAREN", Modifier.align(Alignment.CenterHorizontally).semantics { contentDescription = "Einkaufsliste" },
                    style = Sage.titel(11f, 0.2f), color = TINTE_HELL,
                )
                Schnoerkel()
                posten.chunked(spalten).forEach { reihe ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        reihe.forEach { a ->
                            Eintrag(a, Modifier.weight(1f), onKlick = { onUmschalten(a.id, !a.gekauft) }, onLang = { onDetails(a.id) })
                        }
                        if (reihe.size < spalten) Spacer(Modifier.weight(1f))
                    }
                }
                Schnoerkel()
            }
        }
        Walze()
    }
}

private const val ROLLEN_MS = 900
private val TINTE = Color(0xFF2B2116)

/** Hellere Tinte für Nebenzeilen; auf dem Pergament noch über 4,5:1. */
private val TINTE_HELL = Color(0xFF57432C)

private fun DrawScope.pergament() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFCDB385), Color(0xFFDCC79D), Color(0xFFD4BC8E), Color(0xFFC9AE7E))))
    drawRect(Brush.horizontalGradient(0f to Color(0x55603F1C), 0.08f to Color.Transparent, 0.92f to Color.Transparent, 1f to Color(0x55603F1C)))
    // Schatten der Walzen auf dem Pergament
    drawRect(Brush.verticalGradient(listOf(Color(0x66402A12), Color.Transparent), 0f, 18.dp.toPx()))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x55402A12)), size.height - 14.dp.toPx(), size.height))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Eintrag(a: EinkaufsArtikel, modifier: Modifier, onKlick: () -> Unit, onLang: () -> Unit) {
    val zeile = listOfNotNull(a.menge, a.abteilung).joinToString(" · ").uppercase(Locale.GERMAN)
    Row(
        modifier.heightIn(min = 48.dp)
            .combinedClickable(role = Role.Checkbox, onClick = onKlick, onLongClick = onLang, onLongClickLabel = "Details")
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(a.name, a.menge, a.notiz).joinToString(", ")
                stateDescription = if (a.gekauft) "gekauft" else "auf der Liste"
                onLongClick("Details") { onLang(); true }
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(11.dp).clearAndSetSemantics { }) {
            val raute = svgPfad("M1 0L2 1L1 2L0 1Z")
            scale(size.width / 2f, Offset.Zero) {
                if (a.gekauft) drawPath(raute, TINTE) else drawPath(raute, TINTE, style = Stroke(0.22f))
            }
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                a.name, style = Sage.text(18f).copy(textDecoration = if (a.gekauft) TextDecoration.LineThrough else null),
                color = if (a.gekauft) TINTE_HELL else TINTE, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (zeile.isNotEmpty()) Text(zeile, style = Sage.titel(9.5f, 0.12f), color = TINTE_HELL, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Trennlinie in Tinte mit Raute in der Mitte. */
@Composable
private fun Schnoerkel() {
    Canvas(Modifier.fillMaxWidth().height(14.dp).clearAndSetSemantics { }) {
        val m = size.width / 2
        val y = size.height / 2
        val l = minOf(120.dp.toPx(), m - 8.dp.toPx())
        val r = 5.dp.toPx()
        drawLine(TINTE_HELL, Offset(m - l, y), Offset(m - r - 4.dp.toPx(), y), 1.dp.toPx())
        drawLine(TINTE_HELL, Offset(m + r + 4.dp.toPx(), y), Offset(m + l, y), 1.dp.toPx())
        drawPath(svgPfad("M0 -1L1 0L0 1L-1 0Z").also { it.transform(Matrix().apply { translate(m, y); scale(r, r) }) }, TINTE_HELL)
    }
}

/** Eine Walze der Rolle: runde Schattierung, an den Enden Knäufe aus dunklem Holz mit Goldring. */
@Composable
private fun Walze() {
    Canvas(Modifier.fillMaxWidth().height(20.dp).clearAndSetSemantics { }) {
        val h = size.height
        val rand = 6.dp.toPx()
        val holz = Brush.verticalGradient(listOf(Color(0xFF5A4026), Color(0xFF2A1C10)))
        val knauf = rand + 3.dp.toPx()
        drawRoundRect(holz, Offset(0f, h * 0.1f), Size(knauf, h * 0.8f), CornerRadius(3.dp.toPx()))
        drawRoundRect(holz, Offset(size.width - knauf, h * 0.1f), Size(knauf, h * 0.8f), CornerRadius(3.dp.toPx()))
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF8C7450), Color(0xFFE6D3A8), Color(0xFFC2A675), Color(0xFF6E5838))),
            Offset(rand, 0f), Size(size.width - 2 * rand, h), CornerRadius(h / 2),
        )
        val ring = 1.5.dp.toPx()
        drawRect(GOLD_RING, Offset(rand - ring / 2, h * 0.05f), Size(ring, h * 0.9f))
        drawRect(GOLD_RING, Offset(size.width - rand - ring / 2, h * 0.05f), Size(ring, h * 0.9f))
        drawLine(Color(0x33000000), Offset(rand + 10f, h * 0.7f), Offset(size.width - rand - 10f, h * 0.7f), 1f)
    }
}

private val GOLD_RING = Color(0xFFC9A65A)

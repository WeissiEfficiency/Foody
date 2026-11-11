package de.foody.feature.einkauf.stil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.foody.core.data.einkauf.EinkaufsAnsicht
import de.foody.core.data.einkauf.EinkaufsArtikel
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.core.designsystem.stil.sage.SageKopf
import de.foody.core.designsystem.stil.sage.SageNebenknopf
import de.foody.core.designsystem.stil.sage.SageRaute
import java.util.Locale

/**
 * „Einkauf“ der Düsteren Sage in Immersiv (Entwurf `Q-Einkauf`): Kopf „Der Händler“ mit erledigt/gesamt, die Szene
 * im Laden mit dem Spruch des Händlers und die Posten als Felder mit Raute. Antippen hakt ab (und wieder auf),
 * Langdruck öffnet die Details – wie die Kacheln der normalen Ansicht. Eingabe, „Zuletzt“ und Katalog bleiben.
 */
internal fun LazyGridScope.haendlerKopf(ansicht: EinkaufsAnsicht?, onListen: () -> Unit, menue: @Composable () -> Unit) {
    item(key = "haendler-kopf", span = { GridItemSpan(maxLineSpan) }) {
        val gesamt = ansicht?.alle?.size ?: 0
        val offen = ansicht?.anzahlOffen ?: 0
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SageKopf(
                unter = (ansicht?.name ?: "Einkaufsliste").uppercase(Locale.GERMAN),
                titel = "DER HÄNDLER",
                beschreibung = "Einkauf",
            ) {
                if (gesamt > 0) {
                    Text(
                        "${gesamt - offen} / $gesamt", Modifier.padding(bottom = 10.dp),
                        style = Sage.titel(15f, 0.04f), color = FoodyTheme.farben.text,
                    )
                }
                SageNebenknopf("LISTEN", "Frühere Listen", onListen, akzent = true)
                menue()
            }
            Laden(offen)
        }
    }
}

/**
 * Die offenen Posten in Laufreihenfolge, danach die schon gekauften (durchgestrichen); je Reihe zwei wie im Entwurf,
 * unabhängig von den Spalten des Katalogs darunter.
 */
internal fun LazyGridScope.haendlerPosten(ansicht: EinkaufsAnsicht?, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit) {
    val offen = ansicht?.offen.orEmpty().flatMap { it.artikel }
    val gekauft = ansicht?.alle.orEmpty().filter { it.gekauft }
    items((offen + gekauft).chunked(2), key = { "posten-${it.first().id}" }, span = { GridItemSpan(maxLineSpan) }) { reihe ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            reihe.forEach { a -> Posten(a, Modifier.weight(1f), onKlick = { onUmschalten(a.id, !a.gekauft) }, onLang = { onDetails(a.id) }) }
            if (reihe.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Posten(a: EinkaufsArtikel, modifier: Modifier, onKlick: () -> Unit, onLang: () -> Unit) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    val zeile = listOfNotNull(a.menge, a.abteilung.uppercase(Locale.GERMAN)).joinToString(" · ")
    Row(
        modifier.heightIn(min = 50.dp)
            .border(1.dp, if (a.gekauft) f.balkenSpur else f.lichtkante)
            .then(if (a.gekauft) Modifier else Modifier.background(Brush.verticalGradient(listOf(glut.copy(alpha = 0.08f), Color.Transparent))))
            .combinedClickable(role = Role.Checkbox, onClick = onKlick, onLongClick = onLang, onLongClickLabel = "Details")
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(a.name, a.menge, a.notiz).joinToString(", ")
                stateDescription = if (a.gekauft) "gekauft" else "auf der Liste"
                onLongClick("Details") { onLang(); true }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { SageRaute(10.dp, gefuellt = a.gekauft) }
        Column(Modifier.weight(1f)) {
            Text(
                a.name, style = Sage.text(16.5f).copy(textDecoration = if (a.gekauft) TextDecoration.LineThrough else null),
                color = if (a.gekauft) f.nebentext else f.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (zeile.isNotEmpty()) Text(zeile, style = Sage.titel(10.5f, 0.1f), color = f.nebentext, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Was der Händler sagt, je nach Zahl der offenen Posten. */
internal fun haendlerSpruch(offen: Int): String = when (offen) {
    0 -> "Ihr habt alles. Geht in Frieden."
    1 -> "Ein Ding fehlt Euch noch."
    else -> "${ZAHLWORTE.getOrNull(offen) ?: offen.toString()} Dinge fehlen Euch noch."
}

private val ZAHLWORTE = listOf("Keine", "Ein", "Zwei", "Drei", "Vier", "Fünf", "Sechs", "Sieben", "Acht", "Neun", "Zehn", "Elf", "Zwölf")

/**
 * Der Laden als Zeichnung (Vordach, Pfosten, Theke mit Waren, der Händler im Torbogen mit leuchtenden Augen, Laterne),
 * daneben der Spruch. Höhe wächst mit der Schrift; die Zeichnung ist für TalkBack stumm, der Spruch nicht.
 */
@Composable
private fun Laden(offen: Int) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    val pfade = remember { LadenPfade() }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 140.dp).border(1.dp, f.balkenSpur).clipToBounds()
            .background(Color(0xFF110F0C)),
    ) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics { }) {
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFFF59E0B).copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.22f, size.height * 0.4f), radius = size.width * 0.55f,
                ),
            )
            val s = size.height / 140f
            scale(s, Offset.Zero) {
                drawPath(pfade.dach, Color(0xFF1E1A14))
                drawPath(pfade.dach, Color(0xFF3A3428), style = Stroke(1f))
                drawLine(Color(0xFF2A251C), Offset(14f, 40f), Offset(14f, 140f), 6f)
                drawLine(Color(0xFF2A251C), Offset(226f, 40f), Offset(226f, 140f), 6f)
                drawRect(Color(0xFF1A1611), Offset(0f, 108f), Size(240f, 32f))
                drawRect(Color(0xFF2A2218), Offset(30f, 92f), Size(22f, 16f))
                drawRoundRect(Color(0xFF2A2218), Offset(58f, 96f), Size(16f, 12f), CornerRadius(6f))
                drawRect(Color(0xFF2A2218), Offset(180f, 90f), Size(26f, 18f))
                drawPath(pfade.bogen, Color(0xFF0A0908))
                drawPath(pfade.kapuze, Color(0xFF161310))
                drawCircle(glut, 1.8f, Offset(139f, 78f))
                drawCircle(glut, 1.8f, Offset(153f, 78f))
                drawLine(Color(0xFF3A3428), Offset(76f, 40f), Offset(76f, 54f), 1f)
                drawPath(pfade.laterne, Color(0xFFF59E0B).copy(alpha = 0.85f))
                drawCircle(Color(0xFFF59E0B).copy(alpha = 0.15f), 14f, Offset(76f, 62f))
            }
        }
        Row(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            Text(
                "„Was führt Euch her? ${haendlerSpruch(offen)}“",
                Modifier.width(150.dp).padding(top = 18.dp, end = 12.dp, bottom = 12.dp),
                style = Sage.text(15.5f, kursiv = true),
                color = f.text,
            )
        }
    }
}

private class LadenPfade {
    private fun pfad(d: String) = PathParser().parsePathString(d).toPath()
    val dach = pfad("M0 40L60 14h120l60 26z")
    val bogen = pfad("M120 140v-50c0-24 12-40 26-40s26 16 26 40v50z")
    val kapuze = pfad("M126 70c4-14 12-20 20-20s16 6 20 20c-8-6-32-6-40 0z")
    val laterne = pfad("M70 54h12l-2 16h-8z")
}

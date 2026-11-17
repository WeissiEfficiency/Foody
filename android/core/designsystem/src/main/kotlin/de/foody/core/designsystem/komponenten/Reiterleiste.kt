package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.stil.sage.Sage
import java.util.Locale

/** Schrift der Reiter je Stil (wie in den Entwürfen); Milchglas in der Schrift der Knöpfe. */
@Composable
private fun reiterSchrift(): TextStyle = when (FoodyTheme.stil.stil) {
    FoodyStil.SAGE -> Sage.titel(12.5f, 0.12f)
    FoodyStil.ANIME -> Anime.text(14f, FontWeight.ExtraBold)
    FoodyStil.EDO -> Edo.text(14.5f, FontWeight.Medium)
    FoodyStil.BAYRISCH -> Bayrisch.text(15f, FontWeight.SemiBold)
    FoodyStil.GRAUEN -> Grauen.text(16f)
    FoodyStil.KREUZER -> Kreuzer.titel(13f, FontWeight.Bold, 0.08f)
    FoodyStil.MILCHGLAS -> MaterialTheme.typography.labelLarge
}

/**
 * Reiter einer Seite (Planen, Listen, Eintragen) in der Art des Stils: Anime und Bayrisch als Pillen mit Rand (der
 * gewählte gefüllt), die übrigen als Wörter mit einer Linie darunter und einem Strich im Akzent unter dem gewählten.
 * TalkBack liest je Reiter [beschreibungen] (das normale Wort) und „ausgewählt“.
 */
@Composable
fun Reiterleiste(
    woerter: List<String>,
    gewaehlt: Int,
    onWahl: (Int) -> Unit,
    modifier: Modifier = Modifier,
    beschreibungen: List<String> = woerter,
) {
    val f = FoodyTheme.farben
    val s = FoodyTheme.stil
    val akzent = if (s.stil == FoodyStil.MILCHGLAS) f.primaer else s.zier
    val pillen = s.stil == FoodyStil.ANIME || s.stil == FoodyStil.BAYRISCH
    val schrift = reiterSchrift()
    val versal = s.stil == FoodyStil.KREUZER
    Row(
        modifier.fillMaxWidth().selectableGroup().then(
            if (pillen) Modifier else Modifier.drawBehind { drawLine(f.balkenSpur, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) },
        ),
        horizontalArrangement = Arrangement.spacedBy(if (pillen) 6.dp else 0.dp),
    ) {
        woerter.forEachIndexed { i, wort ->
            val an = i == gewaehlt
            val form = FoodyTheme.formen.knopf
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .then(
                        when {
                            pillen && an -> Modifier.background(akzent, form).border(2.dp, f.text, form)
                            pillen -> Modifier.border(2.dp, f.text, form)
                            an -> Modifier.drawBehind {
                                drawLine(akzent, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx())
                            }
                            else -> Modifier
                        },
                    )
                    .selectable(an, role = Role.Tab) { onWahl(i) }
                    .semantics { contentDescription = beschreibungen.getOrElse(i) { wort } }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (versal) wort.uppercase(Locale.GERMAN) else wort,
                    Modifier.clearAndSetSemantics {},
                    style = schrift,
                    color = if (an || pillen) f.text else f.nebentext,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

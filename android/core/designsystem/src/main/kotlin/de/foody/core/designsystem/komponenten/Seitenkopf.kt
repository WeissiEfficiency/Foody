package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.TitelArt
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.edo.edoShu
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.core.designsystem.stil.stilTitel
import de.foody.core.designsystem.token.FoodyAbstaende

/**
 * Kopf einer Seite wie im Entwurf: optional ein runder Zurück-Knopf, darüber die [ueberzeile] in Versalien („SAMSTAG,
 * 31. OKTOBER“, „6 REZEPTE“), darunter der Titel in Fraunces; rechts runde [aktionen].
 */
@Composable
fun Seitenkopf(
    titel: String,
    modifier: Modifier = Modifier,
    ueberzeile: String? = null,
    onZurueck: (() -> Unit)? = null,
    /** Was TalkBack statt des Titels liest, wenn der Titel ein Stilwort ist („Das Lager“ → „Einstellungen“). */
    beschreibung: String? = null,
    aktionen: @Composable RowScope.() -> Unit = {},
) {
    // Zurück, Titel und Aktionen in einer Zeile. Passt das längste Wort des Titels neben die Aktionen nicht mehr
    // (lange Stilwörter, große Schrift), rücken die Aktionen in eine eigene Zeile darunter, statt das Wort zu trennen.
    Layout(
        contents = listOf(
            { if (onZurueck != null) RunderKnopf(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onZurueck) },
            {
                Column {
                    if (ueberzeile != null) Augenbraue(ueberzeile)
                    // Düstere Sage, Immersiv: Titel in Cinzel mit Laufweite im Akzent wie die Köpfe der Szenen
                    // Stile R–V, Immersiv: Titelschrift des Stils ([stilTitel])
                    val sage = FoodyTheme.stil.immersiv(FoodyStil.SAGE)
                    val eigen = stilTitel(if (onZurueck != null) TitelArt.SEITE_ZURUECK else TitelArt.SEITE)
                    Text(
                        titel,
                        Modifier.ueberschrift().then(if (beschreibung != null) Modifier.semantics { contentDescription = beschreibung } else Modifier),
                        style = when {
                            sage -> if (onZurueck != null) Sage.titel(20f, 0.06f) else Sage.titel(28f, 0.1f)
                            eigen != null -> eigen
                            onZurueck != null -> MaterialTheme.typography.headlineMedium
                            else -> MaterialTheme.typography.headlineLarge
                        },
                        color = if (sage) FoodyTheme.farben.primaer else FoodyTheme.farben.text,
                    )
                }
            },
            { Row(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalAlignment = Alignment.CenterVertically, content = aktionen) },
        ),
        modifier,
    ) { (zurueckTeil, titelTeil, aktionenTeil), c ->
        val abstand = FoodyAbstaende.s3.roundToPx()
        val frei = Constraints(maxWidth = c.maxWidth, maxHeight = c.maxHeight)
        val zurueck = zurueckTeil.firstOrNull()?.measure(frei)?.takeIf { it.width > 0 }
        val links = zurueck?.let { it.width + abstand } ?: 0
        val aktionen = aktionenTeil.first().measure(frei.copy(maxWidth = (c.maxWidth - links).coerceAtLeast(0)))
        val rechts = if (aktionen.width > 0) aktionen.width + abstand else 0
        val titelMessung = titelTeil.first()
        val breite = if (c.hasBoundedWidth) c.maxWidth else links + titelMessung.maxIntrinsicWidth(c.maxHeight) + rechts
        val rest = (breite - links).coerceAtLeast(0)
        val eineZeile = rechts == 0 || rest - rechts >= titelMessung.minIntrinsicWidth(c.maxHeight)
        val titel = titelMessung.measure(Constraints(maxWidth = (if (eineZeile) rest - rechts else rest).coerceAtLeast(0)))
        val oben = maxOf(zurueck?.height ?: 0, titel.height, if (eineZeile) aktionen.height else 0)
        val unten = if (eineZeile || aktionen.height == 0) 0 else FoodyAbstaende.s2.roundToPx() + aktionen.height
        layout(breite, (oben + unten).coerceAtLeast(c.minHeight)) {
            zurueck?.place(0, (oben - zurueck.height) / 2)
            titel.place(links, (oben - titel.height) / 2)
            if (eineZeile) {
                aktionen.place(breite - aktionen.width, (oben - aktionen.height) / 2)
            } else {
                aktionen.place(breite - aktionen.width, oben + FoodyAbstaende.s2.roundToPx())
            }
        }
    }
}

/**
 * Titel eines Blatts (Überschrift für TalkBack). In der Düsteren Sage (Immersiv) in Cinzel mit Laufweite im Akzent;
 * [beschreibung] liest TalkBack statt eines Stilworts. [zeichen] steht im Edo-Stil (Immersiv) als schräger
 * Zinnoberstempel vor dem Titel wie im Entwurf („記“), TalkBack überspringt ihn.
 */
@Composable
fun BlattTitel(titel: String, modifier: Modifier = Modifier, beschreibung: String? = null, zeichen: String? = null) {
    val sage = FoodyTheme.stil.immersiv(FoodyStil.SAGE)
    val text = @Composable { m: Modifier ->
        Text(
            titel,
            m.ueberschrift().then(if (beschreibung != null) Modifier.semantics { contentDescription = beschreibung } else Modifier),
            style = if (sage) Sage.titel(20f, 0.1f) else stilTitel(TitelArt.BLATT) ?: MaterialTheme.typography.headlineSmall,
            color = if (sage) FoodyTheme.farben.primaer else FoodyTheme.farben.text,
        )
    }
    if (zeichen == null || !FoodyTheme.stil.immersiv(FoodyStil.EDO)) {
        text(modifier)
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).rotate(-4f).background(edoShu(), Edo.form).clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(zeichen, style = Edo.titel(18f), color = Color.White, maxLines = 1)
        }
        text(Modifier.weight(1f))
    }
}

package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.token.FoodyFormen
import de.foody.core.designsystem.token.GlasStufe
import de.foody.core.designsystem.token.herzFarbe

/** Testkennung des Bild-Platzhalters (Rezept ohne Foto). */
const val PLATZHALTER_TAG = "rezept-platzhalter"

/** Testkennungen: Symbol im Platzhalter, Glas-Leiste der großen Karte. */
const val PLATZHALTER_SYMBOL_TAG = "rezept-platzhalter-symbol"
const val GLASLEISTE_TAG = "rezept-glasleiste"

/** Aktion in der großen Karte, z. B. „Kochen“. */
data class KartenAktion(val text: String, val onKlick: () -> Unit)

/**
 * Große Rezeptkarte („Heute“): Bild randlos, darüber unten eine dichte Glas-Leiste mit Augenbraue, Titel, Metazeile
 * und optionaler Aktion. TalkBack liest den vollen Titel, auch wenn er sichtbar gekürzt ist.
 */
@Composable
fun RezeptkarteGross(
    titel: String,
    meta: String,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    augenbraue: String? = null,
    bild: (@Composable () -> Unit)? = null,
    aktion: KartenAktion? = null,
) {
    val gross = grosseSchrift()
    val beschreibung = listOfNotNull(augenbraue, titel, meta).joinToString(", ")
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 232.dp)
            .clip(FoodyFormen.gross)
            .clickable(onClick = onKlick)
            .semantics { contentDescription = beschreibung },
    ) {
        Box(Modifier.matchParentSize()) {
            // Symbol oben, damit es nicht hinter der Glas-Leiste verschwindet
            // Bei großer Schrift deckt die Leiste die ganze Karte – dann kein Symbol hinter dem Text
            bild?.invoke() ?: RezeptPlatzhalter(Modifier.fillMaxSize(), symbolOben = true, mitSymbol = !gross)
        }
        GlasFlaeche(
            GlasStufe.BILD,
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp).testTag(GLASLEISTE_TAG),
            form = FoodyFormen.mittel,
        ) {
            val text: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (augenbraue != null) Augenbraue(augenbraue, farbe = FoodyTheme.farben.primaer)
                    Text(
                        titel,
                        style = MaterialTheme.typography.titleLarge,
                        color = FoodyTheme.farben.text,
                        maxLines = if (gross) 4 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(meta, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
                }
            }
            if (gross || aktion == null) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    text(Modifier.fillMaxWidth())
                    if (aktion != null) FoodyKnopf(aktion.text, onKlick = aktion.onKlick)
                }
            } else {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    text(Modifier.weight(1f))
                    FoodyKnopf(aktion.text, onKlick = aktion.onKlick)
                }
            }
        }
    }
}

/** Rasterkarte (Rezeptliste): quadratisches Bild mit Abzeichen, Titel darunter. */
@Composable
fun RezeptkarteRaster(
    titel: String,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    bild: (@Composable () -> Unit)? = null,
    favorit: Boolean = false,
    zeit: String? = null,
    kcal: String? = null,
) {
    val beschreibung = listOfNotNull(titel, "Favorit".takeIf { favorit }, zeit, kcal).joinToString(", ")
    Column(
        modifier
            .clip(FoodyFormen.mittel)
            .clickable(onClick = onKlick)
            .semantics { contentDescription = beschreibung },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(FoodyFormen.mittel)) {
            bild?.invoke() ?: RezeptPlatzhalter(Modifier.fillMaxSize())
            if (favorit) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Favorite, contentDescription = null, tint = herzFarbe(FoodyTheme.farbwelt), modifier = Modifier.size(18.dp)) }
            }
            if (zeit != null || kcal != null) {
                // Umbrechende Zeile: bei großer Schrift rutscht kcal unter die Zeit statt sie zu überdecken
                FlowRow(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (zeit != null) Abzeichen(zeit, Modifier)
                    if (kcal != null) Abzeichen(kcal, Modifier)
                }
            }
        }
        Text(
            titel,
            style = MaterialTheme.typography.titleSmall,
            color = FoodyTheme.farben.text,
            maxLines = if (grosseSchrift()) 4 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 4.dp),
        )
    }
}

/** Kleines Abzeichen auf dem Bild; dunkler Grund (α 0,6), damit weiße Schrift auf jedem Foto 4,5:1 hält. */
@Composable
private fun Abzeichen(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Rezept ohne Foto: Verlauf in der Farbwelt mit Besteck-Symbol – mittig oder ([symbolOben]) im oberen Teil. */
@Composable
fun RezeptPlatzhalter(modifier: Modifier = Modifier, symbolOben: Boolean = false, mitSymbol: Boolean = true) {
    Box(
        modifier
            .testTag(PLATZHALTER_TAG)
            .background(Brush.linearGradient(FoodyTheme.werte.platzhalter)),
        contentAlignment = if (symbolOben) Alignment.TopCenter else Alignment.Center,
    ) {
        if (mitSymbol) Icon(
            Icons.Outlined.Restaurant,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(top = if (symbolOben) 20.dp else 0.dp)
                .size(if (symbolOben) 40.dp else 56.dp)
                .testTag(PLATZHALTER_SYMBOL_TAG),
        )
    }
}

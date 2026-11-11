package de.foody.feature.einstellungen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilAkzent
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.stilWerte
import de.foody.core.designsystem.token.FoodyAbstaende

/**
 * Wahl des Stils: je ein Muster aus Grund, Karte und Akzent des Stils (Milchglas in der aktuellen Farbwelt), darunter
 * der Name. Verhält sich wie Optionsfelder; der gewählte Stil trägt einen Ring und einen Haken. Raster wie bei der
 * Farbwelt, Spalten nach Breite und Schriftgröße.
 */
@Composable
internal fun StilWahl(gewaehlt: FoodyStil, variante: StilVariante, onWahl: (FoodyStil) -> Unit) {
    Raster(FoodyStil.entries, zelle = 96.dp) { s, modifier -> StilMuster(s, variante, s == gewaehlt, modifier) { onWahl(s) } }
}

/** Die vier Akzente des [stil]s als runde Farbmuster. */
@Composable
internal fun AkzentWahl(stil: FoodyStil, gewaehlt: StilAkzent, onWahl: (StilAkzent) -> Unit) {
    Raster(stil.akzente, zelle = 72.dp) { a, modifier -> AkzentMuster(a, a == gewaehlt, modifier) { onWahl(a) } }
}

@Composable
private fun <T> Raster(eintraege: List<T>, zelle: Dp, inhalt: @Composable (T, Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val breite = maxOf(zelle, zelle * LocalDensity.current.fontScale * 0.85f)
        val abstand = FoodyAbstaende.s2
        val spalten = ((maxWidth + abstand) / (breite + abstand)).toInt().coerceIn(1, 4)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
            eintraege.chunked(spalten).forEach { reihe ->
                Row(horizontalArrangement = Arrangement.spacedBy(abstand)) {
                    reihe.forEach { inhalt(it, Modifier.weight(1f)) }
                    repeat(spalten - reihe.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun StilMuster(stil: FoodyStil, variante: StilVariante, gewaehlt: Boolean, modifier: Modifier, onKlick: () -> Unit) {
    val dunkel = FoodyTheme.dunkel
    val farbwelt = FoodyTheme.farbwelt
    val werte = remember(stil, variante, dunkel, farbwelt) { stilWerte(stil, variante, dunkel = dunkel, farbwelt = farbwelt) }
    val f = werte.farben
    val ring = if (gewaehlt) FoodyTheme.farben.text else FoodyTheme.farben.lichtkante
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = gewaehlt, role = Role.RadioButton, onClick = onKlick)
            .padding(vertical = FoodyAbstaende.s1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val form = werte.formen.mittel
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .border(if (gewaehlt) 2.dp else 1.dp, ring, form)
                .padding(if (gewaehlt) FoodyAbstaende.s1 else 1.dp)
                .clip(form)
                .background(f.grund),
        ) {
            // Karte mit Titelzeile und Akzentknopf: so sieht der Stil im Kleinen aus
            Column(
                Modifier
                    .padding(FoodyAbstaende.s2)
                    .fillMaxWidth()
                    .clip(werte.formen.klein)
                    .background(werte.werte.glas)
                    .border(werte.kante, f.lichtkante, werte.formen.klein)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.fillMaxWidth(0.7f).height(5.dp).background(f.text, CircleShape))
                Box(Modifier.fillMaxWidth(0.45f).height(4.dp).background(f.nebentext, CircleShape))
                Box(Modifier.size(width = 26.dp, height = 9.dp).background(f.primaer, werte.formen.knopf))
            }
            if (gewaehlt) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(f.primaer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, contentDescription = null, tint = f.aufPrimaer, modifier = Modifier.size(12.dp)) }
            }
        }
        Text(
            stil.anzeigename,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (gewaehlt) FontWeight.Bold else FontWeight.Medium,
            color = if (gewaehlt) FoodyTheme.farben.text else FoodyTheme.farben.nebentext,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun AkzentMuster(akzent: StilAkzent, gewaehlt: Boolean, modifier: Modifier, onKlick: () -> Unit) {
    val ring = if (gewaehlt) FoodyTheme.farben.text else FoodyTheme.farben.lichtkante
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = gewaehlt, role = Role.RadioButton, onClick = onKlick)
            .padding(vertical = FoodyAbstaende.s1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .border(if (gewaehlt) 2.dp else 1.dp, ring, CircleShape)
                .padding(if (gewaehlt) FoodyAbstaende.s1 else 1.dp)
                .clip(CircleShape)
                .background(akzent.farbe),
            contentAlignment = Alignment.Center,
        ) {
            if (gewaehlt) {
                // Haken in Schwarz oder Weiß, je nachdem, was auf dem Akzent besser steht
                val haken = if (akzent.farbe.luminanzHell()) Color.Black else Color.White
                Icon(Icons.Filled.Check, contentDescription = null, tint = haken, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            akzent.anzeigename,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (gewaehlt) FontWeight.Bold else FontWeight.Medium,
            color = if (gewaehlt) FoodyTheme.farben.text else FoodyTheme.farben.nebentext,
            textAlign = TextAlign.Center,
            softWrap = false,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun Color.luminanzHell(): Boolean = 0.2126f * red + 0.7152f * green + 0.0722f * blue > 0.5f

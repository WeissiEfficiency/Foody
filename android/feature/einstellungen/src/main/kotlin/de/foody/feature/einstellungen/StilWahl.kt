package de.foody.feature.einstellungen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilAkzent
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.stilWerte
import de.foody.core.designsystem.token.FoodyAbstaende

/**
 * Wahl des Stils: je ein Muster aus Grund, Karte und Akzent des Stils (Milchglas in der aktuellen Farbwelt), darunter
 * der Name. Verhält sich wie Optionsfelder; der gewählte Stil trägt einen Ring und einen Haken. So viele Spalten, wie
 * in [breite] passen: Die Kacheln sind in jedem Stil gleich groß und gleich auf die Reihen verteilt, damit beim
 * Umschalten nichts springt.
 */
@Composable
internal fun StilWahl(gewaehlt: FoodyStil, variante: StilVariante, breite: Dp, onWahl: (FoodyStil) -> Unit) {
    Raster(FoodyStil.entries, zelle = 88.dp, breite) { s, modifier -> StilMuster(s, variante, s == gewaehlt, modifier) { onWahl(s) } }
}

/**
 * Die vier Akzente des [stil]s als runde Farbmuster: auf dem Handy alle vier in einer Reihe; passen sie nicht (große
 * Schrift), zwei Reihen zu je zwei statt drei und einem allein.
 */
@Composable
internal fun AkzentWahl(stil: FoodyStil, gewaehlt: StilAkzent, breite: Dp, onWahl: (StilAkzent) -> Unit) {
    Raster(stil.akzente, zelle = 64.dp, breite, gleicheReihen = true) { a, modifier -> AkzentMuster(a, a == gewaehlt, modifier) { onWahl(a) } }
}

/**
 * Zellen mit fester Breite ([zelle], mit der Schrift wachsend), linksbündig, so viele Spalten, wie in [breite] passen.
 * [breite] ist in jedem Stil dieselbe (die der schmalsten Karte, siehe [DarstellungAbschnitt]), nicht der Platz in der
 * Karte des Stils: Ob der Stil die Wahl in eine Karte mit Innenabstand legt oder nicht, die Zellen bleiben gleich groß
 * und gleich auf die Reihen verteilt. [gleicheReihen]: lieber 2 + 2 als 3 + 1.
 */
@Composable
private fun <T> Raster(eintraege: List<T>, zelle: Dp, breite: Dp, gleicheReihen: Boolean = false, inhalt: @Composable (T, Modifier) -> Unit) {
    val schrift = LocalDensity.current.fontScale
    val spalten = rasterSpalten(eintraege.size, zelle, breite, schrift, gleicheReihen)
    val zellbreite = zellBreite(zelle, schrift)
    val abstand = FoodyAbstaende.s2
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
        eintraege.chunked(spalten).forEach { reihe ->
            Row(horizontalArrangement = Arrangement.spacedBy(abstand)) {
                reihe.forEach { inhalt(it, Modifier.width(zellbreite)) }
            }
        }
    }
}

/** Spaltenzahl für [Raster]: wie viele Zellen in [breite] passen, bei [gleicheReihen] auf gleich volle Reihen verteilt. */
internal fun rasterSpalten(anzahl: Int, zelle: Dp, breite: Dp, schrift: Float, gleicheReihen: Boolean): Int {
    val zellbreite = zellBreite(zelle, schrift)
    val abstand = FoodyAbstaende.s2
    val passen = ((breite + abstand) / (zellbreite + abstand)).toInt().coerceIn(1, maxOf(anzahl, 1))
    if (!gleicheReihen) return passen
    val reihen = (anzahl + passen - 1) / passen
    return (anzahl + reihen - 1) / reihen
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
                .padding(FoodyAbstaende.s1)
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
        // Immer Platz für zwei Zeilen in fester Zeilenhöhe: „Kosmisches Grauen“ verschiebt die Reihe in keiner Schrift
        Text(
            stil.anzeigename,
            style = MaterialTheme.typography.labelMedium.copy(lineHeight = 16.sp),
            fontWeight = if (gewaehlt) FontWeight.Bold else FontWeight.Medium,
            color = if (gewaehlt) FoodyTheme.farben.text else FoodyTheme.farben.nebentext,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
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
                .padding(FoodyAbstaende.s1)
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

/** Breite einer Zelle: [zelle], ab Schrift 118 % mitwachsend. */
private fun zellBreite(zelle: Dp, schrift: Float): Dp = maxOf(zelle, zelle * schrift * 0.85f)

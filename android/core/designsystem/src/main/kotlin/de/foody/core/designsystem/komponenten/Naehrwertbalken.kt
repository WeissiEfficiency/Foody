package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.FoodyAbstaende

/** Anteile der drei Segmente an der Balkenlänge (zusammen höchstens 1). */
data class Balkenanteile(val eiweiss: Float, val kh: Float, val fett: Float, val ueberZiel: Boolean)

/**
 * Verteilt den gegessenen Stand nach Energie (Eiweiß und Kohlenhydrate 4 kcal/g, Fett 9 kcal/g) und skaliert ihn auf
 * `kcal / ziel`. Ohne sinnvolles Ziel (null oder ≤ 0) füllt die Verteilung den ganzen Balken.
 */
fun balkenanteile(kcal: Int, ziel: Int?, eiweissG: Int, khG: Int, fettG: Int): Balkenanteile {
    val hatZiel = ziel != null && ziel > 0
    val ueber = hatZiel && kcal > ziel
    val e = 4f * eiweissG
    val k = 4f * khG
    val f = 9f * fettG
    val summe = e + k + f
    if (summe <= 0f) return Balkenanteile(0f, 0f, 0f, ueber)
    val laenge = if (hatZiel) minOf(1f, kcal.toFloat() / ziel) else 1f
    return Balkenanteile(e / summe * laenge, k / summe * laenge, f / summe * laenge, ueber)
}

/**
 * kcal-Stand gegen das Tagesziel mit Aufteilung Eiweiß (Oliv), Kohlenhydrate (Safran) und Fett (Tomate). Die Legende
 * nennt die Grammzahlen, damit die Farbe nicht allein trägt; TalkBack liest den ganzen Stand als einen Satz.
 */
@Composable
fun Naehrwertbalken(kcal: Int, ziel: Int?, eiweissG: Int, khG: Int, fettG: Int, modifier: Modifier = Modifier) {
    val f = FoodyTheme.farben
    val a = balkenanteile(kcal, ziel, eiweissG, khG, fettG)
    val hatZiel = ziel != null && ziel > 0
    val stand = if (hatZiel) "${ganzzahlDeutsch(kcal)} / ${ganzzahlDeutsch(ziel)} kcal" else "${ganzzahlDeutsch(kcal)} kcal"
    val kopf = if (a.ueberZiel) "$stand · über Ziel" else stand
    val satzStand = if (hatZiel) "${ganzzahlDeutsch(kcal)} von ${ganzzahlDeutsch(ziel)} kcal" else "${ganzzahlDeutsch(kcal)} kcal"
    val beschreibung = (if (a.ueberZiel) "$satzStand, über Ziel" else satzStand) +
        ": Eiweiß $eiweissG g, Kohlenhydrate $khG g, Fett $fettG g"

    Column(modifier, verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        Text(kopf, style = MaterialTheme.typography.titleSmall, color = f.text)
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(f.balkenSpur)
                .semantics { contentDescription = beschreibung },
        ) {
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                Segment(a.eiweiss, f.oliv)
                Segment(a.kh, f.safran)
                Segment(a.fett, f.primaer)
                val rest = 1f - a.eiweiss - a.kh - a.fett
                if (rest > 0.0001f) Spacer(Modifier.weight(rest))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s1)) {
            Legende("Eiweiß $eiweissG g", f.oliv)
            Legende("KH $khG g", f.safran)
            Legende("Fett $fettG g", f.primaer)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Segment(anteil: Float, farbe: Color) {
    if (anteil > 0.0001f) Box(Modifier.weight(anteil).fillMaxHeight().background(farbe))
}

@Composable
private fun Legende(text: String, farbe: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(farbe))
        Text(text, style = MaterialTheme.typography.bodySmall, color = FoodyTheme.farben.nebentext)
    }
}

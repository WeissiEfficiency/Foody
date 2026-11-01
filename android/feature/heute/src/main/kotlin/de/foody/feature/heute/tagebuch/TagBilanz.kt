package de.foody.feature.heute.tagebuch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.komponenten.LeererZustandZeile
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.komponenten.kcalText
import de.foody.core.designsystem.komponenten.ueberschrift
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.GlasStufe
import de.foody.feature.heute.anzeigename

/**
 * Tagesbilanz wie im Entwurf: Ring mit den kcal (mit Tagesziel als Anteil, sonst voll), daneben Eiweiß, KH und Fett
 * als Balken. Makroziele gibt es nicht; die Balken zeigen den Anteil an der Energie des Tages.
 */
@Composable
internal fun TagBilanz(kcal: Int, ziel: Int?, eiweiss: Int, kh: Int, fett: Int) {
    val f = FoodyTheme.farben
    val hatZiel = ziel != null && ziel > 0
    val anteil = if (hatZiel) (kcal.toFloat() / ziel).coerceIn(0f, 1f) else if (kcal > 0) 1f else 0f
    val beschreibung = (if (hatZiel) "${ganzzahlDeutsch(kcal)} von ${ganzzahlDeutsch(ziel)} kcal" else "${ganzzahlDeutsch(kcal)} kcal") +
        ": Eiweiß $eiweiss g, Kohlenhydrate $kh g, Fett $fett g"
    val energie = (eiweiss * 4 + kh * 4 + fett * 9).coerceAtLeast(1)
    GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(FoodyAbstaende.s4).semantics(mergeDescendants = true) { contentDescription = beschreibung },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s5),
        ) {
            Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(128.dp)) {
                    val strich = 14.dp.toPx()
                    val stil = Stroke(strich, cap = StrokeCap.Round)
                    drawArc(f.balkenSpur, -90f, 360f, false, style = Stroke(strich))
                    if (anteil > 0f) drawArc(f.primaer, -90f, 360f * anteil, false, style = stil)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ganzzahlDeutsch(kcal), style = MaterialTheme.typography.headlineSmall, color = f.text)
                    Text(
                        if (hatZiel) "von ${ganzzahlDeutsch(ziel)} kcal" else "kcal",
                        style = MaterialTheme.typography.labelSmall,
                        color = f.nebentext,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
                MakroBalken("Eiweiß", eiweiss, eiweiss * 4f / energie, f.oliv)
                MakroBalken("KH", kh, kh * 4f / energie, f.safran)
                MakroBalken("Fett", fett, fett * 9f / energie, f.primaer)
            }
        }
    }
}

@Composable
private fun MakroBalken(name: String, gramm: Int, anteil: Float, farbe: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s1)) {
        Row {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = FoodyTheme.farben.text)
            Text("$gramm g", style = MaterialTheme.typography.labelMedium, color = FoodyTheme.farben.nebentext)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(FoodyTheme.farben.balkenSpur)) {
            if (anteil > 0f) Box(Modifier.fillMaxWidth(anteil.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(farbe))
        }
    }
}

/**
 * Eine Mahlzeit als Glaskarte: Titel in Fraunces mit der kcal-Summe, die Einträge (Antippen ändert, der Papierkorb
 * löscht), ohne Einträge ein gestrichelter Platz „Noch nichts eingetragen · Eintragen“.
 */
@Composable
internal fun MahlzeitKarte(
    a: TagAbschnitt,
    onAendern: (TagebuchEintragEntity) -> Unit,
    onLoeschen: (TagebuchEintragEntity) -> Unit,
    onEintragen: () -> Unit,
) {
    val summe = a.eintraege.sumOf { it.werte().kcal ?: 0 }
    val vollstaendig = a.eintraege.all { it.vollstaendig && it.werte().kcal != null }
    GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth(), unschaerfeErlaubt = false) {
        Column(Modifier.padding(FoodyAbstaende.s4), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    a.mahlzeit.anzeigename(),
                    Modifier.weight(1f).ueberschrift(),
                    style = MaterialTheme.typography.titleLarge,
                    color = FoodyTheme.farben.text,
                )
                Text(kcalText(summe, vollstaendig), style = MaterialTheme.typography.labelLarge, color = FoodyTheme.farben.nebentext)
            }
            a.eintraege.forEach { e ->
                HorizontalDivider(color = FoodyTheme.farben.balkenSpur)
                EintragKartenZeile(e, onAendern = { onAendern(e) }, onLoeschen = { onLoeschen(e) })
            }
            if (a.eintraege.isEmpty()) LeererZustandZeile("Noch nichts eingetragen", "Eintragen", onEintragen)
        }
    }
}

/** Zeile in der Mahlzeitkarte: Name, darunter die Menge, rechts die kcal und der Papierkorb. */
@Composable
internal fun EintragKartenZeile(e: TagebuchEintragEntity, onAendern: () -> Unit, onLoeschen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "ändern", onClick = onAendern)
            .semantics(mergeDescendants = true) { contentDescription = "${e.name} ändern" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(e.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
        Text(kcalText(e.werte().kcal, e.vollstaendig), style = MaterialTheme.typography.titleSmall, color = FoodyTheme.farben.text)
        IconButton(onClick = onLoeschen) {
            Icon(Icons.Outlined.Delete, contentDescription = "${e.name} löschen", tint = FoodyTheme.farben.nebentext)
        }
    }
}

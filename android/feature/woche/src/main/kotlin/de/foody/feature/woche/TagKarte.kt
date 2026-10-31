package de.foody.feature.woche

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.foody.core.data.woche.PlanEintrag
import de.foody.core.data.woche.WochenTag
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.Naehrwertbalken
import de.foody.core.designsystem.komponenten.RezeptPlatzhalter
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.komponenten.rezeptBild
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.GlasStufe

/**
 * Ein Tag: Kopf mit Datum (heute hervorgehoben) und kcal, mit Tagesziel der Nährwertbalken; die Einträge zum Antippen
 * und „+ Mahlzeit“. Fehlen einem Gericht Werte, steht „mind.“ vor den kcal.
 */
@Composable
internal fun TagKarte(tag: WochenTag, heute: Boolean, ziel: Int?, onEintrag: (PlanEintrag) -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    val n = tag.naehrwerte
    GlasFlaeche(GlasStufe.DICHT, modifier.fillMaxWidth()) {
        Column(Modifier.padding(FoodyAbstaende.s4), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tagKurz(tag.datum), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, color = if (heute) FoodyTheme.farben.primaer else FoodyTheme.farben.text,
                )
                if (n != null && ziel == null) {
                    Text(
                        (if (n.complete) "" else "mind. ") + "${ganzzahlDeutsch(n.kcal)} kcal",
                        style = MaterialTheme.typography.titleSmall, color = FoodyTheme.farben.text,
                    )
                }
            }
            if (n != null && ziel != null) {
                Naehrwertbalken(n.kcal, ziel, n.protein, n.carbs, n.fat)
                if (!n.complete) Text("mind. – für ein Gericht fehlen Werte", style = MaterialTheme.typography.bodySmall, color = FoodyTheme.farben.nebentext)
            } else if (n != null) {
                Text(
                    "Eiweiß ${n.protein} g · KH ${n.carbs} g · Fett ${n.fat} g",
                    style = MaterialTheme.typography.bodySmall, color = FoodyTheme.farben.nebentext,
                )
            }
            tag.eintraege.forEach { e -> EintragZeile(e) { onEintrag(e) } }
            FoodyKnopf("+ Mahlzeit", onPlus, art = KnopfArt.TEXT)
        }
    }
}

@Composable
private fun EintragZeile(e: PlanEintrag, onKlick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onKlick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))) {
            rezeptBild(e.bild)?.invoke() ?: RezeptPlatzhalter(Modifier.size(40.dp), mitSymbol = false)
        }
        Column(Modifier.weight(1f)) {
            Text(mahlzeitAnzeige(e.mahlzeit), style = MaterialTheme.typography.labelMedium, color = FoodyTheme.farben.nebentext)
            Text(e.rezeptName, style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
        }
        Text("${e.portionen} P.", style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.text)
        if (e.gekocht) Icon(Icons.Outlined.CheckCircle, contentDescription = "gekocht", tint = FoodyTheme.farben.primaer)
    }
}

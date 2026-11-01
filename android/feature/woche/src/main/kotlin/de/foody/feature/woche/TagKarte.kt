package de.foody.feature.woche

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import de.foody.core.data.woche.PlanEintrag
import de.foody.core.data.woche.WochenTag
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.Naehrwertbalken
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.GlasStufe
import de.foody.domain.Mahlzeit
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Die vier bekannten Mahlzeiten in Tagesreihenfolge; fehlt eine an einem Tag mit Einträgen, gibt es einen Platz. */
internal val TAGES_MAHLZEITEN = listOf(Mahlzeit.FRUEHSTUECK, Mahlzeit.MITTAGESSEN, Mahlzeit.SNACK, Mahlzeit.ABENDESSEN)

/** „Samstag, 31.10.“ – Titel einer Tageskarte. */
internal fun tagLang(d: LocalDate): String =
    "${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN)}, ${d.dayOfMonth}.${d.monthValue}."

/**
 * Ein Tag wie im Entwurf: Titel in Fraunces (heute mit Abzeichen „Heute“), rechts die geplanten kcal; mit Tagesziel
 * der Nährwertbalken. Einträge mit Mahlzeit als Überzeile und „✓ gekocht“ bzw. Portionen. Darunter gestrichelte Plätze
 * für fehlende Mahlzeiten ([onPlatz]); ein leerer Tag hat einen Platz „+ Mahlzeit planen“, der wie [onPlus] zur
 * Mahlzeitwahl führt. Fehlen einem Gericht Werte, steht „mind.“ vor den kcal.
 */
@Composable
internal fun TagKarte(
    tag: WochenTag,
    heute: Boolean,
    ziel: Int?,
    onEintrag: (PlanEintrag) -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    onPlatz: (Mahlzeit) -> Unit = { onPlus() },
) {
    val n = tag.naehrwerte
    GlasFlaeche(GlasStufe.DICHT, modifier.fillMaxWidth(), unschaerfeErlaubt = false) {
        Column(Modifier.padding(FoodyAbstaende.s4), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
                Text(tagLang(tag.datum), style = MaterialTheme.typography.titleLarge, color = FoodyTheme.farben.text)
                if (heute) {
                    Text(
                        "Heute",
                        style = MaterialTheme.typography.labelMedium,
                        color = FoodyTheme.farben.aufPrimaerBehaelter,
                        modifier = Modifier.clip(CircleShape).background(FoodyTheme.farben.primaerBehaelter)
                            .padding(horizontal = FoodyAbstaende.s2, vertical = 2.dp),
                    )
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    if (n != null && ziel == null) {
                        Text(
                            (if (n.complete) "" else "mind. ") + "${ganzzahlDeutsch(n.kcal)} kcal geplant",
                            style = MaterialTheme.typography.bodySmall,
                            color = FoodyTheme.farben.nebentext,
                        )
                    }
                }
            }
            if (n != null && ziel != null) {
                Naehrwertbalken(n.kcal, ziel, n.protein, n.carbs, n.fat)
                if (!n.complete) Text("mind. – für ein Gericht fehlen Werte", style = MaterialTheme.typography.bodySmall, color = FoodyTheme.farben.nebentext)
            }
            tag.eintraege.forEach { e -> EintragZeile(e) { onEintrag(e) } }
            if (tag.eintraege.isEmpty()) {
                Platz("Mahlzeit planen", onPlus)
            } else {
                val belegt = tag.eintraege.mapNotNull { Mahlzeit.ausText(it.mahlzeit) }.toSet()
                TAGES_MAHLZEITEN.filter { it !in belegt }.forEach { m -> Platz("${mahlzeitAnzeige(m)} planen") { onPlatz(m) } }
                FoodyKnopf("+ Mahlzeit", onPlus, art = KnopfArt.TEXT)
            }
        }
    }
}

@Composable
private fun EintragZeile(e: PlanEintrag, onKlick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onKlick).padding(vertical = FoodyAbstaende.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
    ) {
        Column(Modifier.weight(1f)) {
            Augenbraue(mahlzeitAnzeige(e.mahlzeit))
            Text(e.rezeptName, style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
        }
        if (e.gekocht) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s1)) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = FoodyTheme.farben.oliv, modifier = Modifier.size(16.dp))
                Text("gekocht", style = MaterialTheme.typography.labelLarge, color = FoodyTheme.farben.oliv)
            }
        } else {
            Text("${e.portionen} P.", style = MaterialTheme.typography.labelLarge, color = FoodyTheme.farben.text)
        }
    }
}

/** Gestrichelter Platz „+ Snack planen“ für eine fehlende Mahlzeit. */
@Composable
private fun Platz(text: String, onKlick: () -> Unit) {
    val kante = FoodyTheme.farben.nebentext.copy(alpha = 0.5f)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .drawBehind {
                drawRoundRect(
                    color = kante,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                )
            }
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onKlick)
            .padding(horizontal = FoodyAbstaende.s4, vertical = FoodyAbstaende.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = FoodyTheme.farben.nebentext, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
    }
}

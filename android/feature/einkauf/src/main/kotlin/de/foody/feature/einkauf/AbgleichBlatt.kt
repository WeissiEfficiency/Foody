package de.foody.feature.einkauf

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.foody.core.data.einkauf.AbgleichArt
import de.foody.core.data.einkauf.AbgleichZeile
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.stil.stilText

/** Text einer Abgleichzeile: „+ 🍅 Tomaten · 400 g · neu“, „↑ 🧅 Zwiebeln · 3 → 4 Stk.“, „− 🥬 Spinat · entfällt“. */
internal fun abgleichText(z: AbgleichZeile): String {
    val kopf = "${z.emoji} ${z.name}"
    return when (z.art) {
        AbgleichArt.NEU -> "+ $kopf · ${z.neu} · neu"
        AbgleichArt.MEHR -> "↑ $kopf · ${z.alt} → ${z.neu}"
        AbgleichArt.WENIGER -> "↓ $kopf · ${z.alt} → ${z.neu}"
        AbgleichArt.WIEDER_OFFEN -> "↑ $kopf · ${z.alt} → ${z.neu} · wieder auf die Liste"
        AbgleichArt.ENTFAELLT -> "− $kopf · entfällt"
    }
}

/** „Mit Plan abgleichen“: jede Änderung abwählbar, „Übernehmen (n)“. */
@Composable
internal fun AbgleichBlatt(
    zeilen: List<AbgleichZeile>,
    abgewaehlt: Set<String>,
    onUmschalten: (String) -> Unit,
    onUebernehmen: () -> Unit,
    onSchliessen: () -> Unit,
) {
    val anzahl = zeilen.count { it.schluessel !in abgewaehlt }
    BlattRahmen(
        stilText("Mit Plan abgleichen", sage = "Mit dem Weg abgleichen"), onSchliessen,
        knopf = {
            FoodyKnopf(stilText("Übernehmen ($anzahl)", sage = "Änderungen übernehmen ($anzahl)"), onUebernehmen, Modifier.fillMaxWidth(), aktiv = anzahl > 0)
        },
        beschreibung = "Mit Plan abgleichen",
    ) {
        if (zeilen.isEmpty()) Text(stilText("Die Liste passt zum Plan.", sage = "Die Liste passt zum Weg."), color = FoodyTheme.farben.nebentext)
        zeilen.forEach { z ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = z.schluessel !in abgewaehlt, onCheckedChange = { onUmschalten(z.schluessel) },
                    modifier = Modifier.semantics { contentDescription = "${z.name} übernehmen" },
                )
                Text(abgleichText(z), style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
            }
        }
    }
}

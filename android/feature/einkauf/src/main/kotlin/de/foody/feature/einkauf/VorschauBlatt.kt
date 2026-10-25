package de.foody.feature.einkauf

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.Augenbraue
import de.foody.core.designsystem.komponenten.FoodyKnopf
import java.time.LocalDate

/** „Neue Liste aus dem Plan“: Zeitraum, Artikel je Abteilung mit Häkchen, „Liste anlegen (n)“. */
@Composable
internal fun VorschauBlatt(
    v: VorschauZustand,
    onZeitraum: (LocalDate, LocalDate) -> Unit,
    onUmschalten: (String) -> Unit,
    onAnlegen: () -> Unit,
    onSchliessen: () -> Unit,
) {
    val anzahl = v.zeilen.orEmpty().count { it.schluessel !in v.abgewaehlt }
    BlattRahmen(
        "Neue Liste aus dem Plan", onSchliessen,
        knopf = { FoodyKnopf("Liste anlegen ($anzahl)", onAnlegen, Modifier.fillMaxWidth(), aktiv = anzahl > 0) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Datum(v.von, "Anfang", { onZeitraum(v.von.minusDays(1), v.bis) }, { onZeitraum(v.von.plusDays(1), v.bis) })
            Text("bis", style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
            Datum(v.bis, "Ende", { onZeitraum(v.von, v.bis.minusDays(1)) }, { onZeitraum(v.von, v.bis.plusDays(1)) })
        }
        val zeilen = v.zeilen
        when {
            zeilen == null -> Text("Wird berechnet …", color = FoodyTheme.farben.nebentext)
            zeilen.isEmpty() -> Text("Im Zeitraum ist nichts geplant", style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.nebentext)
            else -> zeilen.groupBy { it.abteilung }.forEach { (abteilung, liste) ->
                Augenbraue(abteilung)
                liste.forEach { z ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = z.schluessel !in v.abgewaehlt, onCheckedChange = { onUmschalten(z.schluessel) },
                            modifier = Modifier.semantics { contentDescription = "${z.name} übernehmen" },
                        )
                        Text("${z.emoji} ${z.name} · ${z.menge}", style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
                    }
                }
            }
        }
    }
}

@Composable
private fun Datum(d: LocalDate, was: String, onFrueher: () -> Unit, onSpaeter: () -> Unit) {
    IconButton(onClick = onFrueher) {
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "$was früher", tint = FoodyTheme.farben.text)
    }
    Text(datumKurz(d), style = MaterialTheme.typography.titleSmall, color = FoodyTheme.farben.text)
    IconButton(onClick = onSpaeter) {
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "$was später", tint = FoodyTheme.farben.text)
    }
}

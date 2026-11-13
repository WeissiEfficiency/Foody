package de.foody.feature.woche

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
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
import de.foody.core.data.datumKurz
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasBlatt
import de.foody.core.designsystem.komponenten.BlattTitel
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.ueberschrift
import de.foody.core.designsystem.komponenten.zeilenImStil
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.stilText
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.domain.Mahlzeit
import de.foody.feature.woche.stil.VorschlagStil
import de.foody.feature.woche.stil.planenEinleitung
import java.time.LocalDate

/** Was das Füllen-Blatt auslösen kann. */
internal class FuellenAktionen(
    val onMahlzeit: (Mahlzeit) -> Unit,
    val onAbwaehlen: (LocalDate) -> Unit,
    val onTagNeu: (LocalDate) -> Unit,
    val onNeuMischen: () -> Unit,
    val onUebernehmen: () -> Unit,
)

/** „DI 27.10.“ */
internal fun tagKurz(d: LocalDate): String = datumKurz(d).uppercase()

/**
 * „Leere Tage füllen“: Mahlzeit wählen, je Tag ein Vorschlag (abwählbar, „↻“ zieht neu), „Neu mischen“ und
 * „Übernehmen (n)“. [namen] = Rezept-ID → Name.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FuellenBlatt(z: FuellenZustand, namen: Map<String, String>, a: FuellenAktionen, onSchliessen: () -> Unit) {
    val anzahl = (z.vorschlag.keys - z.abgewaehlt).size
    // Ganz geöffnet und scrollbar: die Knöpfe unten sollen ohne Ziehen erreichbar sein
    GlasBlatt(onSchliessen, ganzOffen = true) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = FoodyAbstaende.s6)
                .padding(bottom = FoodyAbstaende.s6).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
        ) {
            BlattTitel(
                stilText("Leere Tage füllen", sage = "Leere Tage bestücken", anime = "Planen!", bayrisch = "Wochn plana", kreuzer = "Kurs plotten"),
                beschreibung = "Leere Tage füllen",
            )
            if (FoodyTheme.stil.immersiv(FoodyStil.SAGE)) {
                Text(
                    "Für jeden leeren Tag wählt das Grimoire ein Mahl, das du länger nicht hattest.",
                    style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.nebentext,
                )
            }
            planenEinleitung()?.let { (satz, schrift) -> Text(satz, style = schrift, color = FoodyTheme.farben.nebentext) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
                listOf(Mahlzeit.FRUEHSTUECK, Mahlzeit.MITTAGESSEN, Mahlzeit.SNACK, Mahlzeit.ABENDESSEN).forEach { m ->
                    FoodyChip(mahlzeitAnzeige(m), ausgewaehlt = m == z.mahlzeit, onKlick = { a.onMahlzeit(m) })
                }
            }
            z.hinweis?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext) }
            val imStil = zeilenImStil()
            z.tage.forEachIndexed { nr, tag ->
                val name = z.vorschlag[tag]?.let { namen[it] }
                if (imStil) {
                    VorschlagStil(tag, tagKurz(tag), name, tag !in z.abgewaehlt, nr, { a.onAbwaehlen(tag) }, { a.onTagNeu(tag) })
                } else {
                    VorschlagZeile(tag, name, tag !in z.abgewaehlt, a)
                }
            }
            z.meldung?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext) }
            Row(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
                FoodyKnopf("Neu mischen", a.onNeuMischen, Modifier.weight(1f), art = KnopfArt.UMRISS, aktiv = z.hinweis == null)
                val bestuecken = if (anzahl == 1) "1 Tag bestücken" else "$anzahl Tage bestücken"
                FoodyKnopf(
                    stilText(
                        "Übernehmen ($anzahl)",
                        sage = bestuecken,
                        bayrisch = "Olle nehma ($anzahl)",
                        grauen = "Den Sternen folgen ($anzahl)",
                        kreuzer = "Route übernehmen ($anzahl)",
                    ),
                    a.onUebernehmen, Modifier.weight(1f), aktiv = anzahl > 0)
            }
        }
    }
}

@Composable
private fun VorschlagZeile(tag: LocalDate, name: String?, gewaehlt: Boolean, a: FuellenAktionen) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = name != null && gewaehlt, onCheckedChange = { a.onAbwaehlen(tag) }, enabled = name != null,
            modifier = Modifier.semantics { contentDescription = "${tagKurz(tag)} übernehmen" },
        )
        Text(
            "${tagKurz(tag)} · ${name ?: "kein Vorschlag"}", Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge, color = if (name != null) FoodyTheme.farben.text else FoodyTheme.farben.nebentext,
        )
        IconButton(onClick = { a.onTagNeu(tag) }) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Neuer Vorschlag für ${tagKurz(tag)}", tint = FoodyTheme.farben.text)
        }
    }
}

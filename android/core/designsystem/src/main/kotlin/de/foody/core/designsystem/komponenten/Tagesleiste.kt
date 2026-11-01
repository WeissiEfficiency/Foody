package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.AugenbraueStil
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.GlasStufe
import de.foody.core.designsystem.token.glasWerte
import java.util.Locale

/** Ein Tag der [Tagesleiste]: Kürzel („Mo“), Tageszahl, ob etwas eingetragen/geplant ist, und der Name für TalkBack. */
data class LeistenTag(val kuerzel: String, val tag: Int, val markiert: Boolean, val beschreibung: String)

/**
 * Sieben Pillen nebeneinander (Woche, Tagebuch): Kürzel in Versalien, Tageszahl, ein Punkt bei Einträgen. Der
 * [gewaehlt]e Tag ist in Primär gefüllt. Verhält sich für TalkBack wie Tabs.
 */
@Composable
fun Tagesleiste(tage: List<LeistenTag>, gewaehlt: Int, onWahl: (Int) -> Unit, modifier: Modifier = Modifier) {
    val farben = FoodyTheme.farben
    val glas = glasWerte(GlasStufe.LEICHT, FoodyTheme.dunkel, FoodyTheme.farbwelt)
    val form = RoundedCornerShape(FoodyAbstaende.s4)
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tage.forEachIndexed { i, t ->
            val aktiv = i == gewaehlt
            val schrift = if (aktiv) farben.aufPrimaer else farben.text
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(form)
                    .background(if (aktiv) farben.primaer else glas.toenung)
                    .border(1.dp, if (aktiv) Color.Transparent else glas.kante, form)
                    .selectable(selected = aktiv, role = Role.Tab, onClick = { onWahl(i) })
                    .semantics(mergeDescendants = true) { contentDescription = t.beschreibung }
                    .padding(vertical = FoodyAbstaende.s2),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    t.kuerzel.uppercase(Locale.GERMAN),
                    style = AugenbraueStil.copy(fontSize = 10.sp),
                    color = if (aktiv) farben.aufPrimaer else farben.nebentext,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(t.tag.toString(), style = MaterialTheme.typography.titleMedium, color = schrift, maxLines = 1)
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (!t.markiert) Color.Transparent else if (aktiv) farben.aufPrimaer else farben.oliv),
                )
            }
        }
    }
}

/** Initiale in einem Kreis (Einkauf, Zutaten, Konto) in der Behälterfarbe. */
@Composable
fun Initiale(name: String, modifier: Modifier = Modifier) {
    val farben = FoodyTheme.farben
    // Wächst mit großer Schrift, statt den Buchstaben abzuschneiden
    Box(
        modifier.sizeIn(minWidth = 32.dp, minHeight = 32.dp).clip(CircleShape).background(farben.primaerBehaelter).padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(Locale.GERMAN),
            style = MaterialTheme.typography.labelLarge,
            color = farben.aufPrimaerBehaelter,
        )
    }
}

package de.foody.feature.einkauf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.foody.core.data.einkauf.ListenKopf
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.stilText

/** „Frühere Listen“: Antippen öffnet, Papierkorb löscht (mit Rückfrage beim Aufrufer). */
@Composable
internal fun FruehereBlatt(listen: List<ListenKopf>, onOeffnen: (String) -> Unit, onLoeschen: (ListenKopf) -> Unit, onSchliessen: () -> Unit) {
    val titel = stilText("Frühere Listen", sage = "Listen des Händlers", anime = "Listen!", bayrisch = "Eikaufszettl", kreuzer = "Ladelisten")
    BlattRahmen(titel, onSchliessen, beschreibung = "Frühere Listen") {
        if (listen.isEmpty()) {
            Text(stilText("Keine früheren Listen.", sage = "Der Händler führt noch keine früheren Listen."), color = FoodyTheme.farben.nebentext)
        }
        listen.forEach { l ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button) { onOeffnen(l.id) }) {
                    Text(l.name, style = MaterialTheme.typography.titleMedium, color = FoodyTheme.farben.text)
                    Text(
                        "angelegt ${l.angelegt.dayOfMonth}.${l.angelegt.monthValue}. · ${l.artikel} Artikel",
                        style = MaterialTheme.typography.bodySmall, color = FoodyTheme.farben.nebentext,
                    )
                }
                IconButton(onClick = { onLoeschen(l) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "„${l.name}“ löschen", tint = FoodyTheme.farben.text)
                }
            }
        }
    }
}

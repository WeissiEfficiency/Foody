package de.foody.feature.zutaten

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.foody.core.data.zutaten.ZutatKurz
import de.foody.core.data.zutaten.filtern
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasBlatt
import de.foody.core.designsystem.token.FoodyAbstaende

/** Ziel fürs Zusammenführen wählen: Suche und Liste der übrigen Zutaten ([ziele] enthält die eigene nicht). */
@Composable
internal fun ZielwahlBlatt(ziele: List<ZutatKurz>, onGewaehlt: (ZutatKurz) -> Unit, onSchliessen: () -> Unit) {
    var suche by rememberSaveable { mutableStateOf("") }
    GlasBlatt(onSchliessen, ganzOffen = true) {
        Column(Modifier.fillMaxWidth().padding(bottom = FoodyAbstaende.s4)) {
            Text(
                "Zusammenführen mit …", Modifier.padding(horizontal = FoodyAbstaende.s4),
                style = MaterialTheme.typography.titleLarge, color = FoodyTheme.farben.text,
            )
            OutlinedTextField(
                suche, { suche = it }, Modifier.fillMaxWidth().padding(horizontal = FoodyAbstaende.s4, vertical = FoodyAbstaende.s2),
                label = { Text("Suchen") }, singleLine = true,
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(filtern(ziele, suche, emptySet()), key = { it.id }) { z ->
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onGewaehlt(z) }
                            .padding(horizontal = FoodyAbstaende.s4, vertical = 10.dp),
                    ) {
                        z.kategorie?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = FoodyTheme.farben.nebentext) }
                        Text(z.name, style = MaterialTheme.typography.bodyLarge, color = FoodyTheme.farben.text)
                    }
                }
            }
        }
    }
}

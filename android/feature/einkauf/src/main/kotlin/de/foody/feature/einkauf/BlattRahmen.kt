package de.foody.feature.einkauf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import java.time.LocalDate

/**
 * Blatt des Einkaufs: ganz geöffnet, Inhalt scrollbar, [knopf] fest unten (ohne Ziehen erreichbar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlattRahmen(titel: String, onSchliessen: () -> Unit, knopf: (@Composable () -> Unit)? = null, inhalt: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onSchliessen, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FoodyTheme.farben.grund,
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(titel, style = MaterialTheme.typography.headlineSmall, color = FoodyTheme.farben.text)
                inhalt()
            }
            knopf?.let { Column(Modifier.fillMaxWidth().padding(top = 16.dp)) { it() } }
        }
    }
}

/** „Liste löschen?“ – vor dem Löschen einer ganzen Liste samt Artikeln. */
@Composable
internal fun LoeschenFrage(name: String, onLoeschen: () -> Unit, onAbbrechen: () -> Unit) {
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("Liste löschen?") },
        text = { Text("„$name“ mit allen Artikeln löschen?") },
        confirmButton = { FoodyKnopf("Löschen", onLoeschen, art = KnopfArt.TEXT) },
        dismissButton = { FoodyKnopf("Abbrechen", onAbbrechen, art = KnopfArt.TEXT) },
    )
}

/** „Mi 28.10.“ */
internal fun datumKurz(d: LocalDate): String =
    "${listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")[d.dayOfWeek.value - 1]} ${d.dayOfMonth}.${d.monthValue}."

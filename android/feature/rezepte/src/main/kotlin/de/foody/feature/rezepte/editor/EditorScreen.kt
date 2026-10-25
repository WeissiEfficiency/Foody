package de.foody.feature.rezepte.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.data.rezepte.Gespeichert
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import kotlinx.coroutines.launch

private val INHALT_MAX = 720.dp

/**
 * Rezept-Editor ([id] `null` = neu): Kopf mit Zurück und Speichern, darunter alle Felder auf einer scrollenden Seite.
 * Zurück mit ungespeicherten Änderungen fragt „Änderungen verwerfen?“. Nach dem Speichern [onFertig] mit der ID.
 * [gekochtMit]: aus dem Kochmodus mit dieser Portionenzahl – weicht sie vom Rezept ab, steht oben ein Hinweis.
 */
@Composable
fun EditorRoute(
    id: String?,
    onFertig: (String) -> Unit,
    onZurueck: () -> Unit,
    gekochtMit: Int? = null,
    vm: EditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { vm.laden(id) }
    val z by vm.zustand.collectAsStateWithLifecycle()
    val ursprung by vm.ursprung.collectAsStateWithLifecycle()
    val moeglich by vm.speichernMoeglich.collectAsStateWithLifecycle()
    val geaendert by vm.geaendert.collectAsStateWithLifecycle()
    var fragen by rememberSaveable { mutableStateOf(false) }
    val bereich = rememberCoroutineScope()
    val zurueck = { if (geaendert) fragen = true else onZurueck() }
    BackHandler(onBack = zurueck)

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = INHALT_MAX).fillMaxWidth().safeDrawingPadding().imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = zurueck) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", tint = FoodyTheme.farben.text)
                }
                Text(
                    if (id == null) "Neues Rezept" else "Bearbeiten", Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall, color = FoodyTheme.farben.text,
                )
                FoodyKnopf("Speichern", {
                    bereich.launch { (vm.speichern() as? Gespeichert.Ok)?.let { onFertig(it.id) } }
                }, aktiv = moeglich)
            }
            z.hinweis?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            val rezeptPortionen = ursprung.portionen.toIntOrNull()
            if (z.geladen && gekochtMit != null && rezeptPortionen != null && gekochtMit != rezeptPortionen) {
                Text(
                    "Gekocht mit $gekochtMit statt $rezeptPortionen Portionen – Mengen beziehen sich auf $rezeptPortionen",
                    style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.primaer,
                )
            }
            if (z.geladen) EditorFelder(z, vm)
        }
    }
    if (fragen) VerwerfenFrage(onVerwerfen = { fragen = false; onZurueck() }, onWeiter = { fragen = false })
}

/** „Änderungen verwerfen?“ – vor dem Verlassen eines Editors mit ungespeicherten Änderungen. */
@Composable
internal fun VerwerfenFrage(onVerwerfen: () -> Unit, onWeiter: () -> Unit) {
    AlertDialog(
        onDismissRequest = onWeiter,
        title = { Text("Änderungen verwerfen?") },
        text = { Text("Die ungespeicherten Änderungen gehen verloren.") },
        confirmButton = { FoodyKnopf("Verwerfen", onVerwerfen, art = KnopfArt.TEXT) },
        dismissButton = { FoodyKnopf("Weiter bearbeiten", onWeiter, art = KnopfArt.TEXT) },
    )
}

package de.foody.app.ui.settings

import de.foody.app.ui.theme.ThemeMode
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.graphics.Color
import de.foody.app.ui.theme.FoodyGlass
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.app.R
import de.foody.app.ui.common.ScreenHeader
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onManageIngredients: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmImport by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val text = message?.let { stringResource(it) }
    LaunchedEffect(text) { if (text != null) { snackbar.showSnackbar(text); vm.messageShown() } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(vm::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmImport = uri?.toString()
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ScreenHeader(stringResource(R.string.nav_more), stringResource(R.string.nav_settings))
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsCard(stringResource(R.string.appearance_title)) {
                    val mode by vm.themeMode.collectAsStateWithLifecycle()
                    val options = listOf(
                        ThemeMode.SYSTEM to R.string.appearance_system,
                        ThemeMode.LIGHT to R.string.appearance_light,
                        ThemeMode.DARK to R.string.appearance_dark,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { i, (m, label) ->
                            SegmentedButton(
                                selected = mode == m,
                                onClick = { vm.setThemeMode(m) },
                                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            ) { Text(stringResource(label)) }
                        }
                    }
                }
                SettingsCard(stringResource(R.string.ingredients_title)) {
                    OutlinedButton(onManageIngredients, Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)) {
                        Text(stringResource(R.string.ingredients_manage))
                    }
                }
                SettingsCard(stringResource(R.string.backup_title)) {
                    Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton({ exportLauncher.launch("foody-backup-${LocalDate.now()}.zip") }, Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)) { Text(stringResource(R.string.backup_export)) }
                    OutlinedButton({ importLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream")) }, Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)) { Text(stringResource(R.string.backup_import)) }
                }
                SettingsCard(stringResource(R.string.privacy_title)) {
                    Text(stringResource(R.string.privacy_text), style = MaterialTheme.typography.bodyMedium)
                }
                SettingsCard(stringResource(R.string.about_title)) {
                    Text(stringResource(R.string.nutrition_disclaimer), style = MaterialTheme.typography.bodySmall)
                }
                // Destruktive Aktion zurückhaltend und zuletzt – nicht der auffälligste Knopf der Seite
                SettingsCard(stringResource(R.string.danger_zone)) {
                    Text(stringResource(R.string.data_delete_warning), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        { confirmDelete = true },
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.data_delete_all)) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text(stringResource(R.string.backup_import)) },
            text = { Text(stringResource(R.string.backup_import_warning)) },
            confirmButton = { TextButton({ vm.import(uri.toUri()); confirmImport = null }) { Text(stringResource(R.string.action_import)) } },
            dismissButton = { TextButton({ confirmImport = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.data_delete_all)) },
            text = { Text(stringResource(R.string.data_delete_warning)) },
            confirmButton = { TextButton({ vm.deleteAll(); confirmDelete = false }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = FoodyGlass.fill,
        border = FoodyGlass.border,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

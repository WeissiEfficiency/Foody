package de.foody.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import de.foody.app.ui.common.ScreenHeader
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.foody.app.R
import de.foody.app.data.repo.BackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val backup: BackupRepository) : ViewModel() {
    private val _message = MutableStateFlow<Int?>(null)
    val message = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    private fun run(ok: Int, block: suspend () -> Unit) = viewModelScope.launch {
        _message.value = runCatching { block() }.fold({ ok }, { R.string.backup_error })
    }

    fun export(uri: android.net.Uri) = run(R.string.backup_exported) { backup.export(uri) }
    fun import(uri: android.net.Uri) = run(R.string.backup_imported) { backup.import(uri) }
    fun deleteAll() = run(R.string.data_deleted) { backup.deleteAll() }
}

@Composable
fun SettingsScreen(onManageIngredients: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmImport by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val text = message?.let { stringResource(it) }
    LaunchedEffect(text) { if (text != null) { snackbar.showSnackbar(text); vm.messageShown() } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmImport = uri?.toString()
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            ScreenHeader(stringResource(R.string.nav_more), stringResource(R.string.nav_settings))
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsCard(stringResource(R.string.ingredients_title)) {
                    OutlinedButton(onManageIngredients, Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)) {
                        Text(stringResource(R.string.ingredients_manage))
                    }
                }
                SettingsCard(stringResource(R.string.backup_title)) {
                    Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton({ exportLauncher.launch("foody-backup-${LocalDate.now()}.json") }, Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)) { Text(stringResource(R.string.backup_export)) }
                    OutlinedButton({ importLauncher.launch(arrayOf("application/json")) }, Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)) { Text(stringResource(R.string.backup_import)) }
                }
                SettingsCard(stringResource(R.string.privacy_title)) {
                    Text(stringResource(R.string.privacy_text), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        { confirmDelete = true },
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.data_delete_all)) }
                }
                SettingsCard(stringResource(R.string.about_title)) {
                    Text(stringResource(R.string.nutrition_disclaimer), style = MaterialTheme.typography.bodySmall)
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
            confirmButton = { TextButton({ vm.import(android.net.Uri.parse(uri)); confirmImport = null }) { Text(stringResource(R.string.action_import)) } },
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
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

package de.foody.app.ui.sync

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.app.R
import de.foody.app.ui.common.ScreenHeader
import de.foody.app.ui.settings.SettingsCard
import java.time.LocalDate

/**
 * Bildschirm „Server verbinden“. [onBack] verlässt den Assistenten ohne Verbindung (nach [SyncSetupViewModel.cancel]);
 * [onDone] wird einmal nach erfolgreichem Verbinden aufgerufen (Navigation zurück zu den Einstellungen).
 */
@Composable
fun SyncSetupScreen(onBack: () -> Unit, onDone: () -> Unit, vm: SyncSetupViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val offerBackup by vm.offerBackup.collectAsStateWithLifecycle()

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.onBackupChosen(uri) else vm.onBackupCancelled()
    }
    LaunchedEffect(offerBackup) {
        if (offerBackup) {
            vm.offerBackupHandled()
            backupLauncher.launch("foody-backup-${LocalDate.now()}.zip")
        }
    }
    LaunchedEffect(state.step) { if (state.step == SetupStep.DONE) onDone() }

    val leave = {
        vm.cancel()
        onBack()
    }
    BackHandler {
        if (state.busy) return@BackHandler // während einer Anfrage ignorieren
        // Auf dem Konto-Schritt zuerst zur Adresse zurück; sonst Assistent verlassen.
        if (!(state.step == SetupStep.ACCOUNT && vm.backToUrl())) leave()
    }

    Column {
        ScreenHeader(stringResource(R.string.sync_title), stringResource(R.string.sync_setup_title))
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (state.step) {
                SetupStep.URL -> UrlStep(state, vm)
                SetupStep.ACCOUNT -> AccountStep(state, vm)
                SetupStep.HOUSEHOLD -> HouseholdStep(state, vm)
                SetupStep.FIRST_SYNC -> FirstSyncStep(state, vm)
                SetupStep.DONE -> Text(stringResource(R.string.sync_setup_connected))
            }
            state.error?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.step != SetupStep.DONE) {
                TextButton(leave, Modifier.fillMaxWidth(), enabled = !state.busy) { Text(stringResource(R.string.sync_setup_cancel)) }
            }
        }
    }
}

@Composable
private fun UrlStep(state: SyncSetupState, vm: SyncSetupViewModel) {
    SettingsCard(stringResource(R.string.sync_setup_title)) {
        Text(stringResource(R.string.sync_setup_url_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            state.url, vm::setUrl, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.sync_setup_url_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.submitUrl() }),
        )
        Button(vm::submitUrl, Modifier.fillMaxWidth(), enabled = state.url.isNotBlank() && !state.busy, shape = RoundedCornerShape(50)) {
            Text(stringResource(R.string.sync_setup_next))
        }
    }
}

@Composable
private fun AccountStep(state: SyncSetupState, vm: SyncSetupViewModel) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    SettingsCard(state.url) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!state.register, { vm.setRegister(false) }, { Text(stringResource(R.string.sync_setup_mode_login)) })
            FilterChip(state.register, { vm.setRegister(true) }, { Text(stringResource(R.string.sync_setup_mode_register)) })
        }
        if (state.register) {
            OutlinedTextField(
                state.inviteCode, vm::setInviteCode, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.sync_setup_invite_code)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
        }
        OutlinedTextField(
            state.username, vm::setUsername, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.sync_setup_username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            state.password, vm::setPassword, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.sync_setup_password)) },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit(state)) vm.submitAccount() }),
            trailingIcon = {
                IconButton({ showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        stringResource(if (showPassword) R.string.sync_setup_password_hide else R.string.sync_setup_password_show),
                    )
                }
            },
        )
        Button(vm::submitAccount, Modifier.fillMaxWidth(), enabled = canSubmit(state), shape = RoundedCornerShape(50)) {
            Text(stringResource(if (state.register) R.string.sync_setup_register else R.string.sync_setup_login))
        }
    }
}

private fun canSubmit(s: SyncSetupState) =
    !s.busy && s.username.isNotBlank() && s.password.isNotEmpty() && (!s.register || s.inviteCode.isNotBlank())

@Composable
private fun HouseholdStep(state: SyncSetupState, vm: SyncSetupViewModel) {
    SettingsCard(stringResource(R.string.sync_setup_household_title)) {
        if (state.households.isNotEmpty()) {
            Text(stringResource(R.string.sync_setup_household_pick), style = MaterialTheme.typography.titleSmall)
            state.households.forEach { h ->
                OutlinedButton({ vm.selectHousehold(h.id) }, Modifier.fillMaxWidth(), enabled = !state.busy, shape = RoundedCornerShape(50)) {
                    Text(h.name)
                }
            }
        }
        Text(stringResource(R.string.sync_setup_household_new), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            state.newHouseholdName, vm::setNewHouseholdName, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.sync_setup_household_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (state.newHouseholdName.isNotBlank()) vm.createHousehold() }),
        )
        Button(vm::createHousehold, Modifier.fillMaxWidth(), enabled = state.newHouseholdName.isNotBlank() && !state.busy, shape = RoundedCornerShape(50)) {
            Text(stringResource(R.string.sync_setup_household_create))
        }
    }
}

@Composable
private fun FirstSyncStep(state: SyncSetupState, vm: SyncSetupViewModel) {
    SettingsCard(stringResource(R.string.sync_setup_first_title)) {
        Text(stringResource(R.string.sync_setup_first_text), style = MaterialTheme.typography.bodyMedium)
        Button(vm::chooseMerge, Modifier.fillMaxWidth(), enabled = !state.busy, shape = RoundedCornerShape(50)) {
            Text(stringResource(R.string.sync_setup_merge))
        }
        Text(stringResource(R.string.sync_setup_merge_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(vm::chooseReplace, Modifier.fillMaxWidth(), enabled = !state.busy, shape = RoundedCornerShape(50)) {
            Text(stringResource(R.string.sync_setup_replace))
        }
        Text(stringResource(R.string.sync_setup_replace_hint), style = MaterialTheme.typography.bodySmall)
    }
}

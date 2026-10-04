package de.foody.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.app.R
import de.foody.app.ui.common.medium
import de.foody.sync.protocol.DeviceDto
import java.time.Instant
import java.time.ZoneId

/**
 * Karte „Synchronisierung“: Status, Verbinden/Erneut verbinden (Navigation über [onConnect], Argument = erneut
 * verbinden), sofort abgleichen, einladen, Geräte, Probleme und Trennen. Meldungen erscheinen in [snackbar].
 */
@Composable
fun SyncSettingsCard(
    snackbar: SnackbarHostState,
    onConnect: (reconnect: Boolean) -> Unit,
    vm: SyncSettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    val message = state.message?.let { stringResource(it) }
    LaunchedEffect(message) { if (message != null) { snackbar.showSnackbar(message); vm.messageShown() } }

    SettingsCard(stringResource(R.string.sync_title)) {
        if (!state.connected) {
            Text(stringResource(R.string.sync_hint_off), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.sync_status_off), style = MaterialTheme.typography.bodyMedium)
            Button({ onConnect(false) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)) {
                Text(stringResource(R.string.sync_connect))
            }
        } else {
            val last = relativeTime(state.lastSyncAt)
            val household = state.householdName
            val status = when {
                state.unauthorized -> stringResource(R.string.sync_status_unauthorized)
                household != null -> stringResource(R.string.sync_status_connected_household, household, last)
                else -> stringResource(R.string.sync_status_connected, last)
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)
            if (state.unauthorized) {
                Button({ onConnect(true) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)) {
                    Text(stringResource(R.string.sync_reconnect))
                }
            } else {
                Button({ vm.syncNow() }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)) {
                    Text(stringResource(R.string.sync_now))
                }
                OutlinedButton({ vm.createInvite() }, Modifier.fillMaxWidth(), enabled = !state.busy, shape = RoundedCornerShape(50)) {
                    Text(stringResource(R.string.sync_invite))
                }
                OutlinedButton({ vm.loadDevices() }, Modifier.fillMaxWidth(), enabled = !state.busy, shape = RoundedCornerShape(50)) {
                    Text(stringResource(R.string.sync_devices))
                }
                if (state.problemCount > 0) {
                    OutlinedButton({ vm.loadProblems() }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)) {
                        Text(stringResource(R.string.sync_problems_button, state.problemCount))
                    }
                }
            }
            OutlinedButton(
                { confirmDisconnect = true },
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.sync_disconnect)) }
        }
    }

    state.invite?.let { invite -> InviteDialog(invite.code, invite.expiresAt, state.serverUrl, vm::dismissInvite) }
    state.devices?.let { devices -> DevicesDialog(devices, vm::revokeDevice, { vm.dismissDevices(); confirmDisconnect = true }, vm::dismissDevices) }
    state.problems?.let { problems ->
        AlertDialog(
            onDismissRequest = vm::dismissProblems,
            title = { Text(stringResource(R.string.sync_problems_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    problems.forEach { p ->
                        Column {
                            Text(p.title ?: stringResource(p.type), style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (p.title != null) "${stringResource(p.type)} · ${stringResource(p.reason)}" else stringResource(p.reason),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(vm::dismissProblems) { Text(stringResource(R.string.action_close)) } },
        )
    }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text(stringResource(R.string.sync_disconnect_title)) },
            text = { Text(stringResource(R.string.sync_disconnect_text)) },
            confirmButton = { TextButton({ vm.disconnect(); confirmDisconnect = false }) { Text(stringResource(R.string.sync_disconnect)) } },
            dismissButton = { TextButton({ confirmDisconnect = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun InviteDialog(code: String, expiresAt: Long, serverUrl: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val copied = stringResource(R.string.sync_invite_copied)
    val shareText = stringResource(R.string.sync_invite_share_text, serverUrl.orEmpty(), code)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_invite_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(code, style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.sync_invite_expires, Instant.ofEpochMilli(expiresAt).atZone(ZoneId.systemDefault()).toLocalDate().medium()),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row {
                    TextButton({
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(copied, code))
                    }) { Text(stringResource(R.string.sync_invite_copy)) }
                    TextButton({
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                        context.startActivity(Intent.createChooser(send, null))
                    }) { Text(stringResource(R.string.action_share)) }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun DevicesDialog(devices: List<DeviceDto>, onRevoke: (String) -> Unit, onDisconnect: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_devices_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                devices.forEach { d ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            if (d.current) "${d.name} (${stringResource(R.string.sync_device_this)})" else d.name,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(stringResource(R.string.sync_device_last_seen, relativeTime(d.lastSeenAt)), style = MaterialTheme.typography.bodySmall)
                        // Das eigene Gerät wird nicht „abgemeldet“, sondern über „Trennen“ vollständig gelöst.
                        TextButton({ if (d.current) onDisconnect() else onRevoke(d.id) }) {
                            Text(stringResource(if (d.current) R.string.sync_disconnect else R.string.sync_device_revoke))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** Kurze deutsche Zeitangabe: „gerade eben“, „vor 5 Min.“, „vor 2 Std.“, sonst das Datum; `null` → „noch nie“. */
@Composable
private fun relativeTime(at: Long?): String {
    if (at == null) return stringResource(R.string.sync_never)
    val minutes = ((System.currentTimeMillis() - at) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.sync_just_now)
        minutes < 60 -> stringResource(R.string.sync_minutes_ago, minutes.toInt())
        minutes < 24 * 60 -> stringResource(R.string.sync_hours_ago, (minutes / 60).toInt())
        else -> Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate().medium()
    }
}

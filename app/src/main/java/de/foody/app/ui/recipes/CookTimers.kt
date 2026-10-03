package de.foody.app.ui.recipes

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.foody.app.R
import de.foody.domain.StepTimer
import kotlinx.coroutines.delay

/** Ein laufender Timer; [endAt] als Uhrzeit, damit er Konfigurationswechsel (Drehen) übersteht. */
data class RunningTimer(val label: String, val endAt: Long)

@Composable
fun rememberRunningTimers(): SnapshotStateList<RunningTimer> = rememberSaveable(
    saver = listSaver(
        save = { list -> list.flatMap { listOf(it.label, it.endAt.toString()) } },
        restore = { flat -> flat.chunked(2).map { (l, e) -> RunningTimer(l, e.toLong()) }.toMutableStateList() },
    ),
) { mutableStateListOf() }

private fun List<RunningTimer>.toMutableStateList() = mutableStateListOf<RunningTimer>().also { it.addAll(this) }

/** Chips für die im Schritt erkannten Zeiten; Tippen startet einen Timer. */
@Composable
fun StepTimerChips(timers: List<StepTimer>, onStart: (StepTimer) -> Unit) {
    if (timers.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        timers.forEach { t ->
            AssistChip(
                onClick = { onStart(t) },
                label = { Text(stringResource(R.string.timer_start, t.label)) },
                leadingIcon = { Icon(Icons.Outlined.Timer, null, Modifier.size(18.dp)) },
                shape = RoundedCornerShape(50),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    leadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
                border = null,
            )
        }
    }
}

/**
 * Leiste mit allen laufenden Timern, unabhängig von der aktuellen Seite.
 * Abgelaufene Timer piepen einmal (ToneGenerator – ohne zusätzliche Berechtigung) und bleiben sichtbar, bis man sie schließt.
 */
@Composable
fun RunningTimersBar(timers: SnapshotStateList<RunningTimer>) {
    if (timers.isEmpty()) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timers.size) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val beeped = remember { mutableSetOf<RunningTimer>() }
    val haptic = LocalHapticFeedback.current
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 90) }.getOrNull() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { tone?.release() } }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        timers.forEach { t ->
            val left = t.endAt - now
            val done = left <= 0
            if (done && beeped.add(t)) {
                tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                    .background(if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg = if (done) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSecondary
                Icon(Icons.Outlined.Timer, null, tint = fg, modifier = Modifier.size(18.dp))
                Text(
                    if (done) stringResource(R.string.timer_done, t.label) else "${t.label} · ${formatLeft(left)}",
                    style = MaterialTheme.typography.titleSmall, color = fg,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton({ timers.remove(t) }) { Icon(Icons.Default.Close, stringResource(R.string.timer_cancel), tint = fg) }
            }
        }
    }
}

private fun formatLeft(ms: Long): String {
    val s = (ms + 999) / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

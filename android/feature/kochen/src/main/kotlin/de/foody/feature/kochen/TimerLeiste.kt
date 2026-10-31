package de.foody.feature.kochen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasFlaeche
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.GlasStufe
import de.foody.feature.kochen.timer.LaufenderTimer

/** Laufende und abgelaufene Timer; Timer anderer Rezepte mit Rezeptnamen. Das Kreuz bricht ab bzw. tippt weg. */
@Composable
internal fun TimerLeiste(timer: List<LaufenderTimer>, rezeptId: String?, jetzt: Long, onEntfernen: (String) -> Unit) {
    if (timer.isEmpty()) return
    GlasFlaeche(GlasStufe.DICHT, Modifier.fillMaxWidth().padding(horizontal = FoodyAbstaende.s4)) {
        Column(Modifier.padding(horizontal = FoodyAbstaende.s4, vertical = FoodyAbstaende.s1)) {
            timer.sortedBy { it.endeUm }.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val titel = if (t.rezeptId == rezeptId) t.label else "${t.rezeptName}: ${t.label}"
                        Text(titel, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
                        Text(
                            if (t.abgelaufen(jetzt)) "abgelaufen" else restzeit(t.endeUm - jetzt),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (t.abgelaufen(jetzt)) FoodyTheme.farben.primaer else FoodyTheme.farben.text,
                        )
                    }
                    IconButton(onClick = { onEntfernen(t.id) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Timer ${t.label} beenden", tint = FoodyTheme.farben.text)
                    }
                }
            }
        }
    }
}

/** „20:00“, „1:05:00“ – Restzeit aufgerundet auf volle Sekunden. */
internal fun restzeit(ms: Long): String {
    val s = (ms + 999) / 1000
    val (h, m, sek) = Triple(s / 3600, s % 3600 / 60, s % 60)
    return if (h > 0) "%d:%02d:%02d".format(h, m, sek) else "%d:%02d".format(m, sek)
}

/**
 * Ton und Vibration, wenn bei offenem Kochmodus ein Timer abläuft und Benachrichtigungen nicht erlaubt sind (mit
 * Freigabe meldet sich der Dienst selbst).
 */
@Composable
internal fun AblaufMelden(timer: List<LaufenderTimer>, jetzt: Long) {
    val context = LocalContext.current
    val gemeldet = remember { mutableStateOf(timer.filter { it.abgelaufen(jetzt) }.map { it.id }.toSet()) }
    val abgelaufen = timer.filter { it.abgelaufen(jetzt) }.map { it.id }.toSet()
    LaunchedEffect(abgelaufen) {
        val neu = abgelaufen - gemeldet.value
        gemeldet.value = abgelaufen
        if (neu.isNotEmpty() && !benachrichtigungenErlaubt(context)) melden(context)
    }
}

private fun benachrichtigungenErlaubt(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun melden(context: Context) {
    RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.play()
    context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
}

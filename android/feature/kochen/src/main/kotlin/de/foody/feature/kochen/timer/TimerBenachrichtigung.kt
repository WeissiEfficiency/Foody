package de.foody.feature.kochen.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import de.foody.feature.kochen.R

/** Kanäle und Benachrichtigungen der Kochtimer. */
internal object TimerBenachrichtigung {
    const val LAUFEND_ID = 6_200
    private const val KANAL_LAUFEND = "kochen_laufend"
    private const val KANAL_ENDE = "kochen_ende"

    fun kanaeleAnlegen(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(KANAL_LAUFEND, "Laufende Timer", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(
            NotificationChannel(KANAL_ENDE, "Timer abgelaufen", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
                enableVibration(true)
            },
        )
    }

    /** Laufende Meldung des Dienstes: Anzahl, nächster Timer mit Countdown bis [naechstesEnde]. */
    fun laufend(context: Context, laufende: List<LaufenderTimer>, naechstesEnde: Long): Notification {
        val naechster = laufende.minByOrNull { it.endeUm }
        return NotificationCompat.Builder(context, KANAL_LAUFEND)
            .setSmallIcon(R.drawable.ic_stat_kochtimer)
            .setContentTitle(
                when (laufende.size) {
                    // Nur Abgelaufenes zu melden: der Dienst endet gleich wieder
                    0 -> "Kochtimer"
                    1 -> "1 Timer läuft"
                    else -> "${laufende.size} Timer laufen"
                },
            )
            .setContentText(naechster?.let { "${it.rezeptName}: ${it.label}" })
            .setWhen(naechstesEnde)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(naechster?.let { oeffnen(context, it) })
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Meldet das Ende von [t]; ohne Freigabe nichts (die App meldet sich dann selbst, solange sie offen ist). */
    fun ende(context: Context, t: LaufenderTimer) = zeigen(
        context, t.id.hashCode(),
        NotificationCompat.Builder(context, KANAL_ENDE)
            .setSmallIcon(R.drawable.ic_stat_kochtimer)
            .setContentTitle(t.rezeptName)
            .setContentText("Timer „${t.label}“ ist abgelaufen")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(oeffnen(context, t))
            .build(),
    )

    fun zeigen(context: Context, id: Int, n: Notification) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(id, n)
        }
    }

    /** Öffnet den Kochmodus des Rezepts über den Deep Link `foody://kochen/{id}?portionen={n}`. */
    private fun oeffnen(context: Context, t: LaufenderTimer): PendingIntent = PendingIntent.getActivity(
        context, t.rezeptId.hashCode(),
        Intent(Intent.ACTION_VIEW, "foody://kochen/${t.rezeptId}?portionen=${t.portionen}".toUri()).setPackage(context.packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

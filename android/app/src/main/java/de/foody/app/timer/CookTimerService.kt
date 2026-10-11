package de.foody.app.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import de.foody.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hält laufende Kochtimer am Leben, auch bei ausgeschaltetem Bildschirm oder wenn Foody im Hintergrund ist.
 *
 * Bewusst ein Vordergrunddienst mit Teil-Wakelock statt exakter Alarme: Exakte Alarme verlangen seit Android 14
 * eine Freigabe, die man in den Einstellungen selbst erteilen muss; ungenaue Alarme kommen im Doze-Modus
 * minutenlang zu spät. Der Wakelock gilt nur bis kurz nach dem nächsten Timer-Ende.
 */
@AndroidEntryPoint
class CookTimerService : Service() {
    @Inject lateinit var timers: CookTimerRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val alerted = mutableSetOf<String>()
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "foody:cooktimer")
            .apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
        scope.launch {
            timers.timers.collectLatest { list ->
                while (true) {
                    val now = System.currentTimeMillis()
                    list.filter { it.isDone(now) && alerted.add(it.id) }.forEach(::notifyDone)
                    val running = list.filterNot { it.isDone(now) }
                    val next = running.minOfOrNull { it.endAt } ?: break
                    notify(ONGOING_ID, ongoingNotification(running, next))
                    wakeLock.acquire(next - now + WAKE_BUFFER_MS)
                    delay(next - now)
                }
                // Nichts läuft mehr: Dienst beenden; abgelaufene Meldungen bleiben stehen, bis man sie schließt
                if (wakeLock.isHeld) wakeLock.release()
                ServiceCompat.stopForeground(this@CookTimerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Pflicht nach startForegroundService – auch wenn der Dienst schon läuft
        val running = timers.timers.value.filterNot { it.isDone(System.currentTimeMillis()) }
        ServiceCompat.startForeground(
            this, ONGOING_ID, ongoingNotification(running, running.minOfOrNull { it.endAt } ?: System.currentTimeMillis()),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        // Ohne Timer nicht neu starten, falls das System den Dienst beendet
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private fun ongoingNotification(running: List<RunningTimer>, nextEnd: Long): Notification {
        val next = running.minByOrNull { it.endAt }
        return NotificationCompat.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(resources.getQuantityString(R.plurals.timer_running_count, running.size, running.size))
            .setContentText(next?.let { "${it.recipeName}: ${it.label}" })
            .setWhen(nextEnd)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openApp())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun notifyDone(timer: RunningTimer) {
        notify(
            timer.notificationId,
            NotificationCompat.Builder(this, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(getString(R.string.timer_done, timer.label))
                .setContentText(timer.recipeName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build(),
        )
    }

    private fun notify(id: Int, notification: Notification) {
        // Ohne Benachrichtigungs-Freigabe läuft der Timer trotzdem; die App piept dann selbst, solange sie offen ist
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(id, notification)
        }
    }

    /** Öffnet Foody wie vom Startbildschirm – die laufende Aufgabe kommt nach vorn, nichts wird neu gestartet. */
    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val ONGOING_ID = 1
        private const val CHANNEL_RUNNING = "timer_running"
        private const val CHANNEL_DONE = "timer_done"
        private const val WAKE_BUFFER_MS = 60_000L

        fun intent(context: Context) = Intent(context, CookTimerService::class.java)

        private fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_RUNNING, context.getString(R.string.timer_channel_running), NotificationManager.IMPORTANCE_LOW),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, context.getString(R.string.timer_channel_done), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                    )
                    enableVibration(true)
                },
            )
        }
    }
}

package de.foody.app.timer

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Ein gestarteter Kochtimer; [endAt] als Uhrzeit, damit die Restzeit überall gleich berechnet wird. */
data class RunningTimer(val id: String, val label: String, val recipeName: String, val endAt: Long) {
    fun isDone(now: Long) = endAt <= now

    /** Feste Benachrichtigungs-ID für die „Abgelaufen“-Meldung dieses Timers. */
    val notificationId get() = id.hashCode()
}

/**
 * Laufende Kochtimer der App – unabhängig davon, welcher Bildschirm offen ist. Der [CookTimerService] hält die App
 * wach, solange ein Timer läuft, und meldet das Ende auch bei ausgeschaltetem Bildschirm.
 */
@Singleton
class CookTimerRepository @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val _timers = MutableStateFlow<List<RunningTimer>>(emptyList())
    val timers = _timers.asStateFlow()

    fun start(label: String, recipeName: String, duration: Duration, now: Long = System.currentTimeMillis()) {
        _timers.update { it + RunningTimer(UUID.randomUUID().toString(), label, recipeName, now + duration.toMillis()) }
        // Gestartet wird immer aus der sichtbaren App – dort ist ein Vordergrunddienst erlaubt
        ContextCompat.startForegroundService(context, CookTimerService.intent(context))
    }

    /** Entfernt einen Timer (laufend oder abgelaufen) samt seiner Meldung. */
    fun dismiss(id: String) {
        val timer = _timers.value.firstOrNull { it.id == id } ?: return
        _timers.update { list -> list.filterNot { it.id == id } }
        NotificationManagerCompat.from(context).cancel(timer.notificationId)
    }
}

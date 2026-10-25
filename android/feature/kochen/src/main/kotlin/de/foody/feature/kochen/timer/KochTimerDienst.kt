package de.foody.feature.kochen.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hält laufende Kochtimer am Leben, auch bei ausgeschaltetem Bildschirm oder wenn Foody im Hintergrund ist.
 *
 * Bewusst ein Vordergrunddienst mit Teil-Wakelock statt exakter Alarme (wie v1): exakte Alarme verlangen seit
 * Android 14 eine Freigabe in den Einstellungen, ungenaue kommen im Doze-Modus minutenlang zu spät. Der Wakelock gilt
 * nur bis kurz nach dem nächsten Ende; was abgelaufen ist, wird einmal gemeldet und im Speicher vermerkt.
 */
@AndroidEntryPoint
class KochTimerDienst : Service() {
    @Inject lateinit var speicher: KochTimerSpeicher

    private val bereich = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var schleife: Job? = null
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "foody:kochtimer")
            .apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TimerBenachrichtigung.kanaeleAnlegen(this)
        vordergrund()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Pflicht nach jedem startForegroundService – auch wenn der Dienst schon läuft
        vordergrund()
        // Die Schleife erst danach: sie kann sofort beenden, wenn nur noch Abgelaufenes zu melden ist – ohne
        // vorheriges startForeground beendet Android dann die ganze App
        if (schleife == null) schleife = bereich.launch { zaehlen() }
        // Ohne Timer nicht neu starten; laufende nimmt die App beim nächsten Öffnen wieder auf
        return START_NOT_STICKY
    }

    private suspend fun zaehlen() = speicher.timer.collectLatest { liste ->
        while (true) {
            val jetzt = speicher.jetzt()
            val plan = zeitplan(liste, jetzt)
            plan.neuAbgelaufen.forEach {
                TimerBenachrichtigung.ende(this@KochTimerDienst, it)
                speicher.gemeldet(it.id)
            }
            val naechstes = plan.naechstesEnde ?: break
            TimerBenachrichtigung.zeigen(this@KochTimerDienst, TimerBenachrichtigung.LAUFEND_ID, laufend(jetzt))
            wakeLock.acquire(naechstes - jetzt + PUFFER_MS)
            delay(naechstes - jetzt)
        }
        // Nichts läuft mehr: beenden; Meldungen über abgelaufene Timer bleiben stehen, bis man sie schließt
        beenden()
    }

    private fun vordergrund() = ServiceCompat.startForeground(
        this, TimerBenachrichtigung.LAUFEND_ID, laufend(speicher.jetzt()), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
    )

    override fun onDestroy() {
        bereich.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private fun laufend(jetzt: Long) = speicher.timer.value.filterNot { it.abgelaufen(jetzt) }.let { l ->
        TimerBenachrichtigung.laufend(this, l, l.minOfOrNull { it.endeUm } ?: jetzt)
    }

    private fun beenden() {
        if (wakeLock.isHeld) wakeLock.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val PUFFER_MS = 2_000L

        /** Startet den Dienst, wenn ein Timer läuft oder ein abgelaufener noch nicht gemeldet ist (nur im Vordergrund aufrufen). */
        fun wennNoetigStarten(context: Context, speicher: KochTimerSpeicher) {
            if (speicher.brauchtDienst()) ContextCompat.startForegroundService(context, Intent(context, KochTimerDienst::class.java))
        }
    }
}

package de.foody.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.OutboxStand
import de.foody.core.data.sync.SyncSpeicher
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Wann der Sync läuft: als einmaliger WorkManager-Auftrag [AUFTRAG] mit Netz-Bedingung ([SyncAuftrag]); kein periodischer
 * Auftrag. Auslöser:
 * - App kommt in den Vordergrund ([appImVordergrund]) und „Jetzt synchronisieren“ ([jetzt]): sofort;
 * - die Outbox wächst (lokale Änderung): entprellt um [ENTPRELLUNG_SEKUNDEN] – jede weitere Änderung ersetzt den
 *   wartenden Auftrag (`REPLACE`).
 * Nur solange die App offen ist ([appOffen]). Geht sie in den Hintergrund ([appImHintergrund]), wird eine noch
 * wartende Änderung einmal sofort gesendet, danach läuft nichts mehr: keine Wiederholung, kein Warten auf Netz.
 * Inaktiver Sync plant nichts. Läuft gerade ein [SyncAuftrag], wird er nicht ersetzt (das bräche ihn ab): Der Wunsch
 * wird vermerkt und der Auftrag plant am Ende einen weiteren hinter sich ([nachLauf]). Hält nur jemand anderes die
 * `laufSperre` (Trennen, Ersetzen), wird normal eingeplant; der Auftrag wartet dann auf die Sperre.
 */
@Singleton
class SyncAusloeser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speicher: SyncSpeicher,
    private val db: FoodyDatenbank,
    @SyncBereich private val bereich: CoroutineScope,
) {
    private val workManager get() = WorkManager.getInstance(context)
    private val netz = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Laufende [SyncAuftrag]e (zwischen [auftragBeginnt] und [nachLauf]). */
    private val laufend = AtomicInteger()

    /** Während eines Laufs kam ein Auslöser: danach erneut. */
    private val nachLaufNoetig = AtomicBoolean(false)
    private var beobachtung: Job? = null

    /** Die App ist offen (zwischen [appImVordergrund] und [appImHintergrund]); sonst plant der Auslöser nichts. */
    @Volatile
    internal var appOffen = false

    /** Für Tests: Während eines Auftrags kam ein Auslöser, der nach dem Lauf nachgeholt wird. */
    internal val nachLaufVermerkt: Boolean get() = nachLaufNoetig.get()

    /** Für Tests: Zahl der verarbeiteten Outbox-Stände der Beobachtung. */
    internal val outboxStaendeGesehen = AtomicInteger()

    /** Beim App-Start: die Outbox beobachten. */
    fun beimStart() {
        outboxBeobachten()
    }

    /** Die App kommt in den Vordergrund: Änderungen der anderen Geräte holen. */
    fun appImVordergrund(): Job {
        appOffen = true
        return bereich.launch { anstossen() }
    }

    /**
     * Die App geht in den Hintergrund: Ein Auftrag, der noch auf seine Entprellung oder auf Netz wartet, läuft sofort
     * und einmalig, wenn Änderungen offen sind, sonst wird er abgemeldet. Ein laufender Auftrag endet von selbst und
     * plant nichts nach.
     */
    fun appImHintergrund(): Job {
        appOffen = false
        nachLaufNoetig.set(false)
        return bereich.launch {
            if (laufend.get() > 0) return@launch
            if (speicher.zustand().aktiv && db.syncDao().outboxStand().first().anzahl > 0) {
                einreihen(ExistingWorkPolicy.REPLACE, verzoegerungSekunden = 0, mitNetz = false)
            } else {
                workManager.cancelUniqueWork(AUFTRAG)
            }
        }
    }

    /** „Jetzt synchronisieren“. */
    fun jetzt(): Job = bereich.launch { anstossen() }

    /** Plant einen Lauf ohne Verzögerung, falls der Sync aktiv ist. */
    internal suspend fun anstossen() {
        if (speicher.zustand().aktiv) planen(verzoegerungSekunden = 0)
    }

    /** Bricht wartende und laufende Aufträge ab (Trennen). */
    fun abbrechen() {
        nachLaufNoetig.set(false)
        workManager.cancelUniqueWork(AUFTRAG)
    }

    /**
     * Beobachtet die Outbox und plant bei Zuwachs entprellt einen Lauf (nur bei aktivem Sync). Zuwachs: mehr Einträge
     * oder ein jüngeres `queuedAt` (erneute Änderung eines schon vorgemerkten Datensatzes). Ein Rückgang (Senden) löst
     * nichts aus, sonst gäbe es eine Schleife. Der erste Wert zählt als Zuwachs gegenüber der leeren Outbox: Was beim
     * Start noch offen ist, geht zeitnah hinaus. Ein zweiter Aufruf tut nichts.
     */
    @Synchronized
    private fun outboxBeobachten() {
        if (beobachtung?.isActive == true) return
        beobachtung = bereich.launch {
            var vorher = OutboxStand(0, null)
            db.syncDao().outboxStand().distinctUntilChanged().collect { stand ->
                val gewachsen = stand.anzahl > vorher.anzahl || (stand.neuester ?: 0) > (vorher.neuester ?: 0)
                vorher = stand
                if (gewachsen && speicher.zustand().aktiv) planen(ENTPRELLUNG_SEKUNDEN)
                outboxStaendeGesehen.incrementAndGet()
            }
        }
    }

    private fun planen(verzoegerungSekunden: Long) {
        if (!appOffen) return
        // Ein laufender Auftrag würde durch REPLACE abgebrochen; er holt den Wunsch in nachLauf nach.
        if (laufend.get() > 0) {
            nachLaufNoetig.set(true)
            return
        }
        einreihen(ExistingWorkPolicy.REPLACE, verzoegerungSekunden)
    }

    private fun einreihen(regel: ExistingWorkPolicy, verzoegerungSekunden: Long, mitNetz: Boolean = true) {
        val auftrag = OneTimeWorkRequestBuilder<SyncAuftrag>()
            .setConstraints(if (mitNetz) netz else Constraints.NONE)
            .setInitialDelay(verzoegerungSekunden, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SEKUNDEN, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(AUFTRAG, regel, auftrag)
    }

    /** Ein [SyncAuftrag] beginnt; Auslöser bis [nachLauf] werden vermerkt statt ihn zu ersetzen. */
    internal fun auftragBeginnt() {
        laufend.incrementAndGet()
    }

    /**
     * Ein [SyncAuftrag] ist fertig: erneut einplanen (hinter dem eigenen Auftrag, `APPEND_OR_REPLACE`), wenn während des
     * Laufs ein Auslöser kam oder der Lauf etwas gesendet hat und die Outbox trotzdem nicht leer ist (mehr als ein Lauf
     * an Paketen). Ohne gesendete Datensätze nicht: dauerhaft zurückgehaltene Einträge planen sonst endlos.
     */
    internal suspend fun nachLauf(ergebnis: SyncErgebnis?) {
        laufend.decrementAndGet()
        val ausgeloest = nachLaufNoetig.getAndSet(false)
        if (!appOffen || !speicher.zustand().aktiv) return
        val mehr = ergebnis is SyncErgebnis.Fertig && ergebnis.gesendet > 0 && db.syncDao().outboxStand().first().anzahl > 0
        if (ausgeloest || mehr) einreihen(ExistingWorkPolicy.APPEND_OR_REPLACE, ENTPRELLUNG_SEKUNDEN)
    }

    companion object {
        /** Eindeutiger Name des einmaligen Sync-Auftrags der V3. */
        const val AUFTRAG = "foody-sync-v3"

        /** Entprellung nach lokalen Änderungen. */
        const val ENTPRELLUNG_SEKUNDEN = 5L
        private const val BACKOFF_SEKUNDEN = 30L
    }
}

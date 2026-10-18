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
 * Inaktiver Sync plant nichts. Läuft gerade ein Auftrag, wird er nicht ersetzt (das bräche ihn ab): Der Wunsch wird
 * vermerkt und der Auftrag plant am Ende einen weiteren hinter sich ([nachLauf]).
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

    /** Beim App-Start: den periodischen Auftrag der v1 abmelden und die Outbox beobachten. */
    fun beimStart() {
        workManager.cancelUniqueWork(ALTER_AUFTRAG)
        outboxBeobachten()
    }

    /** Die App kommt in den Vordergrund: Änderungen der anderen Geräte holen. */
    fun appImVordergrund(): Job = bereich.launch { anstossen() }

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
     * Beobachtet die Outbox und plant bei Zuwachs entprellt einen Lauf (nur bei aktivem Sync). Ein Rückgang (Senden)
     * löst nichts aus, sonst gäbe es eine Schleife. Der erste Wert zählt als Zuwachs gegenüber 0: Was beim Start noch
     * offen ist, geht zeitnah hinaus. Ein zweiter Aufruf tut nichts.
     */
    @Synchronized
    private fun outboxBeobachten() {
        if (beobachtung?.isActive == true) return
        beobachtung = bereich.launch {
            var vorher = 0
            db.syncDao().outboxAnzahl().distinctUntilChanged().collect { anzahl ->
                val gewachsen = anzahl > vorher
                vorher = anzahl
                if (gewachsen && speicher.zustand().aktiv) planen(ENTPRELLUNG_SEKUNDEN)
            }
        }
    }

    private fun planen(verzoegerungSekunden: Long) {
        // Ein laufender Auftrag (oder ein Lauf, der die Sperre hält) würde durch REPLACE abgebrochen.
        if (laufend.get() > 0 || speicher.laufSperre.isLocked) {
            nachLaufNoetig.set(true)
            return
        }
        einreihen(ExistingWorkPolicy.REPLACE, verzoegerungSekunden)
    }

    private fun einreihen(regel: ExistingWorkPolicy, verzoegerungSekunden: Long) {
        val auftrag = OneTimeWorkRequestBuilder<SyncAuftrag>()
            .setConstraints(netz)
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
        if (!speicher.zustand().aktiv) return
        val mehr = ergebnis is SyncErgebnis.Fertig && ergebnis.gesendet > 0 && db.syncDao().outboxAnzahl().first() > 0
        if (ausgeloest || mehr) einreihen(ExistingWorkPolicy.APPEND_OR_REPLACE, ENTPRELLUNG_SEKUNDEN)
    }

    companion object {
        /** Eindeutiger Name des einmaligen Sync-Auftrags der V3. */
        const val AUFTRAG = "foody-sync-v3"

        /** Periodischer Auftrag der v1; wird beim Start abgemeldet. */
        const val ALTER_AUFTRAG = "foody-sync-periodic"

        /** Entprellung nach lokalen Änderungen. */
        const val ENTPRELLUNG_SEKUNDEN = 5L
        private const val BACKOFF_SEKUNDEN = 30L
    }
}

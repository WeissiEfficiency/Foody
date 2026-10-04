package de.foody.app.sync

import androidx.room.withTransaction
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.SyncOutboxEntity
import de.foody.app.data.db.SyncProblemEntity
import de.foody.app.data.db.SyncRecordRevEntity
import de.foody.app.data.db.SyncStateEntity
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.PayloadValidator
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.PushResult
import de.foody.sync.protocol.PushStatus
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Ergebnis eines Sync-Laufs. */
sealed interface SyncOutcome {
    data class Success(val pushed: Int, val pulled: Int, val problems: Int) : SyncOutcome
    data object Unauthorized : SyncOutcome
    data class ProtocolMismatch(val serverTooOld: Boolean) : SyncOutcome
    data object NoHousehold : SyncOutcome
    data class Failed(val transient: Boolean, val message: String) : SyncOutcome
}

/**
 * Ein vollständiger Sync-Lauf: Push (Outbox → Server), Ergebnisse verarbeiten, Pull (Server → lokal) und anwenden;
 * bei `410` (Cursor abgelaufen) ein Voll-Abgleich. Schreibt `lastSyncAt`/`lastError` in `sync_state`.
 * In `lastError` stehen nur feste Kennungen oder Meldungen ohne Token und ohne Antwort-Bodies.
 */
class SyncEngine(
    private val db: FoodyDatabase,
    private val store: SyncLocalStore,
    private val applier: SyncApplier,
    private val api: SyncApi,
    private val clock: Clock,
) {
    private val dao get() = db.syncDao()
    private val mutex = Mutex()

    suspend fun run(): SyncOutcome = mutex.withLock {
        val state = dao.getState()
        if (state == null || !state.active) return@withLock SyncOutcome.Success(0, 0, 0)
        try {
            val pushed = pushAll()
            val pulled = pullAll()
            val problems = dao.problems().size
            setError(null)
            SyncOutcome.Success(pushed, pulled, problems)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SyncApiException.Unauthorized) {
            fail("unauthorized", SyncOutcome.Unauthorized)
        } catch (e: SyncApiException.ProtocolMismatch) {
            val tooOld = e.code == ErrorCode.SERVER_TOO_OLD
            fail(if (tooOld) "protocol_server_too_old" else "protocol_too_old", SyncOutcome.ProtocolMismatch(tooOld))
        } catch (e: SyncApiException.NoHousehold) {
            fail("no_household", SyncOutcome.NoHousehold)
        } catch (e: SyncApiException.Transient) {
            failed(true, e.message)
        } catch (e: SyncApiException.Throttled) {
            failed(true, e.message)
        } catch (e: SyncApiException) {
            failed(false, e.message)
        } catch (e: Exception) {
            // Nur der Klassenname: Meldungen fremder Ausnahmen könnten Daten enthalten.
            failed(false, e.javaClass.simpleName)
        }
    }

    private suspend fun fail(error: String, outcome: SyncOutcome): SyncOutcome {
        setError(error)
        return outcome
    }

    private suspend fun failed(transient: Boolean, message: String?): SyncOutcome {
        val text = message ?: "unbekannt"
        setError((if (transient) "transient: " else "failed: ") + text)
        return SyncOutcome.Failed(transient, text)
    }

    private suspend fun setError(error: String?) {
        db.withTransaction { dao.upsertState((dao.getState() ?: SyncStateEntity()).copy(lastError = error)) }
    }

    // ---- Push ----------------------------------------------------------------------------------------------

    /** Sendet die Outbox in Batches; liefert die Zahl der angenommenen/zusammengeführten Datensätze. */
    private suspend fun pushAll(): Int {
        var pushed = 0
        // Fassungen, die in diesem Lauf schon gesendet wurden (z. B. abgelehnt und weiter vorgemerkt): nicht erneut.
        val sent = HashSet<Triple<RecordType, String, Long>>()
        repeat(MAX_PUSH_BATCHES) {
            val batch = store.pendingBatch(Protocol.MAX_PUSH_RECORDS, sent)
            if (batch.isEmpty()) return pushed
            val response = api.push(batch.map { it.record })
            sent += batch.map { it.key }
            val byKey = batch.associateBy { it.record.type to it.record.id }
            for (result in response.results) {
                val pending = byKey[result.type to result.id] ?: continue
                if (handleResult(pending, result)) pushed++
            }
        }
        return pushed
    }

    /** Verarbeitet ein Push-Ergebnis; `true`, wenn der Server den Datensatz angenommen hat. */
    private suspend fun handleResult(pending: PendingRecord, result: PushResult): Boolean {
        val type = pending.record.type.wire
        val id = pending.record.id
        var current: SyncRecord? = null
        db.withTransaction {
            when (result.status) {
                PushStatus.ACCEPTED -> {
                    result.rev?.let { dao.setRev(SyncRecordRevEntity(type, id, it)) }
                    dao.clearProblem(type, id)
                    dequeueIfUnchanged(pending)
                }
                PushStatus.MERGED -> {
                    dao.clearProblem(type, id)
                    // Wurde der Eintrag zwischenzeitlich bearbeitet, gewinnt die lokale Änderung (geht im nächsten Lauf
                    // raus). Dann bleibt die alte Revision, damit der Server beim nächsten Push erneut zusammenführt
                    // statt den Merge-Stand zu überschreiben.
                    if (dequeueIfUnchanged(pending)) {
                        // In einen anderen Datensatz zusammengeführt: die Revision gehört nicht zur lokalen ID.
                        if (result.canonicalId == null || result.canonicalId == id) {
                            result.rev?.let { dao.setRev(SyncRecordRevEntity(type, id, it)) }
                        }
                        current = result.current
                    }
                }
                PushStatus.REJECTED -> {
                    dao.addProblem(SyncProblemEntity(type, id, rejectCode(result.code), clock.millis()))
                    // Fehlende Verweise erledigen sich evtl. durch den nächsten Pull: Eintrag bleibt.
                    if (result.code != ErrorCode.MISSING_REFERENCE) dequeueIfUnchanged(pending)
                }
            }
        }
        // Der Cursor gehört dem Pull; `current` ist nur ein einzelner Datensatz.
        current?.let { applier.apply(listOf(it), 0, updateCursor = false) }
        return result.status != PushStatus.REJECTED
    }

    /** Entfernt den Outbox-Eintrag, wenn er seit dem Bauen des Batches nicht erneut vorgemerkt wurde. */
    private suspend fun dequeueIfUnchanged(pending: PendingRecord): Boolean {
        val type = pending.record.type.wire
        val id = pending.record.id
        if (dao.queuedAtOf(type, id) != pending.queuedAt) return false
        dao.dequeue(type, id)
        return true
    }

    private fun rejectCode(code: ErrorCode?): String =
        code?.let { ErrorCode.serializer().descriptor.getElementName(it.ordinal) } ?: "rejected"

    // ---- Pull ----------------------------------------------------------------------------------------------

    /** Holt alle Seiten ab dem Cursor und wendet sie einmal an; bei `410` Voll-Abgleich. Liefert die Zahl der Datensätze. */
    private suspend fun pullAll(): Int {
        val cursor = dao.getState()?.cursor ?: 0L
        return try {
            val (records, next) = pullPages(cursor)
            applier.apply(records, next)
            records.size
        } catch (_: SyncApiException.CursorExpired) {
            fullResync()
        }
    }

    private suspend fun pullPages(from: Long): Pair<List<SyncRecord>, Long> {
        val all = ArrayList<SyncRecord>()
        var since = from
        while (true) {
            val page = api.pull(since, PULL_LIMIT)
            all += page.records
            since = page.nextCursor
            if (!page.hasMore) return all to since
        }
    }

    /**
     * Voll-Abgleich: alles ab 0 holen und anwenden, danach lokale Wurzeldatensätze löschen, die der Server nicht
     * (mehr) kennt und die keine offene lokale Änderung haben. Gelöscht wird mit `applyingRemote = 1` (keine
     * Outbox-Einträge), Kinder zuerst; was noch gebraucht wird (Zutat in Rezeptzeilen, Kaskade über offene
     * Änderungen), bleibt stehen.
     */
    private suspend fun fullResync(): Int {
        val (records, next) = pullPages(0)
        // Cursor erst nach dem Löschen setzen: Bricht es ab, wird der Voll-Abgleich wiederholt.
        applier.apply(records, next, updateCursor = false)
        val live = records.filter { !it.deleted }
        val remote = live.map { it.type to it.id }.toSet()
        // Vom Server gehaltene Datensätze, die auf andere verweisen: deren Ziele dürfen nicht weggeräumt werden.
        val remoteRefs = live.flatMap { PayloadValidator.references(it) }.toSet()
        for (type in DELETE_ORDER) {
            val ids = when (type) {
                RecordType.INGREDIENT -> dao.ingredientIds()
                RecordType.RECIPE -> dao.recipeIds()
                RecordType.MEAL_SLOT -> dao.mealSlotIds()
                RecordType.PANTRY_ITEM -> dao.pantryItemIds()
                RecordType.SHOPPING_LIST -> dao.shoppingListIds()
                RecordType.SHOPPING_ITEM -> dao.shoppingItemIds()
            }
            for (id in ids) {
                if ((type to id) !in remote) deleteStale(type, id, remoteRefs)
            }
        }
        db.withTransaction {
            dao.upsertState((dao.getState() ?: SyncStateEntity()).copy(cursor = next, lastSyncAt = clock.millis()))
        }
        return records.size
    }

    private suspend fun deleteStale(type: RecordType, id: String, remoteRefs: Set<Pair<RecordType, String>>) {
        db.withTransaction {
            if (dao.isQueued(type.wire, id)) return@withTransaction
            if ((type to id) in remoteRefs || isStillNeeded(type, id)) {
                // Bleibt stehen, der Server kennt ihn aber nicht: erneut senden, damit beide Seiten konvergieren.
                dao.enqueue(SyncOutboxEntity(type.wire, id, deleted = false, queuedAt = clock.millis()))
                return@withTransaction
            }
            dao.setApplyingRemote(true)
            try {
                when (type) {
                    RecordType.INGREDIENT -> db.ingredientDao().delete(id)
                    RecordType.RECIPE -> db.recipeDao().delete(id)
                    RecordType.MEAL_SLOT -> db.mealPlanDao().delete(id)
                    RecordType.PANTRY_ITEM -> db.pantryDao().delete(id)
                    RecordType.SHOPPING_LIST -> db.shoppingDao().deleteList(id)
                    RecordType.SHOPPING_ITEM -> db.shoppingDao().deleteItem(id)
                }
            } finally {
                dao.setApplyingRemote(false)
            }
            dao.clearProblem(type.wire, id)
        }
    }

    /** Würde das Löschen noch gebrauchte Daten reißen (RESTRICT oder Kaskade über offene lokale Änderungen)? */
    private suspend fun isStillNeeded(type: RecordType, id: String): Boolean = when (type) {
        RecordType.INGREDIENT -> db.ingredientDao().usageCount(id) > 0 || dao.hasQueuedPantryFor(id) ||
            dao.hasQueuedShoppingItemsFor(id)
        RecordType.RECIPE -> dao.hasQueuedSlotsFor(id)
        RecordType.SHOPPING_LIST -> dao.hasQueuedItemsFor(id)
        else -> false
    }

    private companion object {
        const val MAX_PUSH_BATCHES = 20
        const val PULL_LIMIT = 500

        /** Kinder vor Eltern (umgekehrte Abhängigkeitsreihenfolge). */
        val DELETE_ORDER = listOf(
            RecordType.SHOPPING_ITEM, RecordType.SHOPPING_LIST, RecordType.PANTRY_ITEM,
            RecordType.MEAL_SLOT, RecordType.RECIPE, RecordType.INGREDIENT,
        )
    }
}

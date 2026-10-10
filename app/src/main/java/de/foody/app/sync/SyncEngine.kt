package de.foody.app.sync

import androidx.room.withTransaction
import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.SyncOutboxEntity
import de.foody.app.data.db.SyncPhotoLocalEntity
import de.foody.app.data.db.SyncProblemEntity
import de.foody.app.data.db.SyncRecordRevEntity
import de.foody.app.data.db.SyncStateEntity
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.PayloadValidator
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.PushResult
import de.foody.sync.protocol.PhotoHash
import de.foody.sync.protocol.PushStatus
import de.foody.sync.protocol.RecipePayload
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import de.foody.sync.protocol.decode
import java.io.File
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException

/** Ergebnis eines Sync-Laufs. */
sealed interface SyncOutcome {
    data class Success(val pushed: Int, val pulled: Int, val problems: Int) : SyncOutcome
    data object Unauthorized : SyncOutcome
    data class ProtocolMismatch(val serverTooOld: Boolean) : SyncOutcome
    data object NoHousehold : SyncOutcome
    data class Failed(val transient: Boolean, val message: String) : SyncOutcome
}

/** Der Server liefert `hasMore`, aber einen Cursor, der nicht vorrückt. */
private class CursorStuckException : Exception("cursor_stuck")

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
    private val photoIndex: PhotoIndex,
    private val photoStore: RecipePhotoStore,
) {
    private val dao get() = db.syncDao()

    /** `true`, solange ein Lauf die Sperre hält ([SyncLocalStore.runLock]); billig, ohne Suspend. */
    val isRunning: Boolean get() = store.runLock.isLocked

    suspend fun run(): SyncOutcome = store.runLock.withLock {
        val state = dao.getState()
        if (state == null || !state.active) return@withLock SyncOutcome.Success(0, 0, 0)
        try {
            val pushed = pushAll()
            val pulled = pullAll()
            downloadWantedPhotos()
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
        } catch (_: CursorStuckException) {
            failed(true, "cursor_stuck")
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
            sent += batch.map { it.key }
            // Fotos vor den Rezepten hochladen: Kennt der Server das Foto noch nicht, bliebe es beim Empfänger leer.
            val ready = uploadMissingPhotos(batch)
            if (ready.isNotEmpty()) pushed += pushBatch(ready)
        }
        return pushed
    }

    /**
     * Sendet [batch]; antwortet der Server mit `413`, wird er halbiert und beide Hälften nacheinander gesendet
     * (Reihenfolge Eltern vor Kindern bleibt erhalten). Wird ein einzelner Datensatz abgelehnt, bekommt er das
     * Problem `too_large` und verlässt die Outbox. Liefert die Zahl der angenommenen Datensätze.
     */
    private suspend fun pushBatch(batch: List<PendingRecord>): Int {
        val response = try {
            api.push(batch.map { it.record })
        } catch (_: SyncApiException.TooLarge) {
            if (batch.size > 1) {
                val half = batch.size / 2
                return pushBatch(batch.subList(0, half)) + pushBatch(batch.subList(half, batch.size))
            }
            val pending = batch.single()
            db.withTransaction {
                dao.addProblem(SyncProblemEntity(pending.record.type.wire, pending.record.id, "too_large", clock.millis()))
                dequeueIfUnchanged(pending)
            }
            return 0
        }
        val byKey = batch.associateBy { it.record.type to it.record.id }
        var pushed = 0
        for (result in response.results) {
            val pending = byKey[result.type to result.id] ?: continue
            if (handleResult(pending, result)) pushed++
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
                    pending.photoProblem?.let { dao.addProblem(SyncProblemEntity(type, id, it, clock.millis())) }
                    dequeueIfUnchanged(pending)
                }
                PushStatus.MERGED -> {
                    dao.clearProblem(type, id)
                    pending.photoProblem?.let { dao.addProblem(SyncProblemEntity(type, id, it, clock.millis())) }
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
                    // Fehlende Verweise erledigen sich durch den nächsten Pull oder durch das Nachreichen: Eintrag bleibt.
                    if (result.code == ErrorCode.MISSING_REFERENCE) queueLocalParents(pending.record) else dequeueIfUnchanged(pending)
                }
            }
        }
        // Der Cursor gehört dem Pull; `current` ist nur ein einzelner Datensatz.
        current?.let { applier.apply(listOf(it), 0, updateCursor = false, skipKnown = false) }
        return result.status != PushStatus.REJECTED
    }

    /**
     * Merkt Verweisziele vor, die es lokal gibt, die aber nicht in der Outbox stehen: Der Server kennt sie offenbar nicht
     * (etwa eine Startzutat, die nach „nur herunterladen“ nie geändert und daher nie gesendet wurde). Ohne das bliebe der
     * abgelehnte Datensatz für immer stehen; so geht das Ziel im nächsten Batch raus und der Datensatz danach.
     */
    private suspend fun queueLocalParents(record: SyncRecord) {
        val refs = try {
            PayloadValidator.references(record)
        } catch (_: SerializationException) {
            return
        } catch (_: IllegalArgumentException) {
            return
        }
        for ((type, id) in refs) {
            if (dao.isQueued(type.wire, id) || !applier.exists(type, id)) continue
            dao.enqueue(SyncOutboxEntity(type.wire, id, deleted = false, queuedAt = clock.millis()))
        }
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

    // ---- Fotos ---------------------------------------------------------------------------------------------

    /**
     * Lädt die Fotos der Rezepte in [batch], die der Server noch nicht kennt, hoch. Ein Hash ohne lokale Datei
     * (Foto-Wunsch, noch nicht geladen) wird übersprungen – den hat der Server, von dem er stammt. Fehlt dagegen ein
     * eigenes Foto (Datei weg, geändert, zu groß), wartet das Rezept in der Outbox, statt mit unbekanntem Hash zu gehen. Lese- oder
     * Upload-Fehler (IO, 5xx) brechen den Lauf als `Transient` ab (nichts aus dem Batch verlässt die Outbox). Lehnt der
     * Server ein einzelnes Foto dauerhaft ab (4xx, 413), gehen nur die betroffenen Rezepte ohne Foto und mit dem Problem
     * `photo_unsyncable` raus ([withoutPhoto]). Liefert die Datensätze, die gesendet werden dürfen.
     */
    private suspend fun uploadMissingPhotos(batch: List<PendingRecord>): List<PendingRecord> {
        val hashes = batch.mapNotNull { photoOf(it.record) }.distinct()
        if (hashes.isEmpty()) return batch
        val refused = HashSet<String>()
        // Eigene Fotos, die jetzt nicht hochgeladen werden können: die betroffenen Rezepte warten (bleiben in der Outbox).
        val deferred = HashSet<String>()
        for (hash in api.photosMissing(hashes)) {
            val uri = photoIndex.uriFor(hash)
            val file = uri?.let { photoIndex.fileOf(it) }
            if (uri == null || file == null || file.length() > Protocol.MAX_PHOTO_BYTES) {
                // Foto-Wunsch (Hash vom Server, Datei noch nicht geladen): der Server hat ihn. Sonst ein eigenes Foto,
                // das fehlt, geändert wurde oder zu groß ist (413): Rezept nicht mit einem unbekannten Hash senden.
                for (pending in batch) {
                    if (photoOf(pending.record) == hash && dao.photoWanted(pending.record.id)?.sha256 != hash) {
                        deferred += pending.record.id
                    }
                }
                continue
            }
            val bytes = readFile(file)
            // Die Datei hat sich seit dem Hashen geändert: Cache-Eintrag verwerfen, im nächsten Lauf neu hashen.
            if (PhotoHash.of(bytes) != hash) {
                dao.deletePhotoLocal(uri)
                throw SyncApiException.Transient(0, null)
            }
            try {
                api.uploadPhoto(hash, bytes)
            } catch (_: SyncApiException.ClientError) {
                refused += hash
            } catch (_: SyncApiException.TooLarge) {
                refused += hash
            }
        }
        if (refused.isEmpty() && deferred.isEmpty()) return batch
        return batch.filter { it.record.id !in deferred }.map { pending ->
            if (photoOf(pending.record) in refused) withoutPhoto(pending) else pending
        }
    }

    /**
     * Das Rezept ohne Foto und mit dem Problem `photo_unsyncable` – wie ein lokal als nicht übertragbar erkanntes Foto.
     * Würde es stattdessen in der Outbox warten, lüde jeder Lauf dieselben Bytes erneut hoch und das Rezept käme nie an.
     * Eine spätere Änderung des Rezepts versucht das Foto erneut.
     */
    private fun withoutPhoto(pending: PendingRecord): PendingRecord {
        val payload = RecordType.RECIPE.decode(pending.record.payload!!) as RecipePayload
        val record = pending.record.copy(payload = SyncMapper.toJson(payload.copy(photo = null)))
        return pending.copy(record = record, photoProblem = SyncLocalStore.PHOTO_UNSYNCABLE)
    }

    private fun photoOf(record: SyncRecord): String? {
        if (record.type != RecordType.RECIPE || record.deleted) return null
        val payload = record.payload ?: return null
        return try {
            (record.type.decode(payload) as? RecipePayload)?.photo
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private suspend fun readFile(file: File): ByteArray = try {
        withContext(Dispatchers.IO) { file.readBytes() }
    } catch (e: IOException) {
        throw SyncApiException.Transient(0, null, e)
    }

    /**
     * Lädt die Fotos nach, die Server-Rezepte brauchen (`sync_photo_wanted`). 404 → der Wunsch bleibt für den nächsten
     * Lauf. Bytes mit falschem Hash werden verworfen: Problem `photo_mismatch` am Rezept, der Wunsch bleibt (der Push
     * schickt so weiter den Server-Hash und überschreibt dort nichts). Solange das Problem besteht, wird nicht erneut
     * geladen; ein neuer Server-Stand des Rezepts löscht es (`SyncApplier`) und stößt den Abruf wieder an. Folge: das
     * Problem hält ein auf dem Server gelöschtes Rezept im Voll-Abgleich lokal (Daten bleiben erhalten). Der Link wird nur
     * gesetzt, wenn der Wunsch unverändert noch besteht (ein lokal neu gewähltes Foto löscht ihn).
     */
    private suspend fun downloadWantedPhotos() {
        val mismatched = dao.problems().filter { it.type == RecordType.RECIPE.wire && it.code == "photo_mismatch" }
            .map { it.recordId }.toSet()
        var transient: SyncApiException.Transient? = null
        for (wish in dao.photosWanted()) {
            if (db.recipeDao().get(wish.recipeId) == null) {
                dao.deletePhotoWanted(wish.recipeId) // Waise (etwa nach dem Voll-Abgleich)
                continue
            }
            if (mismatched.contains(wish.recipeId)) continue
            val uri = try {
                photoIndex.uriFor(wish.sha256) ?: downloadPhoto(wish.recipeId, wish.sha256)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SyncApiException.Transient) {
                transient = e // später erneut versuchen, die übrigen Wünsche trotzdem laden
                continue
            } catch (_: SyncApiException.ClientError) {
                continue // dieser Wunsch bleibt, blockiert aber die anderen nicht
            } catch (_: SyncApiException.TooLarge) {
                continue // ebenso: Antwort über MAX_PHOTO_BYTES (etwa falsche Länge eines Proxys)
            } ?: continue
            db.withTransaction {
                if (dao.photoWanted(wish.recipeId)?.sha256 != wish.sha256) return@withTransaction
                dao.setApplyingRemote(true)
                try {
                    dao.setRecipeImage(wish.recipeId, uri)
                } finally {
                    dao.setApplyingRemote(false)
                }
                dao.deletePhotoWanted(wish.recipeId)
            }
        }
        // Nach dem Durchlauf melden, damit WorkManager es erneut versucht.
        transient?.let { throw it }
    }

    /** Lädt ein Foto und legt es als eigene Datei ab; liefert den Link oder `null` (nicht auf dem Server / Hash falsch). */
    private suspend fun downloadPhoto(recipeId: String, sha256: String): String? {
        val bytes = api.downloadPhoto(sha256) ?: return null
        if (PhotoHash.of(bytes) != sha256) {
            db.withTransaction {
                dao.addProblem(SyncProblemEntity(RecordType.RECIPE.wire, recipeId, "photo_mismatch", clock.millis()))
            }
            return null
        }
        val file = photoStore.newPhotoFile()
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            withContext(Dispatchers.IO) {
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(file)) throw IOException("rename")
            }
        } catch (e: IOException) {
            tmp.delete()
            throw SyncApiException.Transient(0, null, e)
        }
        val uri = photoStore.storedUri(file)
        dao.upsertPhotoLocal(SyncPhotoLocalEntity(uri, sha256, file.length(), file.lastModified()))
        return uri
    }

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
            // Ein Cursor, der trotz weiterer Seiten nicht vorrückt, würde endlos laufen.
            if (page.hasMore && page.nextCursor <= since) throw CursorStuckException()
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
        // Unlesbare Datensätze (z. B. neueres Schema) hat der Applier schon als Problem vermerkt; hier überspringen.
        val remoteRefs = live.flatMap {
            try {
                PayloadValidator.references(it)
            } catch (_: SerializationException) {
                emptyList()
            } catch (_: IllegalArgumentException) {
                emptyList()
            }
        }.toSet()
        for (type in DELETE_ORDER) {
            val ids = when (type) {
                RecordType.INGREDIENT -> dao.ingredientIds()
                RecordType.RECIPE -> dao.recipeIds()
                RecordType.MEAL_SLOT -> dao.mealSlotIds()
                RecordType.PANTRY_ITEM -> dao.pantryItemIds()
                RecordType.SHOPPING_LIST -> dao.shoppingListIds()
                RecordType.SHOPPING_ITEM -> dao.shoppingItemIds()
                RecordType.TAGEBUCH_EINTRAG -> dao.tagebuchIds()
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
            // Vom Server abgelehnt (Problem vermerkt): der lokale Stand ist die einzige Kopie, nicht löschen.
            if (dao.hasProblem(type.wire, id)) return@withTransaction
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
                    RecordType.TAGEBUCH_EINTRAG -> db.tagebuchDao().delete(id)
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
            RecordType.TAGEBUCH_EINTRAG, RecordType.SHOPPING_ITEM, RecordType.SHOPPING_LIST, RecordType.PANTRY_ITEM,
            RecordType.MEAL_SLOT, RecordType.RECIPE, RecordType.INGREDIENT,
        )
    }
}

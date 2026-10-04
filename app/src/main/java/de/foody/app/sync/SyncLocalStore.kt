package de.foody.app.sync

import androidx.room.withTransaction
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.SyncOutboxEntity
import de.foody.app.data.db.SyncStateEntity
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ein zu sendender Datensatz mit dem `queuedAt` des Outbox-Eintrags zum Zeitpunkt des Bauens. Nur wenn der Eintrag
 * nach dem Push noch dasselbe `queuedAt` hat, wurde nichts nachträglich geändert und er darf entfernt werden.
 */
data class PendingRecord(val record: SyncRecord, val queuedAt: Long) {
    /** Identität dieser Fassung des Eintrags: ein erneutes Vormerken ergibt ein anderes `queuedAt`. */
    val key: Triple<RecordType, String, Long> get() = Triple(record.type, record.id, queuedAt)
}

/**
 * Lokale Seite des Syncs: schaltet ihn ein/aus und baut aus der Outbox die Datensätze für den Push.
 * Die Outbox selbst füllen die Trigger (siehe `SyncTriggers`); das Entfernen nach erfolgreichem Push
 * ist Sache des späteren Sync-Ablaufs, [pendingRecords] verändert die Outbox nicht.
 */
@Singleton
class SyncLocalStore @Inject constructor(private val db: FoodyDatabase) {
    private val dao get() = db.syncDao()

    /**
     * Schaltet den Sync ein (Cursor zurück auf 0). Mit [uploadExisting] werden zusätzlich alle vorhandenen
     * Datensätze zum Senden vorgemerkt. Alles in einer Transaktion.
     */
    suspend fun activate(serverUrl: String, householdId: String, uploadExisting: Boolean) {
        db.withTransaction {
            val state = dao.getState() ?: SyncStateEntity()
            dao.upsertState(
                state.copy(
                    active = true,
                    serverUrl = serverUrl,
                    householdId = householdId,
                    cursor = 0,
                    lastError = null,
                ),
            )
            if (uploadExisting) enqueueAll()
        }
    }

    /** Schaltet den Sync aus und verwirft Outbox, Revisionen und Probleme. */
    suspend fun deactivate() {
        db.withTransaction {
            val state = dao.getState() ?: SyncStateEntity()
            dao.upsertState(state.copy(active = false, serverUrl = null, householdId = null, cursor = 0, lastError = null))
            dao.clearOutbox()
            dao.clearRevs()
            dao.clearProblems()
        }
    }

    /** Merkt jeden Wurzeldatensatz aller sechs Typen als lebend vor. */
    suspend fun enqueueAll() {
        dao.enqueueAllRoots(System.currentTimeMillis())
    }

    /**
     * Datensätze für den nächsten Push: lebende in [RecordType]-Reihenfolge (Abhängigkeiten zuerst), danach
     * Löschungen in umgekehrter Reihenfolge; höchstens [limit]. Eine lebend vorgemerkte Zeile, die inzwischen
     * fehlt, geht als Löschung hinaus.
     */
    suspend fun pendingRecords(limit: Int = Protocol.MAX_PUSH_RECORDS): List<SyncRecord> =
        pendingBatch(limit).map { it.record }

    /**
     * Wie [pendingRecords], mit dem zugehörigen `queuedAt` je Datensatz. Fassungen aus [exclude] (bereits in diesem
     * Lauf gesendet) werden übersprungen, damit dauerhaft vorgemerkte Einträge den Batch nicht blockieren.
     */
    suspend fun pendingBatch(
        limit: Int = Protocol.MAX_PUSH_RECORDS,
        exclude: Set<Triple<RecordType, String, Long>> = emptySet(),
    ): List<PendingRecord> = db.withTransaction {
        val types = RecordType.entries
        // Innerhalb eines Typs bleibt die Outbox-Reihenfolge (queuedAt) erhalten.
        val outbox = dao.outbox().filter { e ->
            exclude.isEmpty() || Triple(types.first { it.wire == e.type }, e.recordId, e.queuedAt) !in exclude
        }
        val byType = outbox.groupBy { e -> types.first { it.wire == e.type } }
        val live = types.flatMap { t -> byType[t].orEmpty().filter { !it.deleted }.map { t to it } }
        val deletions = types.flatMap { t -> byType[t].orEmpty().filter { it.deleted }.map { t to it } }

        val records = ArrayList<PendingRecord>()
        val vanished = ArrayList<Pair<RecordType, SyncOutboxEntity>>()
        for ((type, entry) in live) {
            if (records.size >= limit) break
            val built = build(type, entry)
            if (built != null) records += PendingRecord(built, entry.queuedAt) else vanished += type to entry
        }
        val allDeletions = (deletions + vanished).sortedWith(
            compareByDescending<Pair<RecordType, SyncOutboxEntity>> { it.first.ordinal }.thenBy { it.second.queuedAt },
        )
        for ((type, entry) in allDeletions) {
            if (records.size >= limit) break
            records += PendingRecord(
                SyncRecord(
                    id = entry.recordId,
                    type = type,
                    deleted = true,
                    updatedAt = entry.queuedAt,
                    baseRev = dao.revOf(type.wire, entry.recordId),
                ),
                entry.queuedAt,
            )
        }
        records
    }

    /** Baut den lebenden Datensatz oder `null`, wenn die Zeile nicht mehr existiert. */
    private suspend fun build(type: RecordType, e: SyncOutboxEntity): SyncRecord? {
        val id = e.recordId
        val (updatedAt, payload) = when (type) {
            RecordType.INGREDIENT -> db.ingredientDao().get(id)?.let { it.updatedAt to SyncMapper.ingredient(it) }
            RecordType.RECIPE -> db.recipeDao().get(id)?.let {
                it.updatedAt to SyncMapper.recipe(it, db.recipeDao().getIngredients(id), db.recipeDao().getSteps(id))
            }
            RecordType.MEAL_SLOT -> db.mealPlanDao().get(id)?.let { it.updatedAt to SyncMapper.mealSlot(it) }
            RecordType.PANTRY_ITEM -> db.pantryDao().get(id)?.let { it.updatedAt to SyncMapper.pantryItem(it) }
            RecordType.SHOPPING_LIST -> db.shoppingDao().getList(id)?.let { it.updatedAt to SyncMapper.shoppingList(it) }
            RecordType.SHOPPING_ITEM -> db.shoppingDao().getItem(id)?.let {
                it.updatedAt to SyncMapper.shoppingItem(it, db.shoppingDao().getSources(id))
            }
        } ?: return null
        return SyncRecord(
            id = id,
            type = type,
            deleted = false,
            updatedAt = updatedAt,
            baseRev = dao.revOf(type.wire, id),
            payload = SyncMapper.toJson(payload),
        )
    }
}

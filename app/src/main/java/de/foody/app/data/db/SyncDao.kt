package de.foody.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_state WHERE id = 1") suspend fun getState(): SyncStateEntity?
    @Upsert suspend fun upsertState(s: SyncStateEntity)
    @Query("UPDATE sync_state SET applyingRemote = :on WHERE id = 1") suspend fun setApplyingRemote(on: Boolean)

    @Query("SELECT * FROM sync_outbox ORDER BY queuedAt, type, recordId") suspend fun outbox(): List<SyncOutboxEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM sync_outbox WHERE type = :type AND recordId = :id)")
    suspend fun isQueued(type: String, id: String): Boolean
    @Upsert suspend fun enqueue(e: SyncOutboxEntity)
    @Query("DELETE FROM sync_outbox WHERE type = :type AND recordId = :id") suspend fun dequeue(type: String, id: String)
    @Query("DELETE FROM sync_outbox") suspend fun clearOutbox()

    /** Merkt alle Wurzeldatensätze (sechs Typen) als lebend vor; vorhandene Einträge werden ersetzt. */
    @Query(
        """INSERT OR REPLACE INTO sync_outbox (type, recordId, deleted, queuedAt)
           SELECT 'ingredient', id, 0, :now FROM ingredient
           UNION ALL SELECT 'recipe', id, 0, :now FROM recipe
           UNION ALL SELECT 'meal_slot', id, 0, :now FROM meal_slot
           UNION ALL SELECT 'pantry_item', id, 0, :now FROM pantry_item
           UNION ALL SELECT 'shopping_list', id, 0, :now FROM shopping_list
           UNION ALL SELECT 'shopping_item', id, 0, :now FROM shopping_item""",
    )
    suspend fun enqueueAllRoots(now: Long)

    @Query("SELECT rev FROM sync_record_rev WHERE type = :type AND recordId = :id") suspend fun revOf(type: String, id: String): Long?
    @Upsert suspend fun setRev(e: SyncRecordRevEntity)
    @Query("DELETE FROM sync_record_rev") suspend fun clearRevs()

    @Upsert suspend fun addProblem(p: SyncProblemEntity)
    @Query("SELECT * FROM sync_problem") suspend fun problems(): List<SyncProblemEntity>
    @Query("DELETE FROM sync_problem") suspend fun clearProblems()
}

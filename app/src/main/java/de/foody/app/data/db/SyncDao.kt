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

    @Query("SELECT rev FROM sync_record_rev WHERE type = :type AND recordId = :id") suspend fun revOf(type: String, id: String): Long?
    @Upsert suspend fun setRev(e: SyncRecordRevEntity)

    @Upsert suspend fun addProblem(p: SyncProblemEntity)
    @Query("SELECT * FROM sync_problem") suspend fun problems(): List<SyncProblemEntity>
}

package de.foody.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * Zugriff auf die Sync-Tabellen. Grundoperationen je Tabelle (alle, einzeln, upsert, löschen); die Abläufe des
 * Sync-Laufs (Pakete, Anwenden, Voll-Abgleich) bauen darauf auf.
 */
@Dao
interface SyncDao {
    // sync_outbox
    @Query("SELECT * FROM sync_outbox ORDER BY queuedAt, type, recordId") suspend fun outbox(): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE type = :type AND recordId = :recordId")
    suspend fun outboxEintrag(type: String, recordId: String): SyncOutboxEntity?

    @Upsert suspend fun upsertOutbox(e: SyncOutboxEntity)

    @Query("DELETE FROM sync_outbox WHERE type = :type AND recordId = :recordId")
    suspend fun loeschenOutbox(type: String, recordId: String)

    // sync_record_rev
    @Query("SELECT * FROM sync_record_rev ORDER BY type, recordId") suspend fun revisionen(): List<SyncRevisionEntity>

    @Query("SELECT * FROM sync_record_rev WHERE type = :type AND recordId = :recordId")
    suspend fun revision(type: String, recordId: String): SyncRevisionEntity?

    @Upsert suspend fun upsertRevision(e: SyncRevisionEntity)

    @Query("DELETE FROM sync_record_rev WHERE type = :type AND recordId = :recordId")
    suspend fun loeschenRevision(type: String, recordId: String)

    // sync_state (genau eine Zeile, id = 1)
    @Query("SELECT * FROM sync_state WHERE id = 1") suspend fun zustand(): SyncZustandEntity?

    @Upsert suspend fun upsertZustand(e: SyncZustandEntity)

    // sync_problem
    @Query("SELECT * FROM sync_problem ORDER BY type, recordId") suspend fun probleme(): List<SyncProblemEntity>

    @Query("SELECT * FROM sync_problem WHERE type = :type AND recordId = :recordId")
    suspend fun problem(type: String, recordId: String): SyncProblemEntity?

    @Upsert suspend fun upsertProblem(e: SyncProblemEntity)

    @Query("DELETE FROM sync_problem WHERE type = :type AND recordId = :recordId")
    suspend fun loeschenProblem(type: String, recordId: String)

    // sync_photo_local
    @Query("SELECT * FROM sync_photo_local ORDER BY uri") suspend fun fotosLokal(): List<SyncFotoLokalEntity>

    @Query("SELECT * FROM sync_photo_local WHERE uri = :uri") suspend fun fotoLokal(uri: String): SyncFotoLokalEntity?

    @Upsert suspend fun upsertFotoLokal(e: SyncFotoLokalEntity)

    @Query("DELETE FROM sync_photo_local WHERE uri = :uri") suspend fun loeschenFotoLokal(uri: String)

    // sync_photo_wanted
    @Query("SELECT * FROM sync_photo_wanted ORDER BY recipeId") suspend fun fotosGewuenscht(): List<SyncFotoGewuenschtEntity>

    @Query("SELECT * FROM sync_photo_wanted WHERE recipeId = :recipeId")
    suspend fun fotoGewuenscht(recipeId: String): SyncFotoGewuenschtEntity?

    @Upsert suspend fun upsertFotoGewuenscht(e: SyncFotoGewuenschtEntity)

    @Query("DELETE FROM sync_photo_wanted WHERE recipeId = :recipeId") suspend fun loeschenFotoGewuenscht(recipeId: String)
}

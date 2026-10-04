package de.foody.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_state WHERE id = 1") suspend fun getState(): SyncStateEntity?
    @Upsert suspend fun upsertState(s: SyncStateEntity)
    @Query("UPDATE sync_state SET applyingRemote = :on WHERE id = 1") suspend fun setApplyingRemote(on: Boolean)

    @Query("SELECT * FROM sync_outbox ORDER BY queuedAt, type, recordId") suspend fun outbox(): List<SyncOutboxEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM sync_outbox WHERE type = :type AND recordId = :id)")
    suspend fun isQueued(type: String, id: String): Boolean
    /** Anzahl offener Outbox-Einträge; meldet sich bei jeder Änderung der Tabelle (für den Sync-Auslöser). */
    @Query("SELECT COUNT(*) FROM sync_outbox") fun observeOutboxCount(): Flow<Int>
    /** Wurde seit [since] (ms) ein Eintrag vorgemerkt oder erneut vorgemerkt? Ältere, nur stehengebliebene zählen nicht. */
    @Query("SELECT EXISTS(SELECT 1 FROM sync_outbox WHERE queuedAt >= :since)") suspend fun hasQueuedSince(since: Long): Boolean
    @Upsert suspend fun enqueue(e: SyncOutboxEntity)
    @Query("SELECT queuedAt FROM sync_outbox WHERE type = :type AND recordId = :id") suspend fun queuedAtOf(type: String, id: String): Long?
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
    @Query("SELECT EXISTS(SELECT 1 FROM sync_problem WHERE type = :type AND recordId = :id)")
    suspend fun hasProblem(type: String, id: String): Boolean
    @Query("DELETE FROM sync_problem") suspend fun clearProblems()
    @Query("DELETE FROM sync_problem WHERE type = :type AND recordId = :id") suspend fun clearProblem(type: String, id: String)

    /** Gibt es einen Vorratseintrag zur Zutat [ingredientId] mit offener lokaler Änderung? (Kaskade beim Löschen der Zutat) */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM pantry_item p JOIN sync_outbox o ON o.type = 'pantry_item' AND o.recordId = p.id " +
            "WHERE p.ingredientId = :ingredientId)",
    )
    suspend fun hasQueuedPantryFor(ingredientId: String): Boolean

    /** Gibt es eine Planposition zum Rezept [recipeId] mit offener lokaler Änderung? */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM meal_slot m JOIN sync_outbox o ON o.type = 'meal_slot' AND o.recordId = m.id " +
            "WHERE m.recipeId = :recipeId)",
    )
    suspend fun hasQueuedSlotsFor(recipeId: String): Boolean

    /** Gibt es einen Einkaufseintrag der Liste [listId] mit offener lokaler Änderung? */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM shopping_item i JOIN sync_outbox o ON o.type = 'shopping_item' AND o.recordId = i.id " +
            "WHERE i.listId = :listId)",
    )
    suspend fun hasQueuedItemsFor(listId: String): Boolean

    /** Gibt es einen Einkaufseintrag zur Zutat [ingredientId] mit offener lokaler Änderung? (`ingredientId` hat keinen FK) */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM shopping_item i JOIN sync_outbox o ON o.type = 'shopping_item' AND o.recordId = i.id " +
            "WHERE i.ingredientId = :ingredientId)",
    )
    suspend fun hasQueuedShoppingItemsFor(ingredientId: String): Boolean

    /** Ids aller lokalen Wurzeldatensätze je Typ (für den Voll-Abgleich). */
    @Query("SELECT id FROM ingredient") suspend fun ingredientIds(): List<String>
    @Query("SELECT id FROM recipe") suspend fun recipeIds(): List<String>
    @Query("SELECT id FROM meal_slot") suspend fun mealSlotIds(): List<String>
    @Query("SELECT id FROM pantry_item") suspend fun pantryItemIds(): List<String>
    @Query("SELECT id FROM shopping_list") suspend fun shoppingListIds(): List<String>
    @Query("SELECT id FROM shopping_item") suspend fun shoppingItemIds(): List<String>

    @Query("SELECT * FROM sync_photo_local WHERE uri = :uri") suspend fun photoLocal(uri: String): SyncPhotoLocalEntity?
    @Query("SELECT * FROM sync_photo_local WHERE sha256 = :sha256") suspend fun photoLocalsByHash(sha256: String): List<SyncPhotoLocalEntity>
    @Upsert suspend fun upsertPhotoLocal(e: SyncPhotoLocalEntity)
    @Query("DELETE FROM sync_photo_local WHERE uri = :uri") suspend fun deletePhotoLocal(uri: String)

    @Query("SELECT * FROM sync_photo_wanted ORDER BY recipeId") suspend fun photosWanted(): List<SyncPhotoWantedEntity>
    @Query("SELECT * FROM sync_photo_wanted WHERE recipeId = :recipeId") suspend fun photoWanted(recipeId: String): SyncPhotoWantedEntity?
    @Upsert suspend fun upsertPhotoWanted(e: SyncPhotoWantedEntity)
    @Query("DELETE FROM sync_photo_wanted WHERE recipeId = :recipeId") suspend fun deletePhotoWanted(recipeId: String)
}

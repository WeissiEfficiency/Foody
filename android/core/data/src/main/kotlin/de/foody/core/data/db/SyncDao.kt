package de.foody.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Zugriff auf die Sync-Tabellen. Grundoperationen je Tabelle (alle, einzeln, upsert, löschen) und die Abfragen, die
 * der `SyncSpeicher` für Pakete, Anwenden und Voll-Abgleich braucht.
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

    /** Merkt jeden Wurzeldatensatz aller sechs Typen als lebend vor; vorhandene Einträge werden ersetzt. */
    @Query(
        """INSERT OR REPLACE INTO sync_outbox (type, recordId, deleted, queuedAt)
           SELECT 'ingredient', id, 0, :jetzt FROM ingredient
           UNION ALL SELECT 'recipe', id, 0, :jetzt FROM recipe
           UNION ALL SELECT 'meal_slot', id, 0, :jetzt FROM meal_slot
           UNION ALL SELECT 'shopping_list', id, 0, :jetzt FROM shopping_list
           UNION ALL SELECT 'shopping_item', id, 0, :jetzt FROM shopping_item
           UNION ALL SELECT 'tagebuch_eintrag', id, 0, :jetzt FROM tagebuch_eintrag""",
    )
    suspend fun allesVormerken(jetzt: Long)

    @Query("DELETE FROM sync_outbox") suspend fun outboxLeeren()

    /** Zahl der Outbox-Einträge, bei jeder Änderung neu (für die Sync-Auslöser). */
    @Query("SELECT COUNT(*) FROM sync_outbox") fun outboxAnzahl(): Flow<Int>

    // sync_record_rev
    @Query("SELECT * FROM sync_record_rev ORDER BY type, recordId") suspend fun revisionen(): List<SyncRevisionEntity>

    @Query("SELECT * FROM sync_record_rev WHERE type = :type AND recordId = :recordId")
    suspend fun revision(type: String, recordId: String): SyncRevisionEntity?

    @Upsert suspend fun upsertRevision(e: SyncRevisionEntity)

    @Query("DELETE FROM sync_record_rev WHERE type = :type AND recordId = :recordId")
    suspend fun loeschenRevision(type: String, recordId: String)

    @Query("DELETE FROM sync_record_rev") suspend fun revisionenLeeren()

    // sync_state (genau eine Zeile, id = 1)
    @Query("SELECT * FROM sync_state WHERE id = 1") suspend fun zustand(): SyncZustandEntity?

    @Upsert suspend fun upsertZustand(e: SyncZustandEntity)

    /** Schaltet die Trigger-Ruhe um (nur innerhalb der Transaktion des Anwendens). */
    @Query("UPDATE sync_state SET applyingRemote = :wert WHERE id = 1") suspend fun applyingRemoteSetzen(wert: Boolean)

    // sync_problem
    @Query("SELECT * FROM sync_problem ORDER BY type, recordId") suspend fun probleme(): List<SyncProblemEntity>

    @Query("SELECT * FROM sync_problem WHERE type = :type AND recordId = :recordId")
    suspend fun problem(type: String, recordId: String): SyncProblemEntity?

    @Upsert suspend fun upsertProblem(e: SyncProblemEntity)

    @Query("DELETE FROM sync_problem WHERE type = :type AND recordId = :recordId")
    suspend fun loeschenProblem(type: String, recordId: String)

    @Query("DELETE FROM sync_problem") suspend fun problemeLeeren()

    // sync_photo_local
    @Query("SELECT * FROM sync_photo_local ORDER BY uri") suspend fun fotosLokal(): List<SyncFotoLokalEntity>

    @Query("SELECT * FROM sync_photo_local WHERE uri = :uri") suspend fun fotoLokal(uri: String): SyncFotoLokalEntity?

    @Upsert suspend fun upsertFotoLokal(e: SyncFotoLokalEntity)

    @Query("DELETE FROM sync_photo_local WHERE uri = :uri") suspend fun loeschenFotoLokal(uri: String)

    /** Gemerkte Dateien mit diesem Hash (ob die Datei noch passt, prüft der `FotoIndex`). */
    @Query("SELECT * FROM sync_photo_local WHERE sha256 = :sha256 ORDER BY uri")
    suspend fun fotosLokalNachHash(sha256: String): List<SyncFotoLokalEntity>

    // sync_photo_wanted
    @Query("SELECT * FROM sync_photo_wanted ORDER BY recipeId") suspend fun fotosGewuenscht(): List<SyncFotoGewuenschtEntity>

    @Query("SELECT * FROM sync_photo_wanted WHERE recipeId = :recipeId")
    suspend fun fotoGewuenscht(recipeId: String): SyncFotoGewuenschtEntity?

    @Upsert suspend fun upsertFotoGewuenscht(e: SyncFotoGewuenschtEntity)

    @Query("DELETE FROM sync_photo_wanted WHERE recipeId = :recipeId") suspend fun loeschenFotoGewuenscht(recipeId: String)

    @Query("DELETE FROM sync_photo_wanted") suspend fun fotowuenscheLeeren()

    // Abläufe des Sync-Speichers auf den Fachtabellen

    /** Setzt das Foto eines Rezepts, ohne `updatedAt` zu ändern (nur mit `applyingRemote = 1` aufrufen). */
    @Query("UPDATE recipe SET imageUri = :link WHERE id = :rezeptId")
    suspend fun rezeptFotoSetzen(rezeptId: String, link: String?)

    /** Zutat mit genau diesem Namen (binär, passt zum eindeutigen Index auf `canonicalName`). */
    @Query("SELECT * FROM ingredient WHERE canonicalName = :name LIMIT 1")
    suspend fun zutatNachNameExakt(name: String): ZutatEntity?

    /** Zutat mit diesem Namen ohne Beachtung der Groß-/Kleinschreibung. */
    @Query("SELECT * FROM ingredient WHERE canonicalName = :name COLLATE NOCASE LIMIT 1")
    suspend fun zutatNachName(name: String): ZutatEntity?

    // Zusammenführen zweier Zutaten: alle Verweise von [von] auf [nach] umhängen (Trigger merken die Eltern vor).
    @Query("UPDATE recipe_ingredient SET ingredientId = :nach WHERE ingredientId = :von")
    suspend fun rezeptZeilenUmhaengen(von: String, nach: String)

    @Query("UPDATE shopping_item SET ingredientId = :nach WHERE ingredientId = :von")
    suspend fun einkaufsartikelUmhaengen(von: String, nach: String)

    @Query("UPDATE tagebuch_eintrag SET zutatId = :nach WHERE zutatId = :von")
    suspend fun tagebuchUmhaengen(von: String, nach: String)

    /** Wie oft verwenden Rezeptzeilen die Zutat (Fremdschlüssel RESTRICT)? */
    @Query("SELECT COUNT(*) FROM recipe_ingredient WHERE ingredientId = :zutatId")
    suspend fun zutatVerwendungen(zutatId: String): Int

    /** Gibt es einen Einkaufsartikel zur Zutat mit offener lokaler Änderung? (`ingredientId` hat keinen Fremdschlüssel) */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM shopping_item i JOIN sync_outbox o ON o.type = 'shopping_item' AND o.recordId = i.id " +
            "WHERE i.ingredientId = :zutatId)",
    )
    suspend fun wartendeArtikelZurZutat(zutatId: String): Boolean

    /** Gibt es einen Plan-Eintrag zum Rezept mit offener lokaler Änderung? (Kaskade beim Löschen des Rezepts) */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM meal_slot m JOIN sync_outbox o ON o.type = 'meal_slot' AND o.recordId = m.id " +
            "WHERE m.recipeId = :rezeptId)",
    )
    suspend fun wartendePlanEintraegeZumRezept(rezeptId: String): Boolean

    /** Gibt es einen Einkaufsartikel der Liste mit offener lokaler Änderung? (Kaskade beim Löschen der Liste) */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM shopping_item i JOIN sync_outbox o ON o.type = 'shopping_item' AND o.recordId = i.id " +
            "WHERE i.listId = :listeId)",
    )
    suspend fun wartendeArtikelDerListe(listeId: String): Boolean

    /** IDs aller lokalen Wurzeldatensätze je Typ (für den Voll-Abgleich). */
    @Query("SELECT id FROM ingredient") suspend fun zutatIds(): List<String>

    @Query("SELECT id FROM recipe") suspend fun rezeptIds(): List<String>

    @Query("SELECT id FROM meal_slot") suspend fun planEintragIds(): List<String>

    @Query("SELECT id FROM shopping_list") suspend fun einkaufslisteIds(): List<String>

    @Query("SELECT id FROM shopping_item") suspend fun einkaufsartikelIds(): List<String>

    @Query("SELECT id FROM tagebuch_eintrag") suspend fun tagebuchIds(): List<String>
}

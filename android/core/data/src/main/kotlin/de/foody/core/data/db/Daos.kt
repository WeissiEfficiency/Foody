package de.foody.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/*
 * Grund-DAOs je Fachtabelle für Sync und Sicherung. `upsert` nutzt Room-`@Upsert` (INSERT, bei Konflikt UPDATE) –
 * nie `REPLACE`, das als DELETE + INSERT Kindzeilen per CASCADE löschen und Lösch-Trigger auslösen würde.
 * Abfragen für die Oberfläche kommen mit den Bereichs-Repositories.
 */

/** Zutaten (`ingredient`). */
@Dao
interface ZutatDao {
    @Query("SELECT * FROM ingredient ORDER BY id") suspend fun alle(): List<ZutatEntity>

    @Query("SELECT * FROM ingredient WHERE id = :id") suspend fun nachId(id: String): ZutatEntity?

    @Upsert suspend fun upsert(e: ZutatEntity)

    @Query("DELETE FROM ingredient WHERE id = :id") suspend fun loeschen(id: String)

    /** Leert die Tabelle (Sicherung: Import ersetzt alle Daten). */
    @Query("DELETE FROM ingredient") suspend fun alleLoeschen()
}

/** Rezepte (`recipe`). */
@Dao
interface RezeptDao {
    @Query("SELECT * FROM recipe ORDER BY id") suspend fun alle(): List<RezeptEntity>

    @Query("SELECT * FROM recipe WHERE id = :id") suspend fun nachId(id: String): RezeptEntity?

    @Upsert suspend fun upsert(e: RezeptEntity)

    @Query("DELETE FROM recipe WHERE id = :id") suspend fun loeschen(id: String)

    /** Leert die Tabelle (Sicherung: Import ersetzt alle Daten). */
    @Query("DELETE FROM recipe") suspend fun alleLoeschen()
}

/** Zutaten der Rezepte (`recipe_ingredient`). */
@Dao
interface RezeptZutatDao {
    @Query("SELECT * FROM recipe_ingredient ORDER BY id") suspend fun alle(): List<RezeptZutatEntity>

    @Query("SELECT * FROM recipe_ingredient WHERE id = :id") suspend fun nachId(id: String): RezeptZutatEntity?

    /** Zeilen eines Rezepts in Anzeigereihenfolge. */
    @Query("SELECT * FROM recipe_ingredient WHERE recipeId = :recipeId ORDER BY sortOrder, id")
    suspend fun nachRezept(recipeId: String): List<RezeptZutatEntity>

    @Upsert suspend fun upsert(e: RezeptZutatEntity)

    @Query("DELETE FROM recipe_ingredient WHERE id = :id") suspend fun loeschen(id: String)

    /** Leert die Tabelle (Sicherung: Import ersetzt alle Daten). */
    @Query("DELETE FROM recipe_ingredient") suspend fun alleLoeschen()
}

/** Arbeitsschritte (`instruction_step`). */
@Dao
interface ArbeitsschrittDao {
    @Query("SELECT * FROM instruction_step ORDER BY id") suspend fun alle(): List<ArbeitsschrittEntity>

    @Query("SELECT * FROM instruction_step WHERE id = :id") suspend fun nachId(id: String): ArbeitsschrittEntity?

    /** Schritte eines Rezepts in Anzeigereihenfolge. */
    @Query("SELECT * FROM instruction_step WHERE recipeId = :recipeId ORDER BY position, id")
    suspend fun nachRezept(recipeId: String): List<ArbeitsschrittEntity>

    @Upsert suspend fun upsert(e: ArbeitsschrittEntity)

    @Query("DELETE FROM instruction_step WHERE id = :id") suspend fun loeschen(id: String)

    /** Leert die Tabelle (Sicherung: Import ersetzt alle Daten). */
    @Query("DELETE FROM instruction_step") suspend fun alleLoeschen()
}

/** Plan-Einträge (`meal_slot`). */
@Dao
interface PlanEintragDao {
    @Query("SELECT * FROM meal_slot ORDER BY id") suspend fun alle(): List<PlanEintragEntity>

    @Query("SELECT * FROM meal_slot WHERE id = :id") suspend fun nachId(id: String): PlanEintragEntity?

    @Upsert suspend fun upsert(e: PlanEintragEntity)

    @Query("DELETE FROM meal_slot WHERE id = :id") suspend fun loeschen(id: String)

    /** Leert die Tabelle (Sicherung: Import ersetzt alle Daten). */
    @Query("DELETE FROM meal_slot") suspend fun alleLoeschen()
}

/** Einkaufslisten (`shopping_list`). */
@Dao
interface EinkaufslisteDao {
    @Query("SELECT * FROM shopping_list ORDER BY id") suspend fun alle(): List<EinkaufslisteEntity>

    @Query("SELECT * FROM shopping_list WHERE id = :id") suspend fun nachId(id: String): EinkaufslisteEntity?

    @Upsert suspend fun upsert(e: EinkaufslisteEntity)

    @Query("DELETE FROM shopping_list WHERE id = :id") suspend fun loeschen(id: String)

    /** Leert die Tabelle (Sicherung: Import ersetzt alle Daten). */
    @Query("DELETE FROM shopping_list") suspend fun alleLoeschen()
}

/** Einkaufsartikel (`shopping_item`). */
@Dao
interface EinkaufsartikelDao {
    @Query("SELECT * FROM shopping_item ORDER BY id") suspend fun alle(): List<EinkaufsartikelEntity>

    @Query("SELECT * FROM shopping_item WHERE id = :id") suspend fun nachId(id: String): EinkaufsartikelEntity?

    @Upsert suspend fun upsert(e: EinkaufsartikelEntity)

    @Query("DELETE FROM shopping_item WHERE id = :id") suspend fun loeschen(id: String)

    /** Leert die Tabelle (Sicherung: Import ersetzt alle Daten). */
    @Query("DELETE FROM shopping_item") suspend fun alleLoeschen()
}

/** Herkunft der Einkaufsartikel (`shopping_item_source`). */
@Dao
interface EinkaufsartikelQuelleDao {
    @Query("SELECT * FROM shopping_item_source ORDER BY id") suspend fun alle(): List<EinkaufsartikelQuelleEntity>

    @Query("SELECT * FROM shopping_item_source WHERE id = :id") suspend fun nachId(id: String): EinkaufsartikelQuelleEntity?

    /** Herkunftszeilen eines Einkaufsartikels. */
    @Query("SELECT * FROM shopping_item_source WHERE shoppingItemId = :shoppingItemId ORDER BY id")
    suspend fun nachArtikel(shoppingItemId: String): List<EinkaufsartikelQuelleEntity>

    @Upsert suspend fun upsert(e: EinkaufsartikelQuelleEntity)

    @Query("DELETE FROM shopping_item_source WHERE id = :id") suspend fun loeschen(id: String)

    /** Leert die Tabelle (Sicherung: Import ersetzt alle Daten). */
    @Query("DELETE FROM shopping_item_source") suspend fun alleLoeschen()
}

/** Tagebuch-Einträge (`tagebuch_eintrag`). */
@Dao
interface TagebuchDao {
    @Query("SELECT * FROM tagebuch_eintrag ORDER BY id") suspend fun alle(): List<TagebuchEintragEntity>

    @Query("SELECT * FROM tagebuch_eintrag WHERE id = :id") suspend fun nachId(id: String): TagebuchEintragEntity?

    @Upsert suspend fun upsert(e: TagebuchEintragEntity)

    @Query("DELETE FROM tagebuch_eintrag WHERE id = :id") suspend fun loeschen(id: String)

    /** Leert die Tabelle (Sicherung: Import ersetzt alle Daten). */
    @Query("DELETE FROM tagebuch_eintrag") suspend fun alleLoeschen()
}

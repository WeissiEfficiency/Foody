package de.foody.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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

    /** Alle Zutaten als Flow (Nährwertberechnung der Oberfläche). */
    @Query("SELECT * FROM ingredient ORDER BY id") fun alleFlow(): Flow<List<ZutatEntity>>

    /** Namenstreffer ohne Groß-/Kleinschreibung (wie v1 `findByName`). */
    @Query("SELECT * FROM ingredient WHERE canonicalName = :name COLLATE NOCASE LIMIT 1")
    suspend fun nachNameOhneGross(name: String): ZutatEntity?

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

    /** Nicht archivierte Rezepte, nach Name. */
    @Query("SELECT * FROM recipe WHERE archivedAt IS NULL ORDER BY name COLLATE NOCASE, id") fun aktive(): Flow<List<RezeptEntity>>

    /** Rezepte mit der Quell-URL [url] (genauer Vergleich, wie beim Import gespeichert), nach ID sortiert. */
    @Query("SELECT * FROM recipe WHERE sourceUrl = :url ORDER BY id") suspend fun nachQuelle(url: String): List<RezeptEntity>

    /** Portionen-Nachtrag: nur Portionen und Notiz; wie jede lokale Änderung neuer Zeitstempel und Version + 1. */
    @Query("UPDATE recipe SET defaultServings = :portionen, notes = :notiz, updatedAt = :updatedAt, version = version + 1 WHERE id = :id")
    suspend fun portionenSetzen(id: String, portionen: Int, notiz: String?, updatedAt: Long)

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

    /** Alle Zeilen als Flow, in Anzeigereihenfolge (Nährwerte je Rezept). */
    @Query("SELECT * FROM recipe_ingredient ORDER BY recipeId, sortOrder, id") fun alleFlow(): Flow<List<RezeptZutatEntity>>

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

    /** Plan-Einträge von [von] bis [bis] (beide eingeschlossen). */
    @Query("SELECT * FROM meal_slot WHERE date BETWEEN :von AND :bis ORDER BY date, id")
    fun zwischen(von: LocalDate, bis: LocalDate): Flow<List<PlanEintragEntity>>

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

    /** Offene Artikel der zuletzt geänderten Liste, in Listenreihenfolge. */
    @Query(
        "SELECT * FROM shopping_item WHERE checked = 0 AND listId = " +
            "(SELECT id FROM shopping_list ORDER BY updatedAt DESC, id LIMIT 1) ORDER BY sortOrder, id",
    )
    fun offeneDerJuengstenListe(): Flow<List<EinkaufsartikelEntity>>

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

    /** Einträge eines Tages in Eintragsreihenfolge. */
    @Query("SELECT * FROM tagebuch_eintrag WHERE datum = :datum ORDER BY createdAt, id")
    fun amTag(datum: LocalDate): Flow<List<TagebuchEintragEntity>>

    /** Gibt es schon einen Eintrag aus diesem Plan-Eintrag? */
    @Query("SELECT EXISTS(SELECT 1 FROM tagebuch_eintrag WHERE planEintragId = :planEintragId)")
    suspend fun gibtEsFuerPlan(planEintragId: String): Boolean

    @Upsert suspend fun upsert(e: TagebuchEintragEntity)

    @Query("DELETE FROM tagebuch_eintrag WHERE id = :id") suspend fun loeschen(id: String)

    /** Leert die Tabelle (Sicherung: Import ersetzt alle Daten). */
    @Query("DELETE FROM tagebuch_eintrag") suspend fun alleLoeschen()
}

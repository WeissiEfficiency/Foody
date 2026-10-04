package de.foody.app.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SQLite-Trigger, die jede lokale Änderung in `sync_outbox` vormerken – in derselben Transaktion wie die Änderung,
 * sodass keine Schreibstelle sie vergessen kann. Die Outbox-Trigger sind nur wirksam, wenn `sync_state.active = 1`
 *
 * und `applyingRemote = 0` gilt; ohne Zeile in `sync_state` (oder ohne `activate`) bleibt der Sync inaktiv.
 * Rekursion: Room setzt `PRAGMA recursive_triggers = 1` (InvalidationTracker, auf jeder Schreibverbindung), Trigger
 * dürfen sich also selbst auslösen. Jeder Trigger-Rumpf muss deshalb seine eigene WHEN-Bedingung falsch machen
 * (Pflege-Trigger von `shopping_item`: Zeitstempel ändert sich garantiert, z. B. `MAX(jetzt, alt + 1)`); die
 * Outbox-Trigger schreiben nur in `sync_outbox`, das keine `sync_*`-Trigger hat.
 * Achtung: `OnConflictStrategy.REPLACE` auf einer Wurzeltabelle löscht die alte Zeile und löst `sync_*_ad` aus
 * (Löschung wird vorgemerkt) – dort `@Upsert` verwenden.
 */
object SyncTriggers {
    /** Aktuelle Zeit als Epoch-Millisekunden. */
    private const val NOW = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"

    /** Sync aktiv und es werden gerade keine Server-Daten angewendet. */
    private const val ACTIVE = "(SELECT active = 1 AND applyingRemote = 0 FROM sync_state WHERE id = 1)"

    /** Pflege-Trigger: nicht bei angewendeten Server-Daten; fehlt die Zeile, gilt „nicht anwenden“. */
    private const val NOT_APPLYING = "COALESCE((SELECT applyingRemote FROM sync_state WHERE id = 1), 0) = 0"

    private val roots = listOf("ingredient", "recipe", "meal_slot", "pantry_item", "shopping_list", "shopping_item")

    /** Kindtabelle, Elterntabelle (= Typ), Fremdschlüsselspalte. */
    private val children = listOf(
        Triple("recipe_ingredient", "recipe", "recipeId"),
        Triple("instruction_step", "recipe", "recipeId"),
        Triple("shopping_item_source", "shopping_item", "shoppingItemId"),
    )

    val statements: List<String> = buildList {
        for (t in roots) {
            add(trigger("sync_${t}_ai", "AFTER INSERT ON $t", ACTIVE, "INSERT OR REPLACE INTO sync_outbox VALUES('$t', NEW.id, 0, $NOW);"))
            add(trigger("sync_${t}_au", "AFTER UPDATE ON $t", ACTIVE, "INSERT OR REPLACE INTO sync_outbox VALUES('$t', NEW.id, 0, $NOW);"))
            add(trigger("sync_${t}_ad", "AFTER DELETE ON $t", ACTIVE, "INSERT OR REPLACE INTO sync_outbox VALUES('$t', OLD.id, 1, $NOW);"))
        }
        for ((table, parent, fk) in children) {
            fun queue(row: String) =
                "INSERT OR REPLACE INTO sync_outbox SELECT '$parent', $row.$fk, 0, $NOW " +
                    "WHERE EXISTS (SELECT 1 FROM $parent WHERE id = $row.$fk);"
            add(trigger("sync_${table}_ai", "AFTER INSERT ON $table", ACTIVE, queue("NEW")))
            // Beim Umhängen auf einen anderen Elterndatensatz sind beide betroffen.
            add(trigger("sync_${table}_au", "AFTER UPDATE ON $table", ACTIVE, queue("NEW") + "\n" + queue("OLD")))
            add(trigger("sync_${table}_ad", "AFTER DELETE ON $table", ACTIVE, queue("OLD")))
        }
        // Pflege der Änderungszeitstempel von Einkaufseinträgen (unabhängig von `active`).
        add(
            trigger(
                "sync_shopping_item_checked_stamp", "AFTER UPDATE OF checked ON shopping_item",
                "$NOT_APPLYING AND OLD.checked <> NEW.checked",
                "UPDATE shopping_item SET checkedChangedAt = $NOW WHERE id = NEW.id;",
            ),
        )
        add(
            trigger(
                "sync_shopping_item_updated_stamp", "AFTER UPDATE ON shopping_item",
                "$NOT_APPLYING AND NEW.updatedAt = OLD.updatedAt",
                // MAX: der Zeitstempel ändert sich garantiert, damit die Bedingung nicht erneut zutrifft (Rekursion)
                "UPDATE shopping_item SET updatedAt = MAX($NOW, OLD.updatedAt + 1) WHERE id = NEW.id;",
            ),
        )
        add(
            trigger(
                "sync_shopping_item_inserted_stamp", "AFTER INSERT ON shopping_item",
                "$NOT_APPLYING AND NEW.updatedAt = 0",
                "UPDATE shopping_item SET updatedAt = $NOW WHERE id = NEW.id;",
            ),
        )
    }

    private fun trigger(name: String, event: String, condition: String, body: String) =
        "CREATE TRIGGER IF NOT EXISTS $name $event WHEN $condition BEGIN\n$body\nEND"

    /** Legt die Sync-Trigger an (idempotent). */
    fun create(db: SupportSQLiteDatabase) {
        statements.forEach(db::execSQL)
    }
}

/** Frische Installation: Zeile `sync_state(id = 1)` und Trigger anlegen (Migration 3 → 4 erledigt das für bestehende). */
val FoodyDatabase.Companion.SYNC_CALLBACK: RoomDatabase.Callback
    get() = SyncCallback

object SyncCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO sync_state(id) VALUES (1)")
        SyncTriggers.create(db)
    }
}

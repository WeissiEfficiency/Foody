package de.foody.core.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SQLite-Trigger von Version 10: Sie merken jede lokale Änderung der Wurzeltabellen und ihrer Kindtabellen in
 * `sync_outbox` vor – in derselben Transaktion wie die Änderung, sodass keine Schreibstelle den Sync vergessen kann.
 * Outbox-Trigger wirken nur bei `sync_state.active = 1` und `applyingRemote = 0`; fehlt die Zeile, bleibt der Sync aus.
 *
 * Regeln:
 * - **Rekursion:** Room schaltet `PRAGMA recursive_triggers` auf jeder Schreibverbindung ein (InvalidationTracker).
 *   Jeder Trigger muss deshalb seine eigene WHEN-Bedingung durch seinen Rumpf falsch machen. Die Pflege-Trigger von
 *   `shopping_item` setzen den Zeitstempel garantiert neu (`MAX(jetzt, alt + 1)`); die Outbox-Trigger schreiben nur
 *   in `sync_outbox`, das selbst keine Trigger hat.
 * - **`@Upsert` statt `REPLACE`:** `OnConflictStrategy.REPLACE` löscht auf einer Wurzeltabelle erst die alte Zeile und
 *   löst `sync_*_ad` aus – die Änderung würde als Löschung vorgemerkt.
 * - **SQLite 3.18** (minSdk 26): kein UPSERT (`ON CONFLICT DO UPDATE`), kein `TRUE`/`FALSE`.
 */
object SyncTrigger {
    /** Aktuelle Zeit als Epoch-Millisekunden. */
    private const val JETZT = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"

    /** Sync aktiv und es werden gerade keine Server-Daten angewendet. */
    private const val AKTIV = "(SELECT active = 1 AND applyingRemote = 0 FROM sync_state WHERE id = 1)"

    /** Pflege-Trigger laufen auch bei inaktivem Sync, nur nicht beim Anwenden von Server-Daten. */
    private const val NICHT_ANWENDEN = "COALESCE((SELECT applyingRemote FROM sync_state WHERE id = 1), 0) = 0"

    /** Tabellen, deren Zeilen selbst Sync-Datensätze sind (Tabellenname = Datensatztyp). */
    private val wurzeln = listOf("ingredient", "recipe", "meal_slot", "shopping_list", "shopping_item", "tagebuch_eintrag")

    private data class Kind(val tabelle: String, val eltern: String, val fremdschluessel: String)

    /** Kindtabellen gehören zum Datensatz ihrer Elternzeile; eine Änderung merkt die Elternzeile vor. */
    private val kinder = listOf(
        Kind("recipe_ingredient", "recipe", "recipeId"),
        Kind("instruction_step", "recipe", "recipeId"),
        Kind("shopping_item_source", "shopping_item", "shoppingItemId"),
    )

    /** `CREATE TRIGGER`-Anweisungen von Version 10 (für `onCreate` und die Migration 9 → 10). */
    val anweisungen: List<String> = buildList {
        for (t in wurzeln) {
            add(trigger("sync_${t}_ai", "AFTER INSERT ON $t", AKTIV, vormerken("'$t'", "NEW.id", geloescht = false)))
            add(trigger("sync_${t}_au", "AFTER UPDATE ON $t", AKTIV, vormerken("'$t'", "NEW.id", geloescht = false)))
            add(trigger("sync_${t}_ad", "AFTER DELETE ON $t", AKTIV, vormerken("'$t'", "OLD.id", geloescht = true)))
        }
        for ((tabelle, eltern, fk) in kinder) {
            // Nur wenn die Elternzeile noch existiert (wie v1): Beim kaskadierenden Löschen des Elternteils darf ein
            // Kind-Trigger die vorgemerkte Löschung nie als Änderung (deleted = 0) überschreiben – unabhängig davon,
            // in welcher Reihenfolge SQLite Kaskade und Eltern-Trigger ausführt.
            fun eltern(zeile: String) =
                vormerken("'$eltern'", "$zeile.$fk", geloescht = false, bedingung = "EXISTS (SELECT 1 FROM $eltern WHERE id = $zeile.$fk)")
            add(trigger("sync_${tabelle}_ai", "AFTER INSERT ON $tabelle", AKTIV, eltern("NEW")))
            // Beim Umhängen auf eine andere Elternzeile sind beide betroffen.
            add(trigger("sync_${tabelle}_au", "AFTER UPDATE ON $tabelle", AKTIV, eltern("NEW") + "\n" + eltern("OLD")))
            add(trigger("sync_${tabelle}_ad", "AFTER DELETE ON $tabelle", AKTIV, eltern("OLD")))
        }

        // Pflege der Änderungszeitstempel von Einkaufsartikeln (unabhängig von `active`, für die Zusammenführung).
        add(
            trigger(
                "sync_shopping_item_checked_stamp", "AFTER UPDATE OF checked ON shopping_item",
                "$NICHT_ANWENDEN AND OLD.checked <> NEW.checked",
                "UPDATE shopping_item SET checkedChangedAt = $JETZT WHERE id = NEW.id;",
            ),
        )
        add(
            trigger(
                "sync_shopping_item_updated_stamp", "AFTER UPDATE ON shopping_item",
                "$NICHT_ANWENDEN AND NEW.updatedAt = OLD.updatedAt",
                // MAX: der Zeitstempel ändert sich garantiert, die Bedingung trifft danach nicht mehr zu (Rekursion).
                "UPDATE shopping_item SET updatedAt = MAX($JETZT, OLD.updatedAt + 1) WHERE id = NEW.id;",
            ),
        )
        add(
            trigger(
                "sync_shopping_item_inserted_stamp", "AFTER INSERT ON shopping_item",
                "$NICHT_ANWENDEN AND NEW.updatedAt = 0",
                "UPDATE shopping_item SET updatedAt = $JETZT WHERE id = NEW.id;",
            ),
        )

        // Ein lokal gewähltes Rezeptfoto erledigt den Foto-Wunsch; ebenso Einfügen (z. B. aus einer Sicherung) und
        // Löschen des Rezepts (keine verwaisten Wünsche).
        add(trigger("sync_recipe_wish_ai", "AFTER INSERT ON recipe", NICHT_ANWENDEN, "DELETE FROM sync_photo_wanted WHERE recipeId = NEW.id;"))
        add(trigger("sync_recipe_wish_ad", "AFTER DELETE ON recipe", NICHT_ANWENDEN, "DELETE FROM sync_photo_wanted WHERE recipeId = OLD.id;"))
        add(
            trigger(
                "sync_recipe_image_wish", "AFTER UPDATE OF imageUri ON recipe",
                "$NICHT_ANWENDEN AND OLD.imageUri IS NOT NEW.imageUri",
                "DELETE FROM sync_photo_wanted WHERE recipeId = NEW.id;",
            ),
        )
    }

    private val namensMuster = Regex("""^CREATE TRIGGER IF NOT EXISTS (\w+) """)

    /** Namen aller Trigger aus [anweisungen] (für `DROP TRIGGER` in Migrationen). */
    val namen: List<String> = anweisungen.map { namensMuster.find(it)!!.groupValues[1] }

    /** Legt alle Trigger an (idempotent). */
    fun anlegen(db: SupportSQLiteDatabase) = anweisungen.forEach(db::execSQL)

    /** Entfernt alle Trigger dieser Fassung. */
    fun entfernen(db: SupportSQLiteDatabase) = namen.forEach { db.execSQL("DROP TRIGGER IF EXISTS $it") }

    /**
     * Outbox-Eintrag als zwei Anweisungen: erst UPDATE, dann INSERT, falls es noch keinen gibt. So greift keine
     * Konfliktstrategie der auslösenden Anweisung (Rooms `@Insert` = ABORT würde ein `INSERT OR REPLACE` im Trigger
     * überschreiben). Beim erneuten Vormerken steigt `queuedAt` streng (`MAX(jetzt, alt + 1)`), damit der Push eine
     * Änderung während des Laufs sicher erkennt – auch in derselben Millisekunde. [bedingung] schränkt beide ein.
     */
    private fun vormerken(typ: String, id: String, geloescht: Boolean, bedingung: String? = null): String {
        val und = if (bedingung == null) "" else " AND $bedingung"
        val d = if (geloescht) 1 else 0
        return "UPDATE sync_outbox SET deleted = $d, queuedAt = MAX($JETZT, queuedAt + 1) " +
            "WHERE type = $typ AND recordId = $id$und;\n" +
            "INSERT INTO sync_outbox(type, recordId, deleted, queuedAt) SELECT $typ, $id, $d, $JETZT " +
            "WHERE NOT EXISTS (SELECT 1 FROM sync_outbox WHERE type = $typ AND recordId = $id)$und;"
    }

    private fun trigger(name: String, ereignis: String, bedingung: String, rumpf: String) =
        "CREATE TRIGGER IF NOT EXISTS $name $ereignis WHEN $bedingung BEGIN\n$rumpf\nEND"

    /** Neue Datenbank: Zeile `sync_state(id = 1)` (Sync inaktiv) und alle Trigger anlegen. */
    internal object Callback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT OR IGNORE INTO sync_state(id) VALUES (1)")
            anlegen(db)
        }
    }
}

package de.foody.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.foody.domain.Mahlzeit

/**
 * Lokale Single Source of Truth. Schema wird nach app/schemas exportiert;
 * jede Versionserhöhung braucht eine Migration plus Migrationstest –
 * keine destruktiven Migrationen.
 */
@Database(
    entities = [
        IngredientEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        InstructionStepEntity::class,
        MealSlotEntity::class,
        PantryItemEntity::class,
        ShoppingListEntity::class,
        ShoppingItemEntity::class,
        ShoppingItemSourceEntity::class,
        SyncOutboxEntity::class,
        SyncRecordRevEntity::class,
        SyncStateEntity::class,
        SyncProblemEntity::class,
        SyncPhotoLocalEntity::class,
        SyncPhotoWantedEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FoodyDatabase : RoomDatabase() {
    abstract fun ingredientDao(): IngredientDao
    abstract fun recipeDao(): RecipeDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun pantryDao(): PantryDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "foody.db"
    }
}

/**
 * v1 → v2: Favoriten und Quell-URL für Rezepte.
 * Die Quell-URL bereits importierter Rezepte steht in der ersten Notizzeile („Quelle: https://…“)
 * und wird übernommen, damit auch vorhandene Sammlungen doppelte Importe erkennen.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipe ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE recipe ADD COLUMN sourceUrl TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_sourceUrl ON recipe (sourceUrl)")
        db.execSQL(
            """UPDATE recipe SET sourceUrl = trim(substr(notes, 9,
                   CASE WHEN instr(notes, char(10)) > 0 THEN instr(notes, char(10)) - 9 ELSE length(notes) END))
               WHERE notes LIKE 'Quelle: http%'""",
        )
    }
}

/**
 * v2 → v3: Angaben zu Einkaufsartikeln und eigene Rezeptbewertung. Beide Spalten sind optional (NULL),
 * bestehende Daten bleiben unverändert. In einer Migration gebündelt, damit nicht kurz hintereinander zwei
 * Schemaversionen entstehen.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shopping_item ADD COLUMN note TEXT")
        db.execSQL("ALTER TABLE recipe ADD COLUMN rating INTEGER")
    }
}

/**
 * v3 → v4: Sync-Tabellen, Änderungszeitstempel für Einkaufseinträge und die Outbox-Trigger. Der Sync bleibt
 * inaktiv (`sync_state.active = 0`), bestehende Daten ändern sich nicht.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shopping_item ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE shopping_item ADD COLUMN checkedChangedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_outbox` (`type` TEXT NOT NULL, `recordId` TEXT NOT NULL, `deleted` INTEGER NOT NULL, `queuedAt` INTEGER NOT NULL, PRIMARY KEY(`type`, `recordId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_record_rev` (`type` TEXT NOT NULL, `recordId` TEXT NOT NULL, `rev` INTEGER NOT NULL, PRIMARY KEY(`type`, `recordId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_state` (`id` INTEGER NOT NULL, `active` INTEGER NOT NULL DEFAULT 0, `applyingRemote` INTEGER NOT NULL DEFAULT 0, `serverUrl` TEXT, `householdId` TEXT, `cursor` INTEGER NOT NULL DEFAULT 0, `lastSyncAt` INTEGER, `lastError` TEXT, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_problem` (`type` TEXT NOT NULL, `recordId` TEXT NOT NULL, `code` TEXT NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`type`, `recordId`))")
        db.execSQL("INSERT OR IGNORE INTO sync_state(id) VALUES (1)")
        SyncTriggers.create(db)
    }
}

/**
 * v4 → v5: nur die Sync-Trigger werden neu angelegt – erneutes Vormerken setzt `queuedAt` jetzt streng steigend
 * (`MAX(jetzt, alt + 1)`). Tabellen und Daten bleiben unverändert.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        SyncTriggers.drop(db)
        SyncTriggers.create(db)
    }
}

/**
 * v5 → v6: nur zwei neue Tabellen für den Foto-Sync (Hash-Cache je Fotodatei, gewünschte Fotos je Rezept).
 * Bestehende Tabellen und Daten bleiben unverändert.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_photo_local` (`uri` TEXT NOT NULL, `sha256` TEXT NOT NULL, `size` INTEGER NOT NULL, `modifiedAt` INTEGER NOT NULL, PRIMARY KEY(`uri`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_photo_local_sha256` ON `sync_photo_local` (`sha256`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_photo_wanted` (`recipeId` TEXT NOT NULL, `sha256` TEXT NOT NULL, PRIMARY KEY(`recipeId`))")
        // Neuer Trigger `sync_recipe_image_wish`: Trigger neu anlegen
        SyncTriggers.drop(db)
        SyncTriggers.create(db)
    }
}

/**
 * v6 → v7: Einordnung am Rezept (Mahlzeiten, Gänge; null = vermuten) und Plan-Einträge mit festen Mahlzeit-Schlüsseln.
 * Die Zuordnung der alten Freitexte läuft in Kotlin, weil SQLites lower() keine Umlaute kennt („FRÜHSTÜCK“).
 * Unbekannte Freitexte bleiben stehen („Sonstiges“). Die Sync-Trigger laden umgestellte Einträge einmal hoch – gewollt.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recipe` ADD COLUMN `mahlzeiten` TEXT")
        db.execSQL("ALTER TABLE `recipe` ADD COLUMN `gaenge` TEXT")
        val umstellen = buildList {
            db.query("SELECT id, slotType FROM meal_slot").use { c ->
                while (c.moveToNext()) {
                    val neu = Mahlzeit.ausText(c.getString(1))?.name
                    if (neu != null && neu != c.getString(1)) add(c.getString(0) to neu)
                }
            }
        }
        umstellen.forEach { (id, neu) -> db.execSQL("UPDATE meal_slot SET slotType = ? WHERE id = ?", arrayOf(neu, id)) }
    }
}

/** Alle Migrationen in Reihenfolge – nie fallbackToDestructiveMigration (docs/architecture.md). */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

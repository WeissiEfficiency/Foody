package de.foody.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    ],
    version = 2,
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

/** Alle Migrationen in Reihenfolge – nie fallbackToDestructiveMigration (docs/architecture.md). */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)

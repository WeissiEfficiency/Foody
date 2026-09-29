package de.foody.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
    version = 1,
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

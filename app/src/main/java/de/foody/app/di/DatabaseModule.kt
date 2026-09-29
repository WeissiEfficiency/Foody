package de.foody.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.foody.app.data.db.FoodyDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): FoodyDatabase =
        Room.databaseBuilder(context, FoodyDatabase::class.java, FoodyDatabase.NAME).build()

    @Provides fun ingredientDao(db: FoodyDatabase) = db.ingredientDao()
    @Provides fun recipeDao(db: FoodyDatabase) = db.recipeDao()
    @Provides fun mealPlanDao(db: FoodyDatabase) = db.mealPlanDao()
    @Provides fun pantryDao(db: FoodyDatabase) = db.pantryDao()
    @Provides fun shoppingDao(db: FoodyDatabase) = db.shoppingDao()
}

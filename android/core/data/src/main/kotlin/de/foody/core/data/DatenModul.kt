package de.foody.core.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.MIGRATION_9_10
import javax.inject.Singleton

/**
 * Baut die Datenbank `foody.db`: nur Migration 9 → 10 (v1 → V3), Callback für neue Datenbanken, **nie** ein
 * destruktiver Fallback – ohne Migrationspfad (z. B. Version 8) scheitert das Öffnen, die Daten bleiben unberührt.
 * [name] nur für Tests abweichend.
 */
fun baueDatenbank(context: Context, name: String = FoodyDatenbank.NAME): FoodyDatenbank =
    Room.databaseBuilder(context, FoodyDatenbank::class.java, name)
        .addMigrations(MIGRATION_9_10)
        .addCallback(FoodyDatenbank.CALLBACK)
        .build()

/** Hilt-Modul der Datenschicht. */
@Module
@InstallIn(SingletonComponent::class)
object DatenModul {
    @Provides
    @Singleton
    fun datenbank(@ApplicationContext context: Context): FoodyDatenbank = baueDatenbank(context)
}

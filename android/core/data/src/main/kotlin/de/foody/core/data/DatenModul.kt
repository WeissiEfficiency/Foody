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
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.fotos.FotoIndex
import de.foody.core.data.rezeptimport.PortionenNachtrag
import de.foody.core.data.sicherung.Sicherung
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.data.sync.SyncSpeicher
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

    @Provides
    @Singleton
    fun fotoAblage(@ApplicationContext context: Context): FotoAblage = FotoAblage(context)

    @Provides
    @Singleton
    fun fotoIndex(db: FoodyDatenbank, ablage: FotoAblage): FotoIndex = FotoIndex(db.syncDao(), ablage)

    /** Singleton: [SyncSpeicher.laufSperre] muss für Sync-Lauf, Trennen und das Ersetzen beim Aktivieren dieselbe sein. */
    @Provides
    @Singleton
    fun syncSpeicher(db: FoodyDatenbank, ablage: FotoAblage, index: FotoIndex): SyncSpeicher = SyncSpeicher(db, ablage, index)

    @Provides
    @Singleton
    fun sicherung(@ApplicationContext context: Context, db: FoodyDatenbank, ablage: FotoAblage, sync: SyncSpeicher): Sicherung =
        Sicherung(context, db, ablage, sync)

    @Provides
    @Singleton
    fun portionenNachtrag(db: FoodyDatenbank): PortionenNachtrag = PortionenNachtrag(db)

    @Provides
    @Singleton
    fun tagebuchRepository(db: FoodyDatenbank): TagebuchRepository = TagebuchRepository(db, System::currentTimeMillis)
}

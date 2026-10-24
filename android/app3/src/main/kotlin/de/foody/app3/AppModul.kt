package de.foody.app3

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.sync.SyncAusloeser
import de.foody.core.sync.SyncKonfiguration
import de.foody.core.sync.SyncKonto
import de.foody.feature.einstellungen.KontoZugriff
import javax.inject.Singleton

/** Was nur die App weiß: ob der Build http zum Emulator-Host erlauben darf, und wie die Bereiche an den Sync kommen. */
@Module
@InstallIn(SingletonComponent::class)
object AppModul {
    @Provides
    @Singleton
    fun syncKonfiguration(): SyncKonfiguration = SyncKonfiguration(lokalesHttpErlaubt = BuildConfig.DEBUG)

    @Provides
    fun kontoZugriff(konto: SyncKonto, db: FoodyDatenbank, ausloeser: SyncAusloeser): KontoZugriff =
        KontoAnbindung(konto, db, ausloeser)
}

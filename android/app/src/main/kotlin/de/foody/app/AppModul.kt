package de.foody.app

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.scan.ScanKonfiguration
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

    /** App-Version für den User-Agent der Anfragen an Open Food Facts. */
    @Provides
    @Singleton
    fun scanKonfiguration(): ScanKonfiguration = ScanKonfiguration(BuildConfig.VERSION_NAME)

    @Provides
    fun kontoZugriff(konto: SyncKonto, db: FoodyDatenbank, ausloeser: SyncAusloeser): KontoZugriff =
        KontoAnbindung(konto, db, ausloeser)
}

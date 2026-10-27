package de.foody.core.scan

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.foody.core.data.db.FoodyDatenbank
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Singleton

/**
 * Einstellungen des Scans, die die App liefert (`@Provides` in `:app3`, wie `SyncKonfiguration`). [appVersion] steht im
 * User-Agent der Anfragen an Open Food Facts.
 */
data class ScanKonfiguration(val appVersion: String)

/** Hilt-Modul des Scans (wie v1 `ScanModule`). [ScanKonfiguration] liefert die App. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModul {
    @Binds abstract fun strichcodeLeser(impl: GmsStrichcodeLeser): StrichcodeLeser
    @Binds abstract fun tabellenScanner(impl: MlKitTabellenScanner): TabellenScanner
    @Binds abstract fun playDienste(impl: GooglePlayDienste): PlayDienste
    @Binds abstract fun onlineSuche(impl: ScanEinstellung): OnlineSucheErlaubt

    companion object {
        /** Eigener Client ohne die lokale CA des Sync-Servers; 8 s Zeitlimit. */
        @Provides
        @Singleton
        fun produktSuche(konfiguration: ScanKonfiguration): ProduktSuche = OffProduktSuche(
            HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis = 8_000; connectTimeoutMillis = 8_000 } },
            konfiguration.appVersion,
        )

        @Provides
        fun katalogSuche(db: FoodyDatenbank): KatalogSuche = KatalogSuche { code ->
            db.zutatDao().mitStrichcode(code)?.let { ZutatTreffer(it.id, it.canonicalName) }
        }
    }
}

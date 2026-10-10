package de.foody.app.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.foody.app.BuildConfig
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.scan.GmsStrichcodeLeser
import de.foody.app.scan.GooglePlayDienste
import de.foody.app.scan.KatalogSuche
import de.foody.app.scan.MlKitTabellenScanner
import de.foody.app.scan.OffProduktSuche
import de.foody.app.scan.OnlineSucheErlaubt
import de.foody.app.scan.PlayDienste
import de.foody.app.scan.ProduktSuche
import de.foody.app.scan.ScanPreferences
import de.foody.app.scan.StrichcodeLeser
import de.foody.app.scan.TabellenScanner
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {
    @Binds abstract fun strichcodeLeser(impl: GmsStrichcodeLeser): StrichcodeLeser
    @Binds abstract fun tabellenScanner(impl: MlKitTabellenScanner): TabellenScanner
    @Binds abstract fun playDienste(impl: GooglePlayDienste): PlayDienste
    @Binds abstract fun onlineSuche(impl: ScanPreferences): OnlineSucheErlaubt

    companion object {
        /** Eigener Client ohne die lokale CA des Sync-Servers; 8 s Zeitlimit. */
        @Provides @Singleton
        fun produktSuche(): ProduktSuche = OffProduktSuche(
            HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis = 8_000; connectTimeoutMillis = 8_000 } },
            BuildConfig.VERSION_NAME,
        )

        @Provides
        fun katalogSuche(ingredients: IngredientRepository): KatalogSuche = KatalogSuche { ingredients.zutatMitStrichcode(it) }
    }
}

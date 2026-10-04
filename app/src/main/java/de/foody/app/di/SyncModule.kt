package de.foody.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.foody.app.sync.SyncAccountRepository
import de.foody.app.sync.SyncAccounts
import de.foody.app.sync.SyncEngineFactory
import io.ktor.client.HttpClient
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Scope, der so lange lebt wie der App-Prozess (für Hintergrundbeobachtung, die einen Bildschirm überdauert). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    /** Der Client der [SyncEngineFactory] (ein Verbindungspool); Konto-Aufrufe und Sync-Läufe teilen ihn. */
    @Provides
    @Singleton
    fun syncHttpClient(factory: SyncEngineFactory): HttpClient = factory.httpClient

    /** Oberflächen-ViewModels hängen von der Schnittstelle ab (austauschbar in Tests). */
    @Provides
    fun syncAccounts(repo: SyncAccountRepository): SyncAccounts = repo

    @Provides
    @Singleton
    @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

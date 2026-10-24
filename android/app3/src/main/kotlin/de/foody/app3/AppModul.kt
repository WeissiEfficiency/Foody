package de.foody.app3

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.foody.core.sync.SyncKonfiguration
import javax.inject.Singleton

/** Was nur die App weiß: ob der Build http zum Emulator-Host erlauben darf. */
@Module
@InstallIn(SingletonComponent::class)
object AppModul {
    @Provides
    @Singleton
    fun syncKonfiguration(): SyncKonfiguration = SyncKonfiguration(lokalesHttpErlaubt = BuildConfig.DEBUG)
}

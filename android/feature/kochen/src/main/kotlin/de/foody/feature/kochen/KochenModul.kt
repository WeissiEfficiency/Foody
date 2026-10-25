package de.foody.feature.kochen

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Named
import javax.inject.Singleton

/** Speicher des Kochmodus (die Uhr kommt aus `DatenModul`). */
@Module
@InstallIn(SingletonComponent::class)
object KochenModul {
    @Provides
    @Singleton
    fun sitzungen(@ApplicationContext context: Context, clock: Clock): KochSitzungSpeicher =
        KochSitzungSpeicher(context.getSharedPreferences("kochen_sitzungen", Context.MODE_PRIVATE), clock)

    @Provides
    @Named(TIMER_PREFS)
    fun timerPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(TIMER_PREFS, Context.MODE_PRIVATE)

    const val TIMER_PREFS = "kochen_timer"
}

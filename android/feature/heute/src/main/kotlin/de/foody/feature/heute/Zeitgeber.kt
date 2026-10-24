package de.foody.feature.heute

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Aktuelle Ortszeit als Flow – so wechseln „Heute“ und die nächste Mahlzeit um Mitternacht von selbst. */
fun interface Zeitgeber {
    fun jetzt(): Flow<LocalDateTime>
}

/** Sendet sofort und dann jeweils kurz nach jedem Minutenwechsel. */
fun systemzeit(): Flow<LocalDateTime> = flow {
    while (true) {
        val jetzt = LocalDateTime.now()
        emit(jetzt)
        delay(Duration.between(jetzt, jetzt.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)).toMillis() + NACHLAUF_MS)
    }
}

/** Etwas nach dem Minutenwechsel, damit `now()` sicher schon in der neuen Minute liegt. */
private const val NACHLAUF_MS = 50L

@Module
@InstallIn(SingletonComponent::class)
object HeuteModul {
    @Provides
    fun zeitgeber(): Zeitgeber = Zeitgeber(::systemzeit)
}

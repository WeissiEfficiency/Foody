package de.foody.core.data

import javax.inject.Qualifier

/**
 * Wo Nährwerte, kcal und Einkaufsmengen rechnen (`flowOn`), nie auf dem Haupt-Thread: in der App
 * `Dispatchers.Default` ([DatenModul.rechnen]), in Tests `EmptyCoroutineContext`, damit nichts auf einem fremden Thread
 * nachläuft. Dateizugriffe bleiben bei `Dispatchers.IO`; sie rechnen nichts und laufen in Tests genauso.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Rechnen

package de.foody.app.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Wie [runCatching], aber für suspendierende Blöcke: Ein Abbruch ([CancellationException]) wird weitergereicht
 * statt als Fehler verschluckt. Sonst liefe z. B. ein abgebrochener Import einfach weiter, und der Aufrufer
 * hielte einen Abbruch für einen gewöhnlichen Fehler.
 */
suspend inline fun <T> runSuspendCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

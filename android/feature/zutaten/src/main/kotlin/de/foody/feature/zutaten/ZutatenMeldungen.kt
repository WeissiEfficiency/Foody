package de.foody.feature.zutaten

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Meldungen vom Editor an die Liste („Gelöscht“, „Zusammengeführt in …“): Der Editor schließt sich, die Liste zeigt sie.
 * Ein Empfänger (die Liste); gepuffert, bis sie abholt.
 */
@Singleton
class ZutatenMeldungen @Inject constructor() {
    private val kanal = Channel<String>(Channel.BUFFERED)
    val fluss: Flow<String> = kanal.receiveAsFlow()

    fun senden(text: String) {
        kanal.trySend(text)
    }
}

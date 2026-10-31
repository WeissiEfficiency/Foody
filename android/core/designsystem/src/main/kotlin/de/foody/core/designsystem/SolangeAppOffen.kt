package de.foody.core.designsystem

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.transformLatest

/** Ob die App gerade offen ist (im Vordergrund); die App-Hülle setzt es über den `ProcessLifecycleOwner`. */
object AppOffen {
    private val stand = MutableStateFlow(false)
    val offen: StateFlow<Boolean> = stand.asStateFlow()

    fun setzen(offen: Boolean) {
        stand.value = offen
    }
}

/**
 * Für `stateIn`/`shareIn` in ViewModels: Ein Zustand läuft, solange die App offen ist oder jemand zusieht, und noch
 * 5 s danach. Werte bleiben gespeichert: Zurück auf einen Bildschirm zeigt sie sofort, ohne neu zu rechnen; geändert
 * wird nur, was sich in den Daten ändert. Geht die App in den Hintergrund, ruht alles; beim Öffnen wird neu gerechnet,
 * bis dahin steht der letzte Wert.
 */
val SolangeAppOffen: SharingStarted = solangeOffen(AppOffen.offen)

/** [SolangeAppOffen] mit frei vorgegebenem [offen] (Tests). */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun solangeOffen(offen: Flow<Boolean>, nachlaufMs: Long = 5_000L): SharingStarted = object : SharingStarted {
    override fun command(subscriptionCount: StateFlow<Int>): Flow<SharingCommand> =
        combine(subscriptionCount, offen) { zuseher, app -> zuseher > 0 || app }
            .distinctUntilChanged()
            .transformLatest { aktiv ->
                if (!aktiv) delay(nachlaufMs)
                emit(if (aktiv) SharingCommand.START else SharingCommand.STOP)
            }
            .dropWhile { it == SharingCommand.STOP }
            .distinctUntilChanged()

    override fun toString() = "SolangeAppOffen"
}

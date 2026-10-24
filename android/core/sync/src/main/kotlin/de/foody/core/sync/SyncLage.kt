package de.foody.core.sync

import de.foody.core.data.db.SyncZustandEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Was die Oberfläche vom Sync zeigt: verbunden, läuft gerade, letzte Fehlerkennung (`offline`, `app_zu_alt`, …). */
data class SyncLage(val aktiv: Boolean, val laeuft: Boolean, val letzterFehler: String?)

/** Quelle der [SyncLage]; in der App aus `sync_state` und dem WorkManager-Auftrag, im Test frei vorgegeben. */
fun interface SyncLageQuelle {
    fun lage(): Flow<SyncLage>
}

/** Fügt die Zeile `sync_state` und den Laufzustand des Auftrags zusammen. */
internal fun syncLage(zustand: Flow<SyncZustandEntity?>, laeuft: Flow<Boolean>): Flow<SyncLage> =
    combine(zustand, laeuft) { z, l -> SyncLage(aktiv = z?.active == true, laeuft = l, letzterFehler = z?.lastError) }
        .distinctUntilChanged()

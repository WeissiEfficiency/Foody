package de.foody.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.SYNC_CALLBACK

/** In-Memory-Datenbank wie in der App: mit [SYNC_CALLBACK] (Zeile `sync_state` und Trigger). */
fun syncTestDb(): FoodyDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FoodyDatabase::class.java)
        .addCallback(FoodyDatabase.SYNC_CALLBACK)
        .build()

/** Schaltet den Sync ein (nur `active = 1`, sonst nichts). */
suspend fun FoodyDatabase.activateSyncForTest() {
    val dao = syncDao()
    dao.upsertState((dao.getState() ?: error("sync_state fehlt")).copy(active = true))
}

package de.foody.app.data.repo

import androidx.room.withTransaction
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.TagebuchDao
import de.foody.app.data.db.TagebuchEintragEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagebuchRepository @Inject constructor(private val db: FoodyDatabase, private val dao: TagebuchDao) {
    fun observeRange(start: LocalDate, end: LocalDate) = dao.observeRange(start, end)
    suspend fun get(id: String) = dao.get(id)
    suspend fun save(e: TagebuchEintragEntity) = dao.upsert(e.copy(updatedAt = System.currentTimeMillis()))
    suspend fun delete(id: String) = dao.delete(id)

    /**
     * Übernimmt einen Plan-Eintrag als gegessen – höchstens einmal: Prüfen und Schreiben in einer Transaktion,
     * damit ein doppelter Tipp keinen zweiten Eintrag erzeugt. `false`, wenn er schon übernommen war.
     */
    suspend fun uebernehmen(planEintragId: String, eintrag: TagebuchEintragEntity): Boolean = db.withTransaction {
        if (dao.existsForPlan(planEintragId)) return@withTransaction false
        dao.upsert(eintrag.copy(planEintragId = planEintragId, updatedAt = System.currentTimeMillis()))
        true
    }
}

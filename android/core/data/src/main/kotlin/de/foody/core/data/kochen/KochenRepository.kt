package de.foody.core.data.kochen

import androidx.room.withTransaction
import de.foody.core.data.db.FoodyDatenbank
import java.time.LocalDate

/** Was nach dem Kochen in die Daten geht. */
class KochenRepository(
    private val db: FoodyDatenbank,
    private val uhr: () -> Long,
) {
    /**
     * Markiert den ersten heutigen, noch nicht gekochten Plan-Eintrag von [rezeptId] als gekocht (zählt für „Oft
     * gekocht“); der Sync-Trigger von `meal_slot` trägt die Änderung in die Outbox. `false`, wenn es keinen gibt.
     */
    suspend fun gekochtMarkieren(rezeptId: String, heute: LocalDate): Boolean = db.withTransaction {
        val eintrag = db.planEintragDao().offeneAm(rezeptId, heute).firstOrNull() ?: return@withTransaction false
        db.planEintragDao().gekochtSetzen(eintrag.id, uhr())
        true
    }
}

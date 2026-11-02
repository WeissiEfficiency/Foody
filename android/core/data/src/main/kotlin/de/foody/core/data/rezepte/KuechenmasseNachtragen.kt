package de.foody.core.data.rezepte

import androidx.room.withTransaction
import de.foody.core.data.db.FoodyDatenbank
import de.foody.domain.KuechenmassNachtrag
import javax.inject.Inject

/**
 * Stellt Rezeptzeilen von vor Regel 30 um („1 Prise“ nach Bedarf → 0,5 g, „2 Stk. Msp.“ → 1 g, „1 Stk. Beutel“ →
 * 1 Pck.), siehe [KuechenmassNachtrag]. Läuft bei jedem Start: Umgestellte Zeilen passen nicht mehr, und so erwischt
 * er auch alte Zeilen, die erst später ankommen (Sicherung, Sync von einem Gerät mit älterer App). Wie beim
 * Zusammenführen von Zutaten werden nur die Zeilen geschrieben; die Trigger merken die Rezepte für den Sync vor.
 */
class KuechenmasseNachtragen @Inject constructor(private val db: FoodyDatenbank) {
    /** Anzahl der umgestellten Zeilen. */
    suspend fun nachtragen(): Int = db.withTransaction {
        val dao = db.rezeptZutatDao()
        var umgestellt = 0
        for (z in dao.alle()) {
            val neu = KuechenmassNachtrag.umstellen(z.amount, z.unit, z.preparationNote) ?: continue
            dao.upsert(z.copy(amount = neu.menge, unit = neu.einheit, preparationNote = neu.hinweis))
            umgestellt++
        }
        umgestellt
    }
}

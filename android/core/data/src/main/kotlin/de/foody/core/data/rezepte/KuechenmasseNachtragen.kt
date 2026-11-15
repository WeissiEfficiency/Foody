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
    /**
     * Anzahl der umgestellten Zeilen. Sucht ohne Transaktion (meist ist nichts zu tun, der Start soll keine Schreibsperre
     * halten) und schreibt nur die Treffer, jeweils frisch gelesen.
     */
    suspend fun nachtragen(): Int {
        val dao = db.rezeptZutatDao()
        val treffer = dao.alle().filter { KuechenmassNachtrag.umstellen(it.amount, it.unit, it.preparationNote) != null }.map { it.id }
        if (treffer.isEmpty()) return 0
        return db.withTransaction {
            var umgestellt = 0
            for (id in treffer) {
                // Frisch lesen: zwischen Suchen und Schreiben kann der Sync die Zeile geändert haben
                val z = dao.nachId(id) ?: continue
                val neu = KuechenmassNachtrag.umstellen(z.amount, z.unit, z.preparationNote) ?: continue
                dao.upsert(z.copy(amount = neu.menge, unit = neu.einheit, preparationNote = neu.hinweis))
                umgestellt++
            }
            umgestellt
        }
    }
}

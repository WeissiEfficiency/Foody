package de.foody.core.data.bls

import androidx.room.withTransaction
import de.foody.core.data.db.FoodyDatenbank
import de.foody.domain.BlsDichte
import de.foody.domain.NutrientBasis
import javax.inject.Inject

/**
 * Trägt bei Zutaten, die vor Version 3.6 aus dem BLS übernommen wurden, die Dichte nach ([BlsDichte]), damit sie sich
 * auch in ml, EL und TL rechnen. Betroffen sind nur Zutaten mit BLS-Herkunft, Werten je 100 g und ohne Dichte; eine im
 * Katalog gesetzte Dichte bleibt. Läuft bei jedem Start wie [de.foody.core.data.rezepte.KuechenmasseNachtragen] und
 * erwischt so auch Zutaten aus einer Sicherung oder vom Sync. Den Auszug liest es nur, wenn es Kandidaten gibt.
 */
class BlsDichteNachtragen(private val db: FoodyDatenbank, private val bls: BlsNachschlag, private val uhr: () -> Long) {
    @Inject constructor(db: FoodyDatenbank, bls: BlsNachschlag) : this(db, bls, System::currentTimeMillis)

    /** Anzahl der Zutaten, die eine Dichte bekommen haben. */
    suspend fun nachtragen(): Int {
        val kandidaten = db.zutatDao().alle().filter {
            it.densityGPerMl == null && it.nutrientBasis == NutrientBasis.PER_100_G && it.nutrientSource?.startsWith(BLS_QUELLE) == true
        }
        if (kandidaten.isEmpty()) return 0
        val lebensmittel = bls.nachNamen(kandidaten.mapNotNull { it.nutrientSource?.removePrefix(BLS_QUELLE) }.toSet())
        val jetzt = uhr()
        return db.withTransaction {
            var geaendert = 0
            for (k in kandidaten) {
                val dichte = lebensmittel[k.nutrientSource?.removePrefix(BLS_QUELLE)]?.let(BlsDichte::gProMl) ?: continue
                // Frisch lesen: zwischen Suchen und Schreiben kann der Sync die Zeile geändert haben
                val aktuell = db.zutatDao().nachId(k.id)?.takeIf { it.densityGPerMl == null } ?: continue
                db.zutatDao().upsert(aktuell.copy(densityGPerMl = dichte, updatedAt = jetzt))
                geaendert++
            }
            geaendert
        }
    }
}

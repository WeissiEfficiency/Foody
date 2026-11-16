package de.foody.core.data.bls

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.core.data.Einstellungsschluessel
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.ZutatEntity
import de.foody.domain.BlsDichte
import de.foody.domain.NutrientBasis
import javax.inject.Inject

/**
 * Trägt bei Zutaten, die vor Version 3.6 aus dem BLS übernommen wurden, die Dichte nach ([BlsDichte]), damit sie sich
 * auch in ml, EL und TL rechnen. Betroffen sind nur Zutaten mit BLS-Herkunft, Werten je 100 g und ohne Dichte; eine im
 * Katalog gesetzte Dichte bleibt. Läuft bei jedem Start wie [de.foody.core.data.rezepte.KuechenmasseNachtragen] und
 * erwischt so auch Zutaten aus einer Sicherung oder vom Sync. Den Auszug liest es nur, wenn es ungeprüfte Kandidaten
 * gibt: Was ohne Ergebnis geprüft wurde (Festes), merkt es sich mit `updatedAt` in den Prefs
 * ([Einstellungsschluessel.BLS_DICHTE_GEPRUEFT]); eine geänderte Zeile wird wieder geprüft.
 */
class BlsDichteNachtragen(
    private val db: FoodyDatenbank,
    private val bls: BlsNachschlag,
    private val prefs: SharedPreferences,
    private val uhr: () -> Long,
) {
    @Inject constructor(db: FoodyDatenbank, bls: BlsNachschlag, @ApplicationContext kontext: Context) :
        this(db, bls, kontext.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE), System::currentTimeMillis)

    /** Anzahl der Zutaten, die eine Dichte bekommen haben. */
    suspend fun nachtragen(): Int {
        val kandidaten = db.zutatDao().ohneDichteAus(BLS_QUELLE).filter { it.nutrientBasis == NutrientBasis.PER_100_G }
        val geprueft = prefs.getStringSet(Einstellungsschluessel.BLS_DICHTE_GEPRUEFT, null).orEmpty()
        val offen = kandidaten.filter { schluessel(it) !in geprueft }
        if (offen.isEmpty()) {
            merken(kandidaten, geprueft)
            return 0
        }
        val lebensmittel = bls.nachNamen(offen.mapNotNull { it.nutrientSource?.removePrefix(BLS_QUELLE) }.toSet())
        val jetzt = uhr()
        val bekommen = db.withTransaction {
            buildSet {
                for (k in offen) {
                    val dichte = lebensmittel[k.nutrientSource?.removePrefix(BLS_QUELLE)]?.let(BlsDichte::gProMl) ?: continue
                    // Frisch lesen: zwischen Suchen und Schreiben kann der Sync die Zeile geändert haben
                    val aktuell = db.zutatDao().nachId(k.id)?.takeIf { it.densityGPerMl == null } ?: continue
                    db.zutatDao().upsert(aktuell.copy(densityGPerMl = dichte, updatedAt = jetzt))
                    add(k.id)
                }
            }
        }
        merken(kandidaten.filter { it.id !in bekommen }, geprueft)
        return bekommen.size
    }

    /** Merkt sich genau die [ohneErgebnis] geprüften Zeilen; Gelöschtes und Geändertes fällt so heraus. */
    private fun merken(ohneErgebnis: List<ZutatEntity>, vorher: Set<String>) {
        val neu = ohneErgebnis.mapTo(HashSet(), ::schluessel)
        if (neu != vorher) prefs.edit { putStringSet(Einstellungsschluessel.BLS_DICHTE_GEPRUEFT, neu) }
    }

    // Mit [STAND]: Erkennt BlsDichte später mehr, wird alles einmal neu geprüft (STAND erhöhen)
    private fun schluessel(z: ZutatEntity) = "$STAND:${z.id}@${z.updatedAt}"

    private companion object {
        const val STAND = 1
    }
}

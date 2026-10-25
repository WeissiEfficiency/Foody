package de.foody.core.data.startdaten

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.core.data.Einstellungsschluessel
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.ZutatEntity
import javax.inject.Inject

/**
 * Legt fehlende Startzutaten an, aktualisiert unveränderte Starteinträge und ergänzt gleichnamige eigene Zutaten ohne
 * Nährwerte – je [Startdaten.VERSION] einmal, wie die v1 (`seedVersion` in den Prefs `foody`, alt `seeded` = 1).
 * Die v1 glich vorher noch alte Schreibweisen ab; das braucht die V3 nicht: Umsteiger haben Version 8 schon.
 */
class StartdatenLader @Inject constructor(
    private val db: FoodyDatenbank,
    @ApplicationContext private val kontext: Context,
) {
    /** Anzahl angelegter oder geänderter Zutaten; 0, wenn die Version schon geladen ist. */
    suspend fun ladenWennNoetig(): Int {
        val prefs = kontext.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)
        val geladen = prefs.getInt(SEED_VERSION, if (prefs.getBoolean(SEEDED_ALT, false)) 1 else 0)
        if (geladen >= Startdaten.VERSION) return 0
        val dao = db.zutatDao()
        var geaendert = 0
        db.withTransaction {
            for (start in Startdaten.zutaten(System.currentTimeMillis())) {
                // Auch über die ID: Ein umbenannter Starteintrag (v1 oder Sync) darf nicht überschrieben werden
                val vorhanden = dao.nachNameOhneGross(start.canonicalName) ?: dao.nachId(start.id)
                val neu = when {
                    vorhanden == null -> start
                    // Unveränderter Starteintrag (gleicher Name, Startwerte): Korrekturen der Startdaten übernehmen
                    vorhanden.id == start.id && vorhanden.canonicalName.equals(start.canonicalName, ignoreCase = true) &&
                        vorhanden.nutrientSource in setOf(Startdaten.QUELLE, start.nutrientSource) ->
                        start.copy(createdAt = vorhanden.createdAt, version = vorhanden.version)
                    else -> vorhanden.ergaenztAus(start)
                }
                if (neu != vorhanden) {
                    dao.upsert(neu)
                    geaendert++
                }
            }
        }
        prefs.edit().putInt(SEED_VERSION, Startdaten.VERSION).apply()
        return geaendert
    }

    /** Vergisst die geladene Version (nach „Alle Daten löschen“): [ladenWennNoetig] spielt den Katalog dann neu ein. */
    fun versionZuruecksetzen() {
        kontext.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE).edit()
            .putInt(SEED_VERSION, 0).remove(SEEDED_ALT).commit()
    }

    private companion object {
        const val SEED_VERSION = "seedVersion"
        const val SEEDED_ALT = "seeded"
    }
}

/** Füllt nur Lücken: Nährwerte, wenn keine da sind, sonst Kategorie, Dichte, Stückgewicht, Strichcode (wie v1 `fillFrom`). */
private fun ZutatEntity.ergaenztAus(start: ZutatEntity): ZutatEntity {
    val mitWerten = if (nutrientBasis == null && start.nutrientBasis != null) {
        copy(
            nutrientBasis = start.nutrientBasis, energyKj = start.energyKj, protein = start.protein, carbs = start.carbs,
            fat = start.fat, fiber = start.fiber, sugar = start.sugar, salt = start.salt, nutrientSource = start.nutrientSource,
        )
    } else {
        this
    }
    return mitWerten.copy(
        category = category ?: start.category,
        densityGPerMl = densityGPerMl ?: start.densityGPerMl,
        pieceWeightG = pieceWeightG ?: start.pieceWeightG,
        barcode = barcode ?: start.barcode,
    )
}

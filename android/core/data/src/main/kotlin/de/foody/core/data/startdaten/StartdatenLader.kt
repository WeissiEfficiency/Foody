package de.foody.core.data.startdaten

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.core.data.Einstellungsschluessel
import de.foody.core.data.Einstellungsschluessel.SEED_VERSION
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.zutaten.ergaenztUm
import java.time.Clock
import javax.inject.Inject

/**
 * Legt fehlende Startzutaten an, aktualisiert unveränderte Starteinträge und ergänzt gleichnamige eigene Zutaten ohne
 * Nährwerte – je [Startdaten.VERSION] einmal, wie die v1 (`seedVersion` in den Prefs `foody`).
 * Die v1 glich vorher noch alte Schreibweisen ab; das braucht die V3 nicht: Umsteiger haben Version 8 schon.
 */
class StartdatenLader @Inject constructor(
    private val db: FoodyDatenbank,
    @ApplicationContext private val kontext: Context,
    /** Zeitstempel der angelegten Zutaten (aus [de.foody.core.data.DatenModul.uhr]). */
    private val uhr: Clock = Clock.systemDefaultZone(),
) {
    /** Anzahl angelegter oder geänderter Zutaten; 0, wenn die Version schon geladen ist. */
    suspend fun ladenWennNoetig(): Int {
        val prefs = kontext.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)
        val geladen = prefs.getInt(SEED_VERSION, 0)
        if (geladen >= Startdaten.VERSION) return 0
        val dao = db.zutatDao()
        var geaendert = 0
        db.withTransaction {
            for (start in Startdaten.zutaten(uhr.millis())) {
                // Auch über die ID: Ein umbenannter Starteintrag (v1 oder Sync) darf nicht überschrieben werden
                val vorhanden = dao.nachNameOhneGross(start.canonicalName) ?: dao.nachId(start.id)
                val neu = when {
                    vorhanden == null -> start
                    // Starteintrag mit Startwerten (gleicher Name, Startquelle): Korrekturen der Nährwerte übernehmen;
                    // Kategorie, Dichte, Stückgewicht und Strichcode können im Katalog bearbeitet sein und bleiben
                    vorhanden.id == start.id && vorhanden.canonicalName.equals(start.canonicalName, ignoreCase = true) &&
                        vorhanden.nutrientSource in setOf(Startdaten.QUELLE, start.nutrientSource) ->
                        start.copy(
                            createdAt = vorhanden.createdAt, version = vorhanden.version,
                            category = vorhanden.category ?: start.category,
                            densityGPerMl = vorhanden.densityGPerMl ?: start.densityGPerMl,
                            pieceWeightG = vorhanden.pieceWeightG ?: start.pieceWeightG,
                            barcode = vorhanden.barcode ?: start.barcode,
                        )
                    else -> vorhanden.ergaenztUm(start)
                }
                if (neu != vorhanden) {
                    dao.upsert(neu)
                    geaendert++
                }
            }
        }
        prefs.edit { putInt(SEED_VERSION, Startdaten.VERSION) }
        return geaendert
    }

    /**
     * Vergisst die geladene Version (vor „Alle Daten löschen“): [ladenWennNoetig] spielt den Katalog dann neu ein.
     * Gibt die bisherige Version zurück, für [versionWiederherstellen].
     */
    fun versionZuruecksetzen(): Int {
        val prefs = kontext.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)
        val vorher = prefs.getInt(SEED_VERSION, 0)
        prefs.edit(commit = true) { putInt(SEED_VERSION, 0) }
        return vorher
    }

    /** Setzt die Version zurück auf [vorher] (Löschen war gesperrt, es wurde nichts geleert). */
    fun versionWiederherstellen(vorher: Int) {
        kontext.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE).edit(commit = true) { putInt(SEED_VERSION, vorher) }
    }
}

package de.foody.core.data.tagebuch

import androidx.room.withTransaction
import de.foody.core.data.alsIngredient
import de.foody.core.data.alsRecipe
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.Naehrwerte
import de.foody.domain.Tagebuch
import de.foody.domain.TagebuchArt
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Schreiben und Lesen des Tagebuchs. Werte werden beim Eintragen aus Rezept bzw. Zutat berechnet und festgehalten;
 * spätere Änderungen am Rezept ändern alte Einträge nie (ADR 0007). Ändern skaliert nur im Verhältnis.
 */
class TagebuchRepository(private val db: FoodyDatenbank, private val uhr: () -> Long) {
    private val dao = db.tagebuchDao()

    fun amTag(datum: LocalDate): Flow<List<TagebuchEintragEntity>> = dao.amTag(datum)

    /** Neue ID; `null`, wenn es das Rezept nicht (mehr) gibt. */
    suspend fun ausRezept(datum: LocalDate, mahlzeit: Mahlzeit, rezeptId: String, portionen: BigDecimal): String? {
        val (name, werte) = rezeptWerte(rezeptId, portionen) ?: return null
        return speichern(neu(datum, mahlzeit, TagebuchArt.REZEPT, name, werte).copy(rezeptId = rezeptId, portionen = portionen))
    }

    /** Neue ID; ohne Nährwerte oder bei nicht umrechenbarer Einheit unvollständig ohne Werte. `null` ohne Zutat. */
    suspend fun ausZutat(datum: LocalDate, mahlzeit: Mahlzeit, zutatId: String, menge: BigDecimal, einheit: MeasureUnit): String? {
        val zutat = db.zutatDao().nachId(zutatId) ?: return null
        val werte = Tagebuch.naehrwerteZutat(zutat.alsIngredient(), menge, einheit) ?: Naehrwerte(null, null, null, null, false)
        val e = neu(datum, mahlzeit, TagebuchArt.ZUTAT, zutat.canonicalName, werte)
        return speichern(e.copy(zutatId = zutatId, menge = menge, einheit = einheit))
    }

    /**
     * Übernimmt einen Plan-Eintrag als gegessen: eine Portion, höchstens einmal (wie v1) – Prüfen und Schreiben in
     * einer Transaktion, damit ein doppelter Tipp keinen zweiten Eintrag erzeugt. `null`, wenn schon übernommen oder
     * Plan-Eintrag bzw. Rezept fehlen.
     */
    suspend fun ausPlan(planEintragId: String): String? = db.withTransaction {
        if (dao.gibtEsFuerPlan(planEintragId)) return@withTransaction null
        val p = db.planEintragDao().nachId(planEintragId) ?: return@withTransaction null
        val (name, werte) = rezeptWerte(p.recipeId, BigDecimal.ONE) ?: return@withTransaction null
        val mahlzeit = Mahlzeit.ausText(p.slotType) ?: Mahlzeit.ABENDESSEN
        val e = neu(p.date, mahlzeit, TagebuchArt.REZEPT, name, werte)
        speichern(e.copy(rezeptId = p.recipeId, planEintragId = planEintragId, portionen = BigDecimal.ONE))
    }

    suspend fun frei(
        datum: LocalDate, mahlzeit: Mahlzeit, name: String, kcal: Int,
        eiweiss: BigDecimal?, kohlenhydrate: BigDecimal?, fett: BigDecimal?,
    ): String = speichern(neu(datum, mahlzeit, TagebuchArt.FREI, name.trim(), Tagebuch.freiWerte(kcal, eiweiss, kohlenhydrate, fett)))

    /**
     * Ändert Menge (Portionen bzw. Zutatmenge), Mahlzeit und Tag. Rezept- und Zutatwerte werden im Verhältnis
     * skaliert ([Tagebuch.skaliert]); freie Einträge übernehmen [freiWerte], wenn angegeben.
     */
    suspend fun aendern(id: String, menge: BigDecimal?, mahlzeit: Mahlzeit, datum: LocalDate, freiWerte: Naehrwerte?) {
        db.withTransaction {
            val alt = dao.nachId(id) ?: return@withTransaction
            val werte = Naehrwerte(alt.energieKj, alt.eiweiss, alt.kohlenhydrate, alt.fett, alt.vollstaendig)
            val neu = when (alt.art) {
                TagebuchArt.REZEPT -> alt.mit(Tagebuch.skaliert(alt.art, werte, alt.portionen, menge)).copy(portionen = menge ?: alt.portionen)
                TagebuchArt.ZUTAT -> alt.mit(Tagebuch.skaliert(alt.art, werte, alt.menge, menge)).copy(menge = menge ?: alt.menge)
                TagebuchArt.FREI -> alt.mit(freiWerte ?: werte)
            }
            dao.upsert(neu.copy(mahlzeit = mahlzeit.name, datum = datum, updatedAt = uhr()))
        }
    }

    /** Löscht und liefert den Eintrag für „Rückgängig“. */
    suspend fun loeschen(id: String): TagebuchEintragEntity? = db.withTransaction {
        dao.nachId(id)?.also { dao.loeschen(id) }
    }

    suspend fun wiederherstellen(e: TagebuchEintragEntity) = dao.upsert(e)

    private suspend fun rezeptWerte(rezeptId: String, portionen: BigDecimal): Pair<String, Naehrwerte>? {
        val r = db.rezeptDao().nachId(rezeptId) ?: return null
        val katalog = db.zutatDao().alle().associate { it.id to it.alsIngredient() }
        val rezept = r.alsRecipe(db.rezeptZutatDao().nachRezept(rezeptId))
        return r.name to Tagebuch.naehrwerteRezept(rezept, katalog, portionen)
    }

    private fun neu(datum: LocalDate, mahlzeit: Mahlzeit, art: TagebuchArt, name: String, w: Naehrwerte): TagebuchEintragEntity {
        val jetzt = uhr()
        return TagebuchEintragEntity(
            id = UUID.randomUUID().toString(), datum = datum, mahlzeit = mahlzeit.name, art = art, name = name,
            energieKj = w.energieKj, eiweiss = w.eiweiss, kohlenhydrate = w.kohlenhydrate, fett = w.fett,
            vollstaendig = w.vollstaendig, createdAt = jetzt, updatedAt = jetzt,
        )
    }

    private suspend fun speichern(e: TagebuchEintragEntity): String {
        dao.upsert(e)
        return e.id
    }
}

private fun TagebuchEintragEntity.mit(w: Naehrwerte) =
    copy(energieKj = w.energieKj, eiweiss = w.eiweiss, kohlenhydrate = w.kohlenhydrate, fett = w.fett, vollstaendig = w.vollstaendig)

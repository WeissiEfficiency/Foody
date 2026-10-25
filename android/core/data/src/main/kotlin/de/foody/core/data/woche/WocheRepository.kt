package de.foody.core.data.woche

import androidx.room.withTransaction
import de.foody.core.data.alsIngredient
import de.foody.core.data.alsRecipe
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.domain.DayNutrition
import de.foody.domain.Mahlzeit
import de.foody.domain.PlanDurchschnitt
import de.foody.domain.PlanTag
import de.foody.domain.RecipeProfiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/**
 * Der Wochenplan: lesen, ändern, „Leere Tage füllen“. kcal und Nährwerte über dieselben Abbildungen wie Karte,
 * Detail und „Heute“; Tageswerte je Person ([DayNutrition]).
 */
class WocheRepository(
    private val db: FoodyDatenbank,
    private val rezepte: RezeptRepository,
    private val uhr: () -> Long,
) {
    /** Die sieben Tage ab [montag]; neu bei jeder Änderung an Plan, Rezepten, Zeilen oder Zutaten. */
    fun woche(montag: LocalDate): Flow<WochenAnsicht> = combine(
        db.planEintragDao().zwischen(montag, montag.plusDays(6)),
        db.rezeptDao().alleFlow(),
        db.rezeptZutatDao().alleFlow(),
        db.zutatDao().alleFlow(),
    ) { plan, rezeptListe, zeilen, zutaten ->
        val katalog = zutaten.associate { it.id to it.alsIngredient() }
        val rezeptNach = rezeptListe.associateBy { it.id }
        val zeilenJe = zeilen.groupBy { it.recipeId }
        val recipe = rezeptListe.associate { it.id to it.alsRecipe(zeilenJe[it.id].orEmpty()) }
        val tage = (0L..6L).map { versatz ->
            val datum = montag.plusDays(versatz)
            // Mit CASCADE verschwinden Einträge mit ihrem Rezept; ohne Rezept gibt es keinen Eintrag
            val eintraege = plan.filter { it.date == datum && it.recipeId in rezeptNach }
                .sortedWith(compareBy({ mahlzeitReihenfolge(it.slotType) }, { it.slotType.lowercase() }, { it.id }))
            WochenTag(
                datum,
                eintraege.map { p ->
                    val r = rezeptNach.getValue(p.recipeId)
                    PlanEintrag(
                        p.id, p.date, p.slotType, r.id, r.name, r.imageUri,
                        RecipeProfiles.profile(recipe.getValue(r.id), katalog).kcalPerServing, p.servings, p.cookedAt != null,
                    )
                },
                DayNutrition.of(eintraege.map { recipe.getValue(it.recipeId) }, katalog),
            )
        }
        val durchschnitt = PlanDurchschnitt.kcal(
            tage.mapNotNull { t -> t.naehrwerte?.let { PlanTag(t.eintraege.map { it.mahlzeit }, it) } },
        )
        WochenAnsicht(montag, tage, durchschnitt)
    }

    /** Rezepte für die Auswahl beim Hinzufügen: die Karten ohne archivierte. */
    fun rezeptAuswahl(): Flow<List<RezeptKarte>> = rezepte.karten().map { k -> k.filterNot { it.archiviert } }

    /** Legt einen Eintrag an; Portionen 1–99, Name wie [umbenennen]. */
    suspend fun hinzufuegen(datum: LocalDate, mahlzeit: String, rezeptId: String, portionen: Int): String {
        val jetzt = uhr()
        val e = PlanEintragEntity(
            UUID.randomUUID().toString(), datum, mahlzeitText(mahlzeit), rezeptId, portionen.coerceIn(MIN_PORTIONEN, MAX_PORTIONEN),
            createdAt = jetzt, updatedAt = jetzt,
        )
        db.planEintragDao().upsert(e)
        return e.id
    }

    suspend fun portionen(id: String, n: Int) = aendern(id) { it.copy(servings = n.coerceIn(MIN_PORTIONEN, MAX_PORTIONEN)) }

    suspend fun verschieben(id: String, datum: LocalDate) = aendern(id) { it.copy(date = datum) }

    /** Getrimmt, 1–30 Zeichen; ein bekannter Name („abendessen“) wird als Mahlzeit gespeichert. */
    suspend fun umbenennen(id: String, mahlzeit: String) {
        val text = mahlzeitText(mahlzeit)
        aendern(id) { it.copy(slotType = text) }
    }

    /** Entfernt den Eintrag und gibt ihn für „Rückgängig“ zurück; `null`, wenn es ihn nicht gab. */
    suspend fun entfernen(id: String): PlanEintragEntity? = db.withTransaction {
        db.planEintragDao().nachId(id)?.also { db.planEintragDao().loeschen(id) }
    }

    /** Legt einen entfernten Eintrag mit derselben ID wieder an. */
    suspend fun wiederherstellen(e: PlanEintragEntity) = db.planEintragDao().upsert(e.copy(updatedAt = uhr()))

    /** Setzt bzw. löscht `cookedAt` (zählt für „Oft gekocht“). */
    suspend fun gekocht(id: String, an: Boolean) {
        val jetzt = uhr()
        aendern(id) { it.copy(cookedAt = jetzt.takeIf { an }) }
    }

    private suspend fun aendern(id: String, f: (PlanEintragEntity) -> PlanEintragEntity) = db.withTransaction {
        val e = db.planEintragDao().nachId(id) ?: return@withTransaction
        db.planEintragDao().upsert(f(e).copy(updatedAt = uhr()))
    }

    private companion object {
        const val MIN_PORTIONEN = 1
        const val MAX_PORTIONEN = 99
    }
}

private const val MAX_NAME = 30

/** Gespeicherter Mahlzeit-Text: bekannte Namen als `Mahlzeit.name`, sonst getrimmt (1–30 Zeichen). */
internal fun mahlzeitText(eingabe: String): String {
    val t = eingabe.trim()
    require(t.length in 1..MAX_NAME) { "Mahlzeit muss 1–$MAX_NAME Zeichen haben" }
    return Mahlzeit.ausText(t)?.name ?: t
}

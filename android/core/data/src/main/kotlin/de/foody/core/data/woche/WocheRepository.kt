package de.foody.core.data.woche

import de.foody.core.data.alsIngredient
import de.foody.core.data.alsRecipe
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.domain.DayNutrition
import de.foody.domain.PlanDurchschnitt
import de.foody.domain.PlanTag
import de.foody.domain.RecipeProfiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

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
}

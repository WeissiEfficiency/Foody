package de.foody.core.data.rezepte

import de.foody.core.data.alsIngredient
import de.foody.core.data.alsRecipe
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.domain.Einordnung
import de.foody.domain.Gang
import de.foody.domain.Ingredient
import de.foody.domain.Mahlzeit
import de.foody.domain.NutritionCalculator
import de.foody.domain.RecipeProfiles
import de.foody.domain.RezeptEinordnung
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Rezepte lesen und schreiben. kcal und Nährwerte immer über `:domain` mit denselben Abbildungen wie „Heute“
 * (`DomainAbbildung`), damit Karte, Detail und „Heute“ dieselbe Zahl zeigen.
 */
class RezeptRepository(
    private val db: FoodyDatenbank,
    private val ablage: FotoAblage,
    private val uhr: () -> Long,
) {
    /** Alle Rezepte (auch archivierte); neu berechnet bei jeder Änderung an Rezepten, Zeilen, Zutaten oder Plan. */
    fun karten(): Flow<List<RezeptKarte>> = combine(
        db.rezeptDao().alleFlow(),
        db.rezeptZutatDao().alleFlow(),
        db.zutatDao().alleFlow(),
        db.planEintragDao().gekochtJeRezept(),
    ) { rezepte, zeilen, zutaten, gekocht ->
        val katalog = zutaten.associate { it.id to it.alsIngredient() }
        val jeRezept = zeilen.groupBy { it.recipeId }
        val gekochtJe = gekocht.associate { it.recipeId to it.anzahl }
        rezepte.map { it.karte(jeRezept[it.id].orEmpty(), katalog, gekochtJe[it.id] ?: 0) }
    }

    /** Ein Rezept vollständig; `null`, wenn es die ID nicht (mehr) gibt. */
    fun voll(id: String): Flow<RezeptVoll?> = combine(
        db.rezeptDao().nachIdFlow(id),
        db.rezeptZutatDao().nachRezeptFlow(id),
        db.arbeitsschrittDao().nachRezeptFlow(id),
        db.zutatDao().alleFlow(),
    ) { rezept, zeilen, schritte, zutaten ->
        rezept ?: return@combine null
        val katalog = zutaten.associate { it.id to it.alsIngredient() }
        RezeptVoll(
            rezept = rezept,
            zeilen = zeilen.map { z ->
                val zutat = katalog[z.ingredientId]
                RezeptZeile(z.ingredientId, zutat?.name.orEmpty(), z.amount, z.unit, z.preparationNote, z.optional, zutat?.nutrients != null)
            },
            schritte = schritte.map { it.text },
            naehrwerte = NutritionCalculator.calculate(rezept.alsRecipe(zeilen), katalog),
            einordnung = rezept.einordnung(),
        )
    }
}

internal fun RezeptEntity.einordnung(): Einordnung =
    RezeptEinordnung.einordnen(name, tagsAus(tags), Mahlzeit.mengeAus(mahlzeiten), Gang.mengeAus(gaenge))

private fun RezeptEntity.karte(zeilen: List<RezeptZutatEntity>, katalog: Map<String, Ingredient>, gekocht: Int): RezeptKarte {
    val profil = RecipeProfiles.profile(alsRecipe(zeilen), katalog)
    return RezeptKarte(
        id = id,
        name = name,
        bild = imageUri,
        kcalJePortion = profil.kcalPerServing,
        eiweissJePortion = profil.proteinPerServing,
        zeitMinuten = listOfNotNull(prepMinutes, cookMinutes).takeIf { it.isNotEmpty() }?.sum(),
        favorit = favorite,
        gekocht = gekocht,
        diaeten = profil.diets,
        einordnung = einordnung(),
        tags = tagsAus(tags),
        zutatNamen = zeilen.mapNotNull { katalog[it.ingredientId]?.name },
        archiviert = archivedAt != null,
        angelegt = createdAt,
    )
}

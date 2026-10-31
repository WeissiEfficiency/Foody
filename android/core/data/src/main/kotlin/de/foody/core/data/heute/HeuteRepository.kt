package de.foody.core.data.heute

import de.foody.core.data.alsIngredient
import de.foody.core.data.alsRecipe
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.domain.Ingredient
import de.foody.domain.Quantity
import de.foody.domain.QuantityFormatter
import de.foody.domain.RecipeProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/** Ein geplantes Gericht für „Heute“ (nur mit vorhandenem, nicht archiviertem Rezept). */
data class GeplanteMahlzeit(
    val planEintragId: String,
    val datum: LocalDate,
    val slot: String,
    val rezeptId: String,
    val titel: String,
    val portionen: Int,
    val kcalJePortion: Int?,
    val zeitMinuten: Int?,
    val bild: String?,
    val gekocht: Boolean,
)

/** Rezept in Kurzform für Vorschläge. */
data class RezeptKurz(val id: String, val titel: String, val kcalJePortion: Int?, val zeitMinuten: Int?, val bild: String?)

data class ArtikelKurz(val name: String, val menge: String?)

/** Offene Artikel der zuletzt geänderten Einkaufsliste. */
data class OffenerEinkauf(val listeId: String, val anzahl: Int, val artikel: List<ArtikelKurz>)

/**
 * Lesezugriffe für den Bereich „Heute“. [rechnen]: wo die kcal aller Rezepte entstehen – nie auf dem Haupt-Thread.
 */
class HeuteRepository(private val db: FoodyDatenbank, private val rechnen: CoroutineContext) {
    @Inject constructor(db: FoodyDatenbank) : this(db, Dispatchers.Default)

    /**
     * Aktive Rezepte mit kcal je Portion, neu berechnet, sobald sich Rezepte, Zeilen oder Zutaten ändern. Jeder Sammler
     * rechnet für sich; wer [planHeuteUndMorgen] und [rezeptVorschlaege] zugleich braucht, teilt diesen Flow und gibt ihn
     * beiden mit.
     */
    fun rezepteKurz(): Flow<Map<String, RezeptKurz>> = combine(
        db.rezeptDao().aktive(),
        db.rezeptZutatDao().alleFlow(),
        db.zutatDao().alleFlow(),
    ) { rezepte, zeilen, zutaten ->
        val katalog = zutaten.associate { it.id to it.alsIngredient() }
        val jeRezept = zeilen.groupBy { it.recipeId }
        rezepte.associate { it.id to it.kurz(jeRezept[it.id].orEmpty(), katalog) }
    }.flowOn(rechnen)

    fun planHeuteUndMorgen(heute: LocalDate, rezepte: Flow<Map<String, RezeptKurz>> = rezepteKurz()): Flow<List<GeplanteMahlzeit>> =
        combine(db.planEintragDao().zwischen(heute, heute.plusDays(1)), rezepte) { plan, rezepte ->
            plan.mapNotNull { p ->
                val r = rezepte[p.recipeId] ?: return@mapNotNull null
                GeplanteMahlzeit(
                    planEintragId = p.id, datum = p.date, slot = p.slotType, rezeptId = r.id, titel = r.titel,
                    portionen = p.servings, kcalJePortion = r.kcalJePortion, zeitMinuten = r.zeitMinuten, bild = r.bild,
                    gekocht = p.cookedAt != null,
                )
            }
        }

    fun rezeptVorschlaege(rezepte: Flow<Map<String, RezeptKurz>> = rezepteKurz()): Flow<List<RezeptKurz>> =
        rezepte.map { it.values.toList() }

    /** `null`, wenn die jüngste Liste nichts Offenes hat (oder es keine gibt). */
    fun offenerEinkauf(): Flow<OffenerEinkauf?> = db.einkaufsartikelDao().offeneDerJuengstenListe().map { artikel ->
        artikel.firstOrNull()?.let { erster ->
            OffenerEinkauf(
                listeId = erster.listId,
                anzahl = artikel.size,
                artikel = artikel.map { a ->
                    val menge = a.amount?.let { m -> a.unit?.let { QuantityFormatter.format(Quantity.of(m, it), it) } }
                    ArtikelKurz(a.name, menge)
                },
            )
        }
    }
}

private fun RezeptEntity.kurz(zeilen: List<RezeptZutatEntity>, katalog: Map<String, Ingredient>) = RezeptKurz(
    id = id,
    titel = name,
    kcalJePortion = RecipeProfiles.profile(alsRecipe(zeilen), katalog).kcalPerServing,
    zeitMinuten = listOfNotNull(prepMinutes, cookMinutes).takeIf { it.isNotEmpty() }?.sum(),
    bild = imageUri,
)

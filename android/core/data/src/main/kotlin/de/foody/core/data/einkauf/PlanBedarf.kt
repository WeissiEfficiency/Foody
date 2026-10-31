package de.foody.core.data.einkauf

import de.foody.core.data.alsIngredient
import de.foody.core.data.alsRecipe
import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufsartikelQuelleEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.neueId
import de.foody.domain.DateRange
import de.foody.domain.EinkaufsBedarf
import de.foody.domain.MealSlot
import de.foody.domain.MeasureUnit
import de.foody.domain.ShoppingCatalog
import de.foody.domain.ShoppingNeed
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Eine Zeile der Vorschau „Neue Liste aus dem Plan“. */
data class VorschauZeile(
    val schluessel: String,
    val name: String,
    val emoji: String,
    val abteilung: String,
    val menge: String,
    val herkunft: String,
)

/**
 * Einkaufsbedarf eines Zeitraums (ohne Vorrat, ohne Nie-Kaufen, nur was zu kaufen ist), sortiert nach Abteilung in
 * Laufreihenfolge und Name, mit allem, was Vorschau, Liste und Abgleich zum Beschriften brauchen.
 */
internal class PlanBedarf(
    val bedarf: List<ShoppingNeed>,
    private val zutaten: Map<String, ZutatEntity>,
    private val rezeptNamen: Map<String, String>,
    private val slotDaten: Map<String, LocalDate>,
) {
    fun name(n: ShoppingNeed): String = zutaten[n.ingredientId]?.canonicalName ?: n.ingredientId

    fun kategorie(n: ShoppingNeed): String? = zutaten[n.ingredientId]?.category

    fun basis(n: ShoppingNeed): MeasureUnit = MeasureUnit.baseOf(n.toBuy.dimension)

    fun mengeText(n: ShoppingNeed): String = mengeText(n.toBuy.amountIn(basis(n)), basis(n)).orEmpty()

    fun zeile(n: ShoppingNeed): VorschauZeile = VorschauZeile(
        n.key, name(n), ShoppingCatalog.emojiFor(name(n), kategorie(n)), abteilungVon(kategorie(n), name(n)),
        mengeText(n), herkunftText(quellen(n, "")).orEmpty(),
    )

    /** Herkunftszeilen eines Bedarfs für den Artikel [artikelId], Beitrag in Basiseinheit. */
    fun quellen(n: ShoppingNeed, artikelId: String): List<EinkaufsartikelQuelleEntity> = n.sources.map { s ->
        val b = MeasureUnit.baseOf(s.contributed.dimension)
        EinkaufsartikelQuelleEntity(
            neueId(), artikelId, s.mealSlotId, s.recipeIngredientId, rezeptNamen[s.recipeId].orEmpty(),
            slotDaten[s.mealSlotId] ?: LocalDate.MIN, s.contributed.amountIn(b), b,
        )
    }

    /** Neuer Plan-Artikel für [n] auf der Liste [listeId]. */
    fun artikel(n: ShoppingNeed, listeId: String, reihenfolge: Int, jetzt: Long) = EinkaufsartikelEntity(
        id = neueId(), listId = listeId, ingredientId = n.ingredientId, name = name(n),
        amount = n.toBuy.amountIn(basis(n)), unit = basis(n), manual = false, category = kategorie(n),
        sortOrder = reihenfolge, updatedAt = jetzt,
    )

    companion object {
        suspend fun laden(db: FoodyDatenbank, von: LocalDate, bis: LocalDate): PlanBedarf {
            val plan = db.planEintragDao().zwischen(von, bis).first()
            val rezepte = db.rezeptDao().alle().associateBy { it.id }
            val zeilenJe = db.rezeptZutatDao().alle().groupBy { it.recipeId }
            val zutaten = db.zutatDao().alle().associateBy { it.id }
            val slots = plan.filter { it.recipeId in rezepte }.map { MealSlot(it.id, it.date, it.slotType, it.recipeId, it.servings) }
            val bedarf = EinkaufsBedarf.berechnen(
                DateRange(von, bis),
                slots,
                rezepte.mapValues { (id, r) -> r.alsRecipe(zeilenJe[id].orEmpty()) },
                zutaten.mapValues { it.value.alsIngredient() },
            ).filter { !it.toBuy.isZero() }
            val reihenfolge = ShoppingCatalog.sectionOrder.withIndex().associate { (i, s) -> s to i }
            val namen = zutaten.mapValues { it.value.canonicalName }
            val sortiert = bedarf.sortedWith(
                compareBy(
                    { reihenfolge[abteilungVon(zutaten[it.ingredientId]?.category, namen[it.ingredientId] ?: it.ingredientId)] },
                    { (namen[it.ingredientId] ?: it.ingredientId).lowercase() },
                    { it.key },
                ),
            )
            return PlanBedarf(sortiert, zutaten, rezepte.mapValues { it.value.name }, plan.associate { it.id to it.date })
        }
    }
}

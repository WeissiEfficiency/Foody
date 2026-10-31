package de.foody.domain

/** Herkunft eines Einkaufsbedarfs. */
data class NeedSource(
    val mealSlotId: String,
    val recipeId: String,
    val recipeIngredientId: String,
    val contributed: Quantity,
)

data class ShoppingNeed(
    val ingredientId: String,
    /** Summe der Beiträge, exakt. */
    val required: Quantity,
    val sources: List<NeedSource>,
) {
    /** Was zu kaufen ist; Stück, Packungen und Dosen ganzzahlig aufgerundet (niemand kauft 0,3 Zwiebeln). */
    val toBuy: Quantity get() = if (required.dimension.countable) required.roundedUpToWhole() else required
    val key: String get() = "$ingredientId|${required.dimension}"
}

/**
 * Berechnet den Einkaufsbedarf für einen Planzeitraum.
 *
 * 1. Planpositionen im Zeitraum laden, 2. skalieren, 3. optionale Zutaten auflösen,
 * 4. in Basiseinheit umrechnen, 5. nach Zutat + Dimension gruppieren, 6. Einträge mit Herkunft erzeugen.
 * Einen Vorrat gibt es seit der V3 nicht mehr (ADR 0011).
 *
 * Die Zieldimension einer Zutat hängt nur davon ab, welche Dimensionen im Zeitraum vorkommen, nie von der
 * Reihenfolge der Planzeilen ([EINKAUFS_VORRANG]); sonst wechselt der Schlüssel nach einer Planänderung (Regeln 7 und 10).
 */
class GenerateShoppingListUseCase {
    operator fun invoke(
        range: DateRange,
        slots: List<MealSlot>,
        recipes: Map<String, Recipe>,
        ingredients: Map<String, Ingredient>,
        includeOptional: Boolean = false,
        excludedIngredientIds: Set<String> = emptySet(),
    ): List<ShoppingNeed> {
        // key = ingredientId, value = Beiträge in Planreihenfolge, noch in ihrer eigenen Dimension
        val lines = LinkedHashMap<String, MutableList<Pair<NeedSource, Quantity>>>()

        for (slot in slots.filter { it.date in range }.sortedWith(compareBy({ it.date }, { it.slotType }))) {
            val recipe = recipes[slot.recipeId] ?: continue
            for (line in recipe.ingredients) {
                if (line.optional && !includeOptional) continue
                if (line.ingredientId in excludedIngredientIds) continue
                // Menge 0 = „nach Bedarf“ (z. B. Salz) wird nie eingekauft
                if (line.amount.signum() == 0) continue
                val scaledAmount = RecipeScaler.scale(line.amount, recipe.defaultServings, slot.servings)
                val q = Quantity.of(scaledAmount, line.unit)
                lines.getOrPut(line.ingredientId) { mutableListOf() } += NeedSource(slot.id, recipe.id, line.id, q) to q
            }
        }

        val result = mutableListOf<ShoppingNeed>()
        for ((ingredientId, beitraege) in lines) {
            val info = ingredients[ingredientId]?.conversion ?: ConversionInfo()
            val ziel = zieldimensionen(beitraege.map { it.second }, info)
            val perDim = LinkedHashMap<Dimension, MutableList<NeedSource>>()
            for (dim in EINKAUFS_VORRANG) if (dim in ziel.values) perDim[dim] = mutableListOf()
            for ((source, q) in beitraege) {
                val target = ziel.getValue(q.dimension)
                perDim.getValue(target) += source.copy(contributed = UnitConverter.convert(q, target, info)!!)
            }
            for ((dim, sources) in perDim) {
                val required = sources.map { it.contributed }.fold(Quantity.zero(dim)) { a, b -> a + b }
                result += ShoppingNeed(ingredientId, required, sources)
            }
        }
        return result
    }

    /**
     * Ordnet jeder vorkommenden Dimension ihr Ziel zu: die erste nach [EINKAUFS_VORRANG], in die sie sicher umrechenbar
     * ist und die schon Ziel ist; sonst bleibt sie für sich.
     */
    private fun zieldimensionen(mengen: List<Quantity>, info: ConversionInfo): Map<Dimension, Dimension> {
        val jeDimension = mengen.associateBy { it.dimension }
        val ziele = mutableListOf<Dimension>()
        val zuordnung = mutableMapOf<Dimension, Dimension>()
        for (dim in EINKAUFS_VORRANG) {
            val menge = jeDimension[dim] ?: continue
            val ziel = ziele.firstOrNull { UnitConverter.convert(menge, it, info) != null } ?: dim.also { ziele += it }
            zuordnung[dim] = ziel
        }
        return zuordnung
    }

    companion object {
        /** Gekauft wird in Stück, Packungen und Dosen, sonst nach Gewicht, zuletzt nach Volumen. */
        val EINKAUFS_VORRANG = listOf(Dimension.COUNT, Dimension.PACKAGE, Dimension.CAN, Dimension.MASS, Dimension.VOLUME)
    }
}

enum class DiffType { ADDED, CHANGED, REMOVED, UNCHANGED }

data class ShoppingDiffEntry<T>(val type: DiffType, val key: String, val old: T?, val new: ShoppingNeed?)

/** Vergleicht einen bestehenden Snapshot mit einer Neuberechnung. */
object ShoppingDiff {
    fun <T> diff(
        old: List<T>,
        oldKey: (T) -> String,
        oldQuantity: (T) -> Quantity,
        new: List<ShoppingNeed>,
    ): List<ShoppingDiffEntry<T>> {
        val oldMap = old.associateBy(oldKey)
        val newMap = new.associateBy { it.key }
        val out = mutableListOf<ShoppingDiffEntry<T>>()
        for ((k, n) in newMap) {
            val o = oldMap[k]
            out += when {
                o == null -> ShoppingDiffEntry(DiffType.ADDED, k, null, n)
                oldQuantity(o).baseAmount.compareTo(n.toBuy.baseAmount) != 0 ->
                    ShoppingDiffEntry(DiffType.CHANGED, k, o, n)
                else -> ShoppingDiffEntry(DiffType.UNCHANGED, k, o, n)
            }
        }
        for ((k, o) in oldMap) if (k !in newMap) out += ShoppingDiffEntry(DiffType.REMOVED, k, o, null)
        return out
    }
}

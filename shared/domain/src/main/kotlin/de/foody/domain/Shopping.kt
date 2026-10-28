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
        // key = ingredientId, value = Gruppen je Dimension (Reihenfolge = erste Dimension zuerst)
        val groups = LinkedHashMap<String, LinkedHashMap<Dimension, MutableList<NeedSource>>>()

        for (slot in slots.filter { it.date in range }.sortedWith(compareBy({ it.date }, { it.slotType }))) {
            val recipe = recipes[slot.recipeId] ?: continue
            for (line in recipe.ingredients) {
                if (line.optional && !includeOptional) continue
                if (line.ingredientId in excludedIngredientIds) continue
                // Menge 0 = „nach Bedarf“ (z. B. Salz) wird nie eingekauft
                if (line.amount.signum() == 0) continue
                val scaledAmount = RecipeScaler.scale(line.amount, recipe.defaultServings, slot.servings)
                val q = Quantity.of(scaledAmount, line.unit)
                val info = ingredients[line.ingredientId]?.conversion ?: ConversionInfo()
                val perDim = groups.getOrPut(line.ingredientId) { LinkedHashMap() }
                // In eine bestehende Dimension umrechnen, sofern sicher möglich.
                val target = perDim.keys.firstOrNull { UnitConverter.convert(q, it, info) != null } ?: q.dimension
                val converted = UnitConverter.convert(q, target, info)!!
                perDim.getOrPut(target) { mutableListOf() } +=
                    NeedSource(slot.id, recipe.id, line.id, converted)
            }
        }

        val result = mutableListOf<ShoppingNeed>()
        for ((ingredientId, perDim) in groups) {
            for ((dim, sources) in perDim) {
                val required = sources.map { it.contributed }.fold(Quantity.zero(dim)) { a, b -> a + b }
                result += ShoppingNeed(ingredientId, required, sources)
            }
        }
        return result
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

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
    /** Bedarf vor Vorratsabzug. */
    val required: Quantity,
    /** Abgezogener Vorrat. */
    val fromPantry: Quantity,
    val sources: List<NeedSource>,
) {
    val toBuy: Quantity get() = required.minusClamped(fromPantry)
    val key: String get() = "$ingredientId|${required.dimension}"
}

/**
 * Berechnet den Einkaufsbedarf für einen Planzeitraum.
 *
 * 1. Planpositionen im Zeitraum laden, 2. skalieren, 3. optionale Zutaten auflösen,
 * 4. in Basiseinheit umrechnen, 5. nach Zutat + Dimension gruppieren,
 * 6. Vorrat abziehen (nie unter 0), 7./8. Einträge mit Herkunft erzeugen.
 */
class GenerateShoppingListUseCase {
    operator fun invoke(
        range: DateRange,
        slots: List<MealSlot>,
        recipes: Map<String, Recipe>,
        ingredients: Map<String, Ingredient>,
        pantry: List<PantryItem> = emptyList(),
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

        val remainingPantry = pantry.groupBy { it.ingredientId }
            .mapValues { (_, items) -> items.map { it.quantity }.toMutableList() }
            .toMutableMap()

        val result = mutableListOf<ShoppingNeed>()
        for ((ingredientId, perDim) in groups) {
            val info = ingredients[ingredientId]?.conversion ?: ConversionInfo()
            for ((dim, sources) in perDim) {
                val required = sources.map { it.contributed }.fold(Quantity.zero(dim)) { a, b -> a + b }
                var usedPantry = Quantity.zero(dim)
                val stock = remainingPantry[ingredientId]
                if (stock != null) {
                    val iterator = stock.listIterator()
                    while (iterator.hasNext()) {
                        val p = iterator.next()
                        val pc = UnitConverter.convert(p, dim, info) ?: continue
                        val stillNeeded = required.minusClamped(usedPantry)
                        if (stillNeeded.isZero()) break
                        val take = if (pc.baseAmount <= stillNeeded.baseAmount) pc else stillNeeded
                        usedPantry += take
                        val rest = pc.minusClamped(take)
                        // Restvorrat in ursprünglicher Dimension zurückschreiben
                        val back = UnitConverter.convert(rest, p.dimension, info) ?: Quantity.zero(p.dimension)
                        iterator.set(back)
                    }
                }
                result += ShoppingNeed(ingredientId, required, usedPantry, sources)
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

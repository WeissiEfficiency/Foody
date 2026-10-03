package de.foody.app.data.repo

import androidx.room.withTransaction
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.ShoppingItemEntity
import de.foody.app.data.db.ShoppingItemSourceEntity
import de.foody.app.data.db.ShoppingListEntity
import de.foody.domain.DateRange
import de.foody.domain.DiffType
import de.foody.domain.GenerateShoppingListUseCase
import de.foody.domain.IngredientCatalog
import de.foody.domain.MeasureUnit
import de.foody.domain.Quantity
import de.foody.domain.ShoppingDiff
import de.foody.domain.ShoppingDiffEntry
import de.foody.domain.ShoppingListSnapshot
import de.foody.domain.ShoppingNeed
import javax.inject.Inject
import javax.inject.Singleton

data class NeedPreview(
    val need: ShoppingNeed,
    val ingredientName: String,
    val category: String?,
    /** Rezeptname + Datum je Quelle, für die Herkunftsanzeige. */
    val sourceLabels: List<Pair<String, Quantity>>,
)

@Singleton
class ShoppingRepository @Inject constructor(private val db: FoodyDatabase) {
    private val dao = db.shoppingDao()
    private val generate = GenerateShoppingListUseCase()

    fun observeLists() = dao.observeLists()
    fun observeItems(listId: String) = dao.observeItems(listId)
    suspend fun getSources(itemId: String) = dao.getSources(itemId)

    /** Berechnet den Bedarf für einen Zeitraum, ohne etwas zu speichern. */
    suspend fun preview(range: DateRange, usePantry: Boolean, excluded: Set<String> = emptySet()): List<NeedPreview> {
        val slots = db.mealPlanDao().getRange(range.start, range.endInclusive)
        val recipeDao = db.recipeDao()
        val recipeEntities = slots.map { it.recipeId }.distinct().mapNotNull { recipeDao.get(it) }
        val recipes = recipeEntities.associate { it.id to it.toDomain(recipeDao.getIngredients(it.id)) }
        val ingredientEntities = db.ingredientDao().getAll().associateBy { it.id }
        val ingredients = ingredientEntities.mapValues { it.value.toDomain() }
        val pantry = if (usePantry) db.pantryDao().getAll().map { it.toDomain() } else emptyList()
        // Leitungswasser & Co. nie einkaufen
        val neverBuy = ingredientEntities.values.filter { IngredientCatalog.neverBuy(it.canonicalName) }.map { it.id }
        val needs = generate(range, slots.map { it.toDomain() }, recipes, ingredients, pantry, excludedIngredientIds = excluded + neverBuy)
        val slotById = slots.associateBy { it.id }
        return needs.map { n ->
            val ing = ingredientEntities[n.ingredientId]
            NeedPreview(
                need = n,
                ingredientName = ing?.canonicalName ?: n.ingredientId,
                category = ing?.category,
                sourceLabels = n.sources.map { s ->
                    val name = recipeEntities.firstOrNull { it.id == s.recipeId }?.name.orEmpty()
                    "$name (${slotById[s.mealSlotId]?.date})" to s.contributed
                },
            )
        }
    }

    /** Erzeugt einen Snapshot. Liste + Einträge + Herkunft in einer Transaktion. */
    suspend fun createSnapshot(name: String, range: DateRange, previews: List<NeedPreview>): String {
        val now = System.currentTimeMillis()
        val list = ShoppingListEntity(newId(), name, range.start, range.endInclusive, 1, now, now)
        val (items, sources) = buildItems(list.id, previews.filter { !it.need.toBuy.isZero() })
        dao.insertSnapshot(list, items, sources)
        return list.id
    }

    private suspend fun buildItems(listId: String, previews: List<NeedPreview>, startOrder: Int = 0):
        Pair<List<ShoppingItemEntity>, List<ShoppingItemSourceEntity>> {
        val items = mutableListOf<ShoppingItemEntity>()
        val sources = mutableListOf<ShoppingItemSourceEntity>()
        val recipeDao = db.recipeDao()
        val slotCache = mutableMapOf<String, de.foody.app.data.db.MealSlotEntity?>()
        previews.forEachIndexed { i, p ->
            val item = itemFor(listId, p, startOrder + i)
            items += item
            p.need.sources.forEach { s ->
                val slot = slotCache.getOrPut(s.mealSlotId) { db.mealPlanDao().get(s.mealSlotId) }
                val base = MeasureUnit.baseOf(s.contributed.dimension)
                sources += ShoppingItemSourceEntity(
                    newId(), item.id, s.mealSlotId, s.recipeIngredientId,
                    recipeDao.get(s.recipeId)?.name.orEmpty(),
                    slot?.date ?: java.time.LocalDate.now(),
                    s.contributed.amountIn(base), base,
                )
            }
        }
        return items to sources
    }

    private fun itemFor(listId: String, p: NeedPreview, order: Int): ShoppingItemEntity {
        val base = MeasureUnit.baseOf(p.need.toBuy.dimension)
        return ShoppingItemEntity(
            newId(), listId, p.need.ingredientId, p.ingredientName,
            p.need.toBuy.amountIn(base), base, checked = false, manual = false, category = p.category, sortOrder = order,
        )
    }

    private fun ShoppingItemEntity.key() = "$ingredientId|${unit?.dimension}"
    private fun ShoppingItemEntity.quantity() =
        if (amount != null && unit != null) Quantity.of(amount, unit) else Quantity.zero(de.foody.domain.Dimension.COUNT)

    /** Diff eines bestehenden Snapshots gegen eine Neuberechnung (nur generierte Einträge). */
    suspend fun diff(listId: String, usePantry: Boolean): Pair<List<ShoppingDiffEntry<ShoppingItemEntity>>, List<NeedPreview>> {
        val list = dao.getList(listId) ?: return emptyList<ShoppingDiffEntry<ShoppingItemEntity>>() to emptyList()
        val range = DateRange(list.rangeStart ?: return emptyList<ShoppingDiffEntry<ShoppingItemEntity>>() to emptyList(), list.rangeEnd!!)
        val previews = preview(range, usePantry).filter { !it.need.toBuy.isZero() }
        val old = dao.getItems(listId).filter { !it.manual }
        return ShoppingDiff.diff(old, { it.key() }, { it.quantity() }, previews.map { it.need }) to previews
    }

    /** Wendet einen Diff an. Abgehakte Einträge bleiben erhalten und werden nicht gelöscht. */
    suspend fun applyDiff(listId: String, entries: List<ShoppingDiffEntry<ShoppingItemEntity>>, previews: List<NeedPreview>) =
        db.withTransaction {
            val list = dao.getList(listId) ?: return@withTransaction
            val byKey = previews.associateBy { it.need.key }
            var order = dao.getItems(listId).maxOfOrNull { it.sortOrder + 1 } ?: 0
            for (e in entries) {
                when (e.type) {
                    DiffType.ADDED -> {
                        val (items, sources) = buildItems(listId, listOf(byKey.getValue(e.key)), order++)
                        dao.insertItems(items); dao.insertSources(sources)
                    }
                    DiffType.CHANGED -> {
                        val old = e.old!!
                        val (items, sources) = buildItems(listId, listOf(byKey.getValue(e.key)))
                        val fresh = items.single()
                        dao.upsertItem(old.copy(amount = fresh.amount, unit = fresh.unit, checked = false))
                        dao.deleteSources(old.id)
                        dao.insertSources(sources.map { it.copy(shoppingItemId = old.id) })
                    }
                    DiffType.REMOVED -> if (!e.old!!.checked) dao.deleteItem(e.old!!.id)
                    DiffType.UNCHANGED -> Unit
                }
            }
            dao.upsertList(list.copy(generationVersion = list.generationVersion + 1, updatedAt = System.currentTimeMillis()))
        }

    suspend fun addManual(listId: String, name: String) {
        val order = dao.getItems(listId).maxOfOrNull { it.sortOrder + 1 } ?: 0
        dao.upsertItem(ShoppingItemEntity(newId(), listId, null, name.trim(), null, null, false, true, null, order))
    }

    suspend fun createEmptyList(name: String): String {
        val now = System.currentTimeMillis()
        val l = ShoppingListEntity(newId(), name, null, null, 1, now, now)
        dao.upsertList(l)
        return l.id
    }

    suspend fun setChecked(id: String, checked: Boolean) = dao.setChecked(id, checked)
    suspend fun deleteItem(id: String) = dao.deleteItem(id)
    suspend fun restoreItem(item: ShoppingItemEntity, sources: List<ShoppingItemSourceEntity>) = db.withTransaction {
        dao.upsertItem(item)
        dao.insertSources(sources)
    }
    suspend fun deleteList(id: String) = dao.deleteList(id)

    suspend fun snapshot(listId: String): ShoppingListSnapshot? {
        val list = dao.getList(listId) ?: return null
        return ShoppingListSnapshot(
            list.name,
            dao.getItems(listId).sortedWith(compareBy({ it.category }, { it.sortOrder })).map {
                ShoppingListSnapshot.SnapshotItem(it.name, it.amount?.let { a -> it.unit?.let { u -> Quantity.of(a, u) } }, it.checked, it.category)
            },
        )
    }
}

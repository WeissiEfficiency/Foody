package de.foody.app.data.repo

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientDao
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.db.InstructionStepEntity
import de.foody.app.data.db.MealPlanDao
import de.foody.app.data.db.MealSlotEntity
import de.foody.app.data.db.PantryDao
import de.foody.app.data.db.PantryItemEntity
import de.foody.app.data.db.RecipeDao
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.db.RecipeIngredientEntity
import de.foody.app.data.db.SeedData
import de.foody.domain.Dimension
import de.foody.domain.IngredientCatalog
import de.foody.domain.MeasureUnit
import de.foody.domain.Quantity
import de.foody.domain.RecipeScaler
import de.foody.domain.UnitConverter
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IngredientRepository @Inject constructor(
    private val db: FoodyDatabase,
    private val dao: IngredientDao,
    @ApplicationContext private val context: Context,
) {
    fun observeAll(): Flow<List<IngredientEntity>> = dao.observeAll()

    suspend fun get(id: String) = dao.get(id)

    suspend fun save(e: IngredientEntity) = dao.upsert(e.copy(updatedAt = System.currentTimeMillis(), version = e.version + 1))

    /**
     * Liefert die Zutat zum (über [IngredientCatalog] vereinheitlichten) Namen oder legt sie an.
     * So zeigen „Mehl“ und „Weizenmehl“ aus verschiedenen Importen auf dieselbe Stammzutat.
     */
    suspend fun getOrCreate(name: String): IngredientEntity {
        val canonical = IngredientCatalog.canonicalName(name)
        dao.findByName(canonical)?.let { return it }
        val now = System.currentTimeMillis()
        val e = IngredientEntity(
            newId(), canonical, IngredientCatalog.guessCategory(canonical),
            null, null, null, null, null, null, null, null, null, null, null, now, now,
        )
        dao.upsert(e)
        return e
    }

    /** false, wenn die Zutat noch in Rezepten verwendet wird. */
    suspend fun delete(id: String): Boolean {
        if (dao.usageCount(id) > 0) return false
        dao.delete(id)
        return true
    }

    /**
     * Führt [fromId] in [intoId] zusammen: Rezeptzeilen, Vorrat und Einkaufseinträge zeigen danach auf [intoId].
     * Fehlende Nährwerte/Umrechnungsdaten des Ziels werden aus der Quelle übernommen.
     */
    suspend fun merge(fromId: String, intoId: String) = db.withTransaction {
        if (fromId == intoId) return@withTransaction
        val from = dao.get(fromId) ?: return@withTransaction
        val into = dao.get(intoId) ?: return@withTransaction
        mergeInto(from, into)
    }

    /**
     * Vereinheitlicht alle Zutatennamen nach [IngredientCatalog] (z. B. „Mehl“ → „Weizenmehl“) und führt
     * dabei entstehende Dubletten zusammen. Liefert die Zahl der geänderten Zutaten.
     */
    suspend fun harmonizeNames(): Int = db.withTransaction {
        var changed = 0
        for (e in dao.getAll()) {
            val current = dao.get(e.id) ?: continue // evtl. schon zusammengeführt
            val target = IngredientCatalog.canonicalName(current.canonicalName)
            val existing = dao.findByName(target)
            when {
                existing != null && existing.id != current.id -> mergeInto(current, existing)
                target != current.canonicalName ->
                    dao.upsert(current.copy(canonicalName = target, category = current.category ?: IngredientCatalog.guessCategory(target)))
                current.category == null && IngredientCatalog.guessCategory(target) != null ->
                    dao.upsert(current.copy(category = IngredientCatalog.guessCategory(target)))
                else -> continue
            }
            changed++
        }
        changed
    }

    private suspend fun mergeInto(from: IngredientEntity, into: IngredientEntity) {
        dao.repointRecipeLines(from.id, into.id)
        dao.repointPantry(from.id, into.id)
        dao.repointShoppingItems(from.id, into.id)
        dao.delete(from.id)
        dao.upsert(into.fillFrom(from).copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Legt fehlende Startzutaten an und ergänzt Nährwerte bei gleichnamigen Zutaten ohne Daten
     * (z. B. „Wasser“, das ein Import schon angelegt hat). Läuft je [SeedData.VERSION] einmal.
     */
    suspend fun seedIfNeeded() {
        val prefs = context.getSharedPreferences("foody", Context.MODE_PRIVATE)
        val done = prefs.getInt("seedVersion", if (prefs.getBoolean("seeded", false)) 1 else 0)
        if (done >= SeedData.VERSION) return
        db.withTransaction {
            for (seed in SeedData.ingredients(System.currentTimeMillis())) {
                val existing = dao.findByName(seed.canonicalName)
                if (existing == null) dao.insertAll(listOf(seed)) else dao.upsert(existing.fillFrom(seed))
            }
        }
        prefs.edit().putInt("seedVersion", SeedData.VERSION).apply()
    }
}

/** Übernimmt Nährwerte und Umrechnungsdaten aus [other], wo dieser Eintrag keine hat. */
private fun IngredientEntity.fillFrom(other: IngredientEntity): IngredientEntity {
    val withNutrients = if (nutrientBasis == null && other.nutrientBasis != null) {
        copy(
            nutrientBasis = other.nutrientBasis, energyKj = other.energyKj, protein = other.protein, carbs = other.carbs,
            fat = other.fat, fiber = other.fiber, sugar = other.sugar, salt = other.salt, nutrientSource = other.nutrientSource,
        )
    } else {
        this
    }
    return withNutrients.copy(
        category = category ?: other.category,
        densityGPerMl = densityGPerMl ?: other.densityGPerMl,
        pieceWeightG = pieceWeightG ?: other.pieceWeightG,
    )
}

data class RecipeDraft(
    val id: String?,
    val name: String,
    val defaultServings: Int,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val imageUri: String?,
    val notes: String?,
    val tags: String,
    val ingredients: List<Line>,
    val steps: List<String>,
) {
    data class Line(
        val ingredientId: String,
        val amount: java.math.BigDecimal,
        val unit: MeasureUnit,
        val note: String?,
        val optional: Boolean,
    )
}

@Singleton
class RecipeRepository @Inject constructor(private val dao: RecipeDao) {
    fun observe(query: String, archived: Boolean) = dao.observe(query, archived)
    fun observeActive() = dao.observeActive()
    fun observeRecipe(id: String) = dao.observe(id)
    fun observeIngredients(id: String) = dao.observeIngredients(id)
    fun observeSteps(id: String) = dao.observeSteps(id)
    suspend fun get(id: String) = dao.get(id)
    suspend fun getIngredients(id: String) = dao.getIngredients(id)
    suspend fun getSteps(id: String) = dao.getSteps(id)

    suspend fun save(d: RecipeDraft): String {
        val now = System.currentTimeMillis()
        val existing = d.id?.let { dao.get(it) }
        val id = existing?.id ?: newId()
        val entity = RecipeEntity(
            id = id,
            name = d.name.trim(),
            defaultServings = d.defaultServings,
            prepMinutes = d.prepMinutes,
            cookMinutes = d.cookMinutes,
            imageUri = d.imageUri,
            notes = d.notes?.takeIf { it.isNotBlank() },
            tags = d.tags.trim(),
            archivedAt = existing?.archivedAt,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            version = (existing?.version ?: 0) + 1,
        )
        val lines = d.ingredients.mapIndexed { i, l ->
            RecipeIngredientEntity(newId(), id, l.ingredientId, l.amount, l.unit, i, l.note, l.optional)
        }
        val steps = d.steps.filter { it.isNotBlank() }.mapIndexed { i, s -> InstructionStepEntity(newId(), id, i, s.trim()) }
        dao.save(entity, lines, steps)
        return id
    }

    suspend fun duplicate(id: String, copySuffix: String): String? {
        val r = dao.get(id) ?: return null
        val lines = dao.getIngredients(id)
        val steps = dao.getSteps(id)
        return save(
            RecipeDraft(
                null, "${r.name} $copySuffix", r.defaultServings, r.prepMinutes, r.cookMinutes, r.imageUri, r.notes, r.tags,
                lines.map { RecipeDraft.Line(it.ingredientId, it.amount, it.unit, it.preparationNote, it.optional) },
                steps.map { it.text },
            ),
        )
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        val now = System.currentTimeMillis()
        dao.setArchived(id, if (archived) now else null, now)
    }

    suspend fun delete(id: String) = dao.delete(id)
}

@Singleton
class PlanRepository @Inject constructor(
    private val db: FoodyDatabase,
    private val dao: MealPlanDao,
    private val recipeDao: RecipeDao,
    private val pantryDao: PantryDao,
    private val ingredientDao: IngredientDao,
) {
    fun observeRange(start: LocalDate, end: LocalDate) = dao.observeRange(start, end)

    suspend fun add(date: LocalDate, slotType: String, recipeId: String, servings: Int) {
        val now = System.currentTimeMillis()
        dao.upsert(MealSlotEntity(newId(), date, slotType, recipeId, servings, null, now, now))
    }

    suspend fun update(slot: MealSlotEntity) = dao.upsert(slot.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(id: String) = dao.delete(id)

    /**
     * „Gekocht“ bestätigen: bucht den Verbrauch vom Vorrat ab (nie unter 0).
     * Planung allein verändert den Vorrat nicht.
     */
    suspend fun markCooked(slotId: String) = db.withTransaction {
        val slot = dao.get(slotId) ?: return@withTransaction
        if (slot.cookedAt != null) return@withTransaction
        val recipe = recipeDao.get(slot.recipeId) ?: return@withTransaction
        val pantry = pantryDao.getAll().toMutableList()
        for (line in recipeDao.getIngredients(recipe.id)) {
            if (line.optional) continue
            val info = ingredientDao.get(line.ingredientId)?.toDomain()?.conversion ?: continue
            var need = Quantity.of(RecipeScaler.scale(line.amount, recipe.defaultServings, slot.servings), line.unit)
            for ((idx, p) in pantry.withIndex()) {
                if (p.ingredientId != line.ingredientId || need.isZero()) continue
                val stock = Quantity.of(p.amount, p.unit)
                val needInStockDim = UnitConverter.convert(need, stock.dimension, info) ?: continue
                val take = if (stock.baseAmount <= needInStockDim.baseAmount) stock else needInStockDim
                val rest = stock.minusClamped(take)
                val updated = p.copy(amount = rest.amountIn(p.unit), updatedAt = System.currentTimeMillis())
                pantry[idx] = updated
                pantryDao.upsert(updated)
                val takenInNeedDim = UnitConverter.convert(take, need.dimension, info) ?: take
                need = need.minusClamped(takenInNeedDim)
            }
        }
        dao.upsert(slot.copy(cookedAt = System.currentTimeMillis()))
    }
}

@Singleton
class PantryRepository @Inject constructor(private val dao: PantryDao) {
    fun observeAll() = dao.observeAll()

    suspend fun save(id: String?, ingredientId: String, amount: java.math.BigDecimal, unit: MeasureUnit, bestBefore: LocalDate?) =
        dao.upsert(PantryItemEntity(id ?: newId(), ingredientId, amount, unit, bestBefore, System.currentTimeMillis()))

    suspend fun delete(id: String) = dao.delete(id)
}

internal fun Dimension.baseUnit() = MeasureUnit.baseOf(this)

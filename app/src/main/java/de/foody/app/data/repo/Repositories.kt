package de.foody.app.data.repo

import de.foody.domain.PantryCoverage
import kotlinx.coroutines.flow.map
import android.content.Context
import androidx.core.content.edit
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
import de.foody.domain.Gang
import de.foody.domain.Mahlzeit
import de.foody.domain.alsText
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
    @param:ApplicationContext private val context: Context,
) {
    fun observeAll(): Flow<List<IngredientEntity>> = dao.observeAll()

    suspend fun get(id: String) = dao.get(id)

    suspend fun zutatMitStrichcode(code: String) = dao.findByBarcode(code)

    suspend fun findByName(name: String) = dao.findByName(name)

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
            id = newId(), canonicalName = canonical, category = IngredientCatalog.guessCategory(canonical),
            createdAt = now, updatedAt = now,
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

    private suspend fun mergeInto(from: IngredientEntity, into: IngredientEntity) =
        mergeIngredient(dao, from, into, System.currentTimeMillis())

    /**
     * Legt fehlende Startzutaten an und ergänzt Nährwerte bei gleichnamigen Zutaten ohne Daten
     * (z. B. „Wasser“, das ein Import schon angelegt hat). Läuft je [SeedData.VERSION] einmal.
     */
    suspend fun seedIfNeeded() {
        val prefs = context.getSharedPreferences("foody", Context.MODE_PRIVATE)
        val done = prefs.getInt("seedVersion", if (prefs.getBoolean("seeded", false)) 1 else 0)
        if (done >= SeedData.VERSION) return
        // Neue Schreibweisen im Katalog (z. B. „Zitronenabrieb“ → „Zitronenschale“) zuerst übernehmen,
        // damit die Startdaten die bereits importierten Zutaten unter ihrem Namen finden
        if (done > 0) harmonizeNames()
        db.withTransaction {
            for (seed in SeedData.ingredients(System.currentTimeMillis())) {
                val existing = dao.findByName(seed.canonicalName)
                when {
                    existing == null -> dao.insertAll(listOf(seed))
                    // Unveränderter Starteintrag (gleiche ID, Quelle unverändert): Korrekturen übernehmen
                    existing.id == seed.id && existing.nutrientSource in setOf(SeedData.SOURCE, seed.nutrientSource) ->
                        dao.upsert(seed.copy(createdAt = existing.createdAt, version = existing.version))
                    else -> dao.upsert(existing.fillFrom(seed))
                }
            }
        }
        prefs.edit { putInt("seedVersion", SeedData.VERSION) }
    }
}

data class RecipeDraft(
    val id: String?,
    val name: String,
    val defaultServings: Int,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val imageUri: String? = null,
    val notes: String? = null,
    val tags: String = "",
    val ingredients: List<Line>,
    val steps: List<String> = emptyList(),
    /** Quell-URL bei Importen; null übernimmt beim Bearbeiten die gespeicherte. */
    val sourceUrl: String? = null,
    /**
     * Foto beim Öffnen des Editors. Nur wenn [trackOriginalImage] gesetzt ist und [imageUri] davon abweicht, schreibt
     * Speichern das Foto; sonst bleibt das gespeicherte (ein Sync kann es zwischenzeitlich geändert haben).
     */
    val originalImageUri: String? = null,
    val trackOriginalImage: Boolean = false,
    /** Festgelegte Einordnung; null = vermuten. Wird nur bei [einordnungUebernehmen] geschrieben. */
    val mahlzeiten: Set<Mahlzeit>? = null,
    val gaenge: Set<Gang>? = null,
    /** Nur der Editor setzt das; Import und andere Aufrufer lassen die gespeicherte Einordnung unberührt. */
    val einordnungUebernehmen: Boolean = false,
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
    /** Suche in Name, Tags und Zutaten; % und _ werden wörtlich genommen. */
    fun observe(query: String, archived: Boolean) = dao.observe(escapeLike(query), archived)
    fun observeActive() = dao.observeActive()
    fun observeAll() = dao.observeAll()
    fun observeRecipe(id: String) = dao.observe(id)
    fun observeAllLines() = dao.observeAllIngredients()
    fun observeRequired() = dao.observeRequired().map { rows ->
        rows.map { PantryCoverage.Requirement(it.recipeId, it.ingredientId, it.ingredientName) }
    }
    fun observeIngredients(id: String) = dao.observeIngredients(id)
    fun observeSteps(id: String) = dao.observeSteps(id)
    suspend fun get(id: String) = dao.get(id)
    suspend fun getIngredients(id: String) = dao.getIngredients(id)
    suspend fun getSteps(id: String) = dao.getSteps(id)

    suspend fun save(d: RecipeDraft): String {
        val now = System.currentTimeMillis()
        return dao.saveBuilt(d.id) { existing ->
            val id = existing?.id ?: newId()
            val imageUri = if (d.trackOriginalImage && d.imageUri == d.originalImageUri) existing?.imageUri else d.imageUri
            val entity = RecipeEntity(
                id = id,
                name = d.name.trim(),
                defaultServings = d.defaultServings,
                prepMinutes = d.prepMinutes,
                cookMinutes = d.cookMinutes,
                imageUri = imageUri,
                notes = d.notes?.takeIf { it.isNotBlank() },
                tags = d.tags.trim(),
                archivedAt = existing?.archivedAt,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                version = (existing?.version ?: 0) + 1,
                // Favorit und Quelle gehören nicht zum Editor-Entwurf und bleiben beim Bearbeiten erhalten
                favorite = existing?.favorite ?: false,
                sourceUrl = d.sourceUrl ?: existing?.sourceUrl,
                rating = existing?.rating,
                mahlzeiten = if (d.einordnungUebernehmen) alsText(d.mahlzeiten, existing?.mahlzeiten, Mahlzeit.entries) else existing?.mahlzeiten,
                gaenge = if (d.einordnungUebernehmen) alsText(d.gaenge, existing?.gaenge, Gang.entries) else existing?.gaenge,
            )
            val lines = d.ingredients.mapIndexed { i, l ->
                RecipeIngredientEntity(newId(), id, l.ingredientId, l.amount, l.unit, i, l.note, l.optional)
            }
            val steps = d.steps.filter { it.isNotBlank() }.mapIndexed { i, s -> InstructionStepEntity(newId(), id, i, s.trim()) }
            Triple(entity, lines, steps)
        }
    }

    /** Gespeichertes Rezept als Entwurf (gleiche ID); null, wenn es nicht existiert. */
    suspend fun draftOf(id: String): RecipeDraft? {
        val r = dao.get(id) ?: return null
        return RecipeDraft(
            r.id, r.name, r.defaultServings, r.prepMinutes, r.cookMinutes, r.imageUri, r.notes, r.tags,
            dao.getIngredients(id).map { RecipeDraft.Line(it.ingredientId, it.amount, it.unit, it.preparationNote, it.optional) },
            dao.getSteps(id).map { it.text },
            r.sourceUrl,
            mahlzeiten = Mahlzeit.mengeAus(r.mahlzeiten),
            gaenge = Gang.mengeAus(r.gaenge),
        )
    }

    /** Kopie ohne Quell-URL (sonst gälte sie als Dublette des Imports) und ohne Favoriten-Markierung. */
    suspend fun duplicate(id: String, copySuffix: String): String? {
        val d = draftOf(id) ?: return null
        return save(d.copy(id = null, name = "${d.name} $copySuffix", sourceUrl = null, einordnungUebernehmen = true))
    }

    suspend fun setImage(id: String, uri: String?) = dao.setImage(id, uri, System.currentTimeMillis())

    suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite, System.currentTimeMillis())
    suspend fun setRating(id: String, rating: Int?) = dao.setRating(id, rating?.coerceIn(1, 5), System.currentTimeMillis())

    suspend fun findBySourceUrl(url: String) = dao.findBySourceUrl(url)

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
    fun observeCookedCounts() = dao.observeCookedCounts().map { rows -> rows.associate { it.recipeId to it.count } }

    /** Spätestes Plandatum je Rezept im Zeitraum – für Vorschläge ohne Wiederholung. */
    suspend fun lastPlannedByRecipe(start: LocalDate, end: LocalDate): Map<String, LocalDate> =
        dao.getRange(start, end).groupBy { it.recipeId }.mapValues { (_, slots) -> slots.maxOf { it.date } }

    suspend fun add(date: LocalDate, slotType: String, recipeId: String, servings: Int) {
        val now = System.currentTimeMillis()
        dao.upsert(
            MealSlotEntity(id = newId(), date = date, slotType = slotType, recipeId = recipeId, servings = servings, createdAt = now, updatedAt = now),
        )
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
        val lines = recipeDao.getIngredients(recipe.id)
        // Eine Abfrage für alle Zutaten statt einer je Rezeptzeile
        val infoById = ingredientDao.getByIds(lines.map { it.ingredientId }.distinct()).associate { it.id to it.toDomain().conversion }
        for (line in lines) {
            if (line.optional) continue
            val info = infoById[line.ingredientId] ?: continue
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

/** Maskiert LIKE-Platzhalter, damit „50%“ nicht als Muster gilt (passend zu ESCAPE '!' in der Abfrage). */
internal fun escapeLike(q: String): String = q.replace("!", "!!").replace("%", "!%").replace("_", "!_")

package de.foody.app.sync

import androidx.room.withTransaction
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.db.MealSlotEntity
import de.foody.app.data.db.PantryItemEntity
import de.foody.app.data.db.ShoppingListEntity
import de.foody.app.data.db.SyncOutboxEntity
import de.foody.app.data.db.SyncProblemEntity
import de.foody.app.data.db.SyncRecordRevEntity
import de.foody.app.data.db.SyncStateEntity
import de.foody.app.data.repo.fillFrom
import de.foody.app.data.repo.mergeIngredient
import de.foody.sync.protocol.IngredientPayload
import de.foody.sync.protocol.MealSlotPayload
import de.foody.sync.protocol.PantryItemPayload
import de.foody.sync.protocol.PayloadValidator
import de.foody.sync.protocol.RecipePayload
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.ShoppingItemPayload
import de.foody.sync.protocol.ShoppingListPayload
import de.foody.sync.protocol.SyncRecord
import de.foody.sync.protocol.decode
import kotlinx.serialization.SerializationException
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ergebnis von [SyncApplier.apply]: [applied] = geschriebene lebende Datensätze plus ausgeführte Löschungen,
 * [skippedPending] = wegen offener lokaler Änderung übersprungen, [revived] = Löschung durch Wiederbeleben einer
 * noch verwendeten Zutat abgewehrt, [merged] = gleichnamige lokale Zutat in die Server-Zutat zusammengeführt,
 * [problems] = als `sync_problem` vermerkte Datensätze.
 */
data class ApplyResult(val applied: Int, val skippedPending: Int, val revived: Int, val merged: Int, val problems: Int)

/**
 * Wendet vom Server geholte Datensätze auf die lokale Datenbank an – komplett in einer Transaktion und mit
 * `applyingRemote = 1`, damit die Sync-Trigger nichts in die Outbox stellen. Lokale Änderungen mit offenem
 * Outbox-Eintrag gewinnen (der Datensatz wird übersprungen); sie gehen mit dem nächsten Push hinaus.
 */
@Singleton
class SyncApplier @Inject constructor(private val db: FoodyDatabase) {
    private val dao get() = db.syncDao()

    private class Counters {
        var applied = 0
        var skippedPending = 0
        var revived = 0
        var merged = 0
        var problems = 0
    }

    /** Alles, was ein lebender Datensatz zum Schreiben braucht; entsteht vor dem ersten Schreibzugriff. */
    private sealed interface Mapped {
        data class Ingredient(val entity: IngredientEntity) : Mapped
        data class Recipe(val parts: RecipeParts) : Mapped
        data class MealSlot(val entity: MealSlotEntity) : Mapped
        data class Pantry(val entity: PantryItemEntity) : Mapped
        data class ShoppingList(val entity: ShoppingListEntity) : Mapped
        data class ShoppingItem(val parts: ShoppingItemParts) : Mapped
    }

    suspend fun apply(records: List<SyncRecord>, nextCursor: Long): ApplyResult {
        val c = Counters()
        db.withTransaction {
            val now = System.currentTimeMillis()
            dao.upsertState((dao.getState() ?: SyncStateEntity()).copy(applyingRemote = true))
            try {
                val open = records.filter { r ->
                    val queued = dao.isQueued(r.type.wire, r.id)
                    if (queued) c.skippedPending++
                    !queued
                }
                val live = open.filter { !it.deleted }.sortedWith(compareBy({ it.type.ordinal }, { it.rev ?: 0L }))
                val deletions = open.filter { it.deleted }.sortedWith(compareBy({ -it.type.ordinal }, { it.rev ?: 0L }))
                for (r in live) applyLive(r, now, c)
                for (r in deletions) applyDeletion(r, c)
                dao.upsertState(
                    (dao.getState() ?: SyncStateEntity()).copy(applyingRemote = false, cursor = nextCursor, lastSyncAt = now),
                )
            } finally {
                dao.setApplyingRemote(false)
            }
        }
        return ApplyResult(c.applied, c.skippedPending, c.revived, c.merged, c.problems)
    }

    private suspend fun problem(r: SyncRecord, code: String, now: Long, c: Counters) {
        dao.addProblem(SyncProblemEntity(r.type.wire, r.id, code, now))
        c.problems++
    }

    /** Dekodiert und bildet auf Entities ab; `null` bei unbrauchbarem Payload (nichts wurde geschrieben). */
    private suspend fun map(r: SyncRecord): Pair<Mapped, List<Pair<RecordType, String>>>? = try {
        val payload = requireNotNull(r.payload) { "payload fehlt" }
        val updatedAt = r.updatedAt
        val mapped: Mapped = when (val decoded = r.type.decode(payload)) {
            is IngredientPayload ->
                Mapped.Ingredient(SyncMapper.ingredient(r.id, decoded, updatedAt, db.ingredientDao().get(r.id)))
            is RecipePayload -> Mapped.Recipe(SyncMapper.recipe(r.id, decoded, updatedAt, db.recipeDao().get(r.id)))
            is MealSlotPayload ->
                Mapped.MealSlot(SyncMapper.mealSlot(r.id, decoded, updatedAt, db.mealPlanDao().get(r.id)))
            is PantryItemPayload ->
                Mapped.Pantry(SyncMapper.pantryItem(r.id, decoded, updatedAt, db.pantryDao().get(r.id)))
            is ShoppingListPayload ->
                Mapped.ShoppingList(SyncMapper.shoppingList(r.id, decoded, updatedAt, db.shoppingDao().getList(r.id)))
            is ShoppingItemPayload ->
                Mapped.ShoppingItem(SyncMapper.shoppingItem(r.id, decoded, updatedAt, db.shoppingDao().getItem(r.id)))
            else -> throw IllegalArgumentException("Unbekannter Payload-Typ")
        }
        mapped to PayloadValidator.references(r)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null // u. a. unbekannte Enum-Namen, NumberFormatException
    } catch (_: DateTimeParseException) {
        null
    }

    private suspend fun exists(type: RecordType, id: String): Boolean = when (type) {
        RecordType.INGREDIENT -> db.ingredientDao().get(id) != null
        RecordType.RECIPE -> db.recipeDao().get(id) != null
        RecordType.MEAL_SLOT -> db.mealPlanDao().get(id) != null
        RecordType.PANTRY_ITEM -> db.pantryDao().get(id) != null
        RecordType.SHOPPING_LIST -> db.shoppingDao().getList(id) != null
        RecordType.SHOPPING_ITEM -> db.shoppingDao().getItem(id) != null
    }

    private suspend fun applyLive(r: SyncRecord, now: Long, c: Counters) {
        val (mapped, refs) = map(r) ?: return problem(r, "invalid_payload", now, c)
        if (refs.any { (type, id) -> !exists(type, id) }) return problem(r, "missing_reference", now, c)
        when (mapped) {
            is Mapped.Ingredient -> writeIngredient(mapped.entity, c)
            is Mapped.Recipe -> {
                val rd = db.recipeDao()
                rd.upsert(mapped.parts.recipe)
                rd.deleteIngredients(r.id)
                rd.deleteSteps(r.id)
                rd.insertIngredients(mapped.parts.lines)
                rd.insertSteps(mapped.parts.steps)
            }
            is Mapped.MealSlot -> db.mealPlanDao().upsert(mapped.entity)
            is Mapped.Pantry -> db.pantryDao().upsert(mapped.entity)
            is Mapped.ShoppingList -> db.shoppingDao().upsertList(mapped.entity)
            is Mapped.ShoppingItem -> {
                val sd = db.shoppingDao()
                sd.upsertItem(mapped.parts.item)
                sd.deleteSources(r.id)
                sd.insertSources(mapped.parts.sources)
            }
        }
        dao.setRev(SyncRecordRevEntity(r.type.wire, r.id, r.rev ?: 0L))
        c.applied++
    }

    /**
     * Schreibt eine Server-Zutat. Gibt es lokal eine andere Zutat mit gleichem Namen (ohne Groß-/Kleinschreibung),
     * wird sie in die Server-Zutat zusammengeführt (Server-ID gewinnt).
     */
    private suspend fun writeIngredient(remote: IngredientEntity, c: Counters) {
        val ing = db.ingredientDao()
        // Erst exakt (passt zum eindeutigen Index), sonst ohne Beachtung der Groß-/Kleinschreibung
        val local = ing.findByNameExact(remote.canonicalName)?.takeIf { it.id != remote.id }
            ?: ing.findByName(remote.canonicalName)
        if (local == null || local.id == remote.id) {
            ing.upsert(remote)
            return
        }
        // Der Namensindex ist eindeutig: lokale Zutat vorübergehend umbenennen, dann Server-Zutat schreiben
        val renamed = local.copy(canonicalName = local.canonicalName + "#" + local.id)
        ing.upsert(renamed)
        ing.upsert(remote)
        // Mit aktiven Triggern zusammenführen, damit umgehängte Rezepte/Vorräte/Einträge in die Outbox kommen
        dao.setApplyingRemote(false)
        mergeIngredient(ing, renamed, remote, System.currentTimeMillis())
        dao.setApplyingRemote(true)
        dao.dequeue(RecordType.INGREDIENT.wire, local.id)
        // Hat die Zusammenführung die Server-Zutat nicht ergänzt, muss sie nicht zurückgesendet werden
        if (remote.fillFrom(renamed) == remote) dao.dequeue(RecordType.INGREDIENT.wire, remote.id)
        c.merged++
    }

    /** Würde die Löschung lokal noch gebrauchte Daten reißen (FK RESTRICT oder Kaskade über offene Änderungen)? */
    private suspend fun isStillNeeded(r: SyncRecord): Boolean = when (r.type) {
        RecordType.INGREDIENT -> db.ingredientDao().usageCount(r.id) > 0 || dao.hasQueuedPantryFor(r.id)
        RecordType.RECIPE -> dao.hasQueuedSlotsFor(r.id)
        RecordType.SHOPPING_LIST -> dao.hasQueuedItemsFor(r.id)
        else -> false
    }

    private suspend fun applyDeletion(r: SyncRecord, c: Counters) {
        val rev = SyncRecordRevEntity(r.type.wire, r.id, r.rev ?: 0L)
        if (!exists(r.type, r.id)) {
            dao.setRev(rev)
            return
        }
        if (isStillNeeded(r)) {
            // Noch von Rezeptzeilen verwendet oder von Kaskade mit offener lokaler Änderung betroffen (Vorrat,
            // Planposition, Einkaufseintrag): nicht löschen, beim nächsten Push wiederbeleben (baseRev = rev)
            dao.setRev(rev)
            dao.enqueue(SyncOutboxEntity(r.type.wire, r.id, deleted = false, queuedAt = System.currentTimeMillis()))
            c.revived++
            return
        }
        when (r.type) {
            RecordType.INGREDIENT -> db.ingredientDao().delete(r.id)
            RecordType.RECIPE -> db.recipeDao().delete(r.id)
            RecordType.MEAL_SLOT -> db.mealPlanDao().delete(r.id)
            RecordType.PANTRY_ITEM -> db.pantryDao().delete(r.id)
            RecordType.SHOPPING_LIST -> db.shoppingDao().deleteList(r.id)
            RecordType.SHOPPING_ITEM -> db.shoppingDao().deleteItem(r.id)
        }
        dao.setRev(rev)
        c.applied++
    }
}

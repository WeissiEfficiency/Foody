package de.foody.app.sync

import de.foody.app.data.db.TagebuchEintragEntity
import de.foody.sync.protocol.TagebuchPayload
import android.database.SQLException
import androidx.room.withTransaction
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.db.MealSlotEntity
import de.foody.app.data.db.PantryItemEntity
import de.foody.app.data.db.ShoppingListEntity
import de.foody.app.data.db.SyncOutboxEntity
import de.foody.app.data.db.SyncProblemEntity
import de.foody.app.data.db.SyncRecordRevEntity
import de.foody.app.data.db.SyncPhotoWantedEntity
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
class SyncApplier @Inject constructor(private val db: FoodyDatabase, private val photoIndex: PhotoIndex) {
    private val dao get() = db.syncDao()

    private class Counters {
        var applied = 0
        var skippedPending = 0
        var revived = 0
        var merged = 0
        var problems = 0

        fun copy() = Counters().also { it.restore(this) }

        fun restore(o: Counters) {
            applied = o.applied
            skippedPending = o.skippedPending
            revived = o.revived
            merged = o.merged
            problems = o.problems
        }
    }

    /** Alles, was ein lebender Datensatz zum Schreiben braucht; entsteht vor dem ersten Schreibzugriff. */
    private sealed interface Mapped {
        data class Ingredient(val entity: IngredientEntity) : Mapped
        data class Recipe(val parts: RecipeParts, val remotePhoto: String?) : Mapped
        data class MealSlot(val entity: MealSlotEntity) : Mapped
        data class Pantry(val entity: PantryItemEntity) : Mapped
        data class ShoppingList(val entity: ShoppingListEntity) : Mapped
        data class ShoppingItem(val parts: ShoppingItemParts) : Mapped
        data class Tagebuch(val entity: TagebuchEintragEntity) : Mapped
    }

    /**
     * Jeder Datensatz läuft in einer eigenen Transaktion: Schlägt ein Schreibzugriff fehl (z. B. Constraint-Verletzung),
     * wird nur dieser Datensatz zurückgerollt und als `apply_failed` vermerkt, die übrigen werden trotzdem übernommen.
     * (Ein SAVEPOINT innerhalb einer gemeinsamen Transaktion hilft auf Android nicht: Schon die Room-eigene,
     * verschachtelte DAO-Transaktion des fehlgeschlagenen Zugriffs markiert die äußere als gescheitert.)
     * Der Cursor rückt (nur mit [updateCursor]) erst in einer letzten Transaktion vor; bricht der Lauf vorher ab, wird dieselbe Seite erneut
     * geholt, und das Anwenden ist idempotent.
     *
     * Mit [skipKnown] werden lebende Datensätze übersprungen (gezählt wie `skippedPending`), deren Revision lokal schon
     * bekannt ist und die lokal existieren – etwa das Echo des eigenen Pushs im Pull. Sonst würde das Echo lokale
     * Besonderheiten überschreiben (z. B. ein nicht übertragbares eigenes Foto, das ohne Foto gesendet wurde).
     * Ein `current` aus einem Push-Ergebnis (Merge) wird dagegen immer angewendet (`skipKnown = false`).
     */
    suspend fun apply(
        records: List<SyncRecord>,
        nextCursor: Long,
        updateCursor: Boolean = true,
        skipKnown: Boolean = true,
    ): ApplyResult {
        val c = Counters()
        val now = System.currentTimeMillis()
        val live = records.filter { !it.deleted }.sortedWith(compareBy({ it.type.ordinal }, { it.rev ?: 0L }))
        val deletions = records.filter { it.deleted }.sortedWith(compareBy({ -it.type.ordinal }, { it.rev ?: 0L }))
        // Durch eine Zutaten-Zusammenführung selbst vorgemerkte Datensätze sind keine fremden lokalen Änderungen
        val selfQueued = mutableMapOf<Pair<String, String>, Long>()
        for (r in live) applyOne(r, now, c, selfQueued, skipKnown) { applyLive(r, now, c, selfQueued) }
        for (r in deletions) applyOne(r, now, c, selfQueued, false) { applyDeletion(r, c) }
        db.withTransaction {
            val state = (dao.getState() ?: SyncStateEntity()).copy(applyingRemote = false)
            // Einzelne `current`-Datensätze aus einem Push-Ergebnis dürfen den Pull-Cursor nicht verschieben.
            dao.upsertState(if (updateCursor) state.copy(cursor = nextCursor, lastSyncAt = now) else state)
        }
        return ApplyResult(c.applied, c.skippedPending, c.revived, c.merged, c.problems)
    }

    /**
     * Offene lokale Änderung? Ausgenommen ist nur ein Eintrag, den die Zutaten-Zusammenführung selbst vorgemerkt hat
     * und dessen `queuedAt` unverändert ist; eine spätere Nutzeränderung hebt die Ausnahme auf.
     */
    internal suspend fun shouldSkipPending(type: String, id: String, selfQueued: Map<Pair<String, String>, Long>): Boolean {
        val queuedAt = dao.queuedAtOf(type, id) ?: return false
        return selfQueued[type to id] != queuedAt
    }

    /** Kennen wir diese Revision (oder eine neuere) schon, und gibt es den Datensatz lokal? */
    private suspend fun isKnownRev(r: SyncRecord): Boolean {
        val rev = r.rev ?: return false
        val known = dao.revOf(r.type.wire, r.id) ?: return false
        return rev <= known && exists(r.type, r.id)
    }

    private suspend fun applyOne(
        r: SyncRecord,
        now: Long,
        c: Counters,
        selfQueued: MutableMap<Pair<String, String>, Long>,
        skipKnown: Boolean,
        block: suspend () -> Unit,
    ) {
        val before = c.copy()
        val queuedBefore = selfQueued.toMap()
        try {
            db.withTransaction {
                if (shouldSkipPending(r.type.wire, r.id, selfQueued)) {
                    c.skippedPending++
                    return@withTransaction
                }
                if (skipKnown && !r.deleted && isKnownRev(r)) {
                    c.skippedPending++
                    return@withTransaction
                }
                dao.upsertState((dao.getState() ?: SyncStateEntity()).copy(applyingRemote = true))
                try {
                    block()
                } finally {
                    dao.setApplyingRemote(false)
                }
            }
        } catch (_: SQLException) {
            c.restore(before)
            selfQueued.clear()
            selfQueued.putAll(queuedBefore)
            db.withTransaction { problem(r, "apply_failed", now, c) }
        }
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
            is RecipePayload -> {
                val existing = db.recipeDao().get(r.id)
                val known = decoded.photo?.let { photoIndex.uriFor(it) }
                // Von anderen Geräten ohne Foto bearbeitet, hier aber ein nicht übertragbares eigenes Foto: behalten
                val keepUnsyncable = decoded.photo == null && hasUnsyncableProblem(r.id)
                val own = !keepUnsyncable && existing?.imageUri?.let { photoIndex.isOwnPhoto(it) } == true
                Mapped.Recipe(SyncMapper.recipe(r.id, decoded, updatedAt, existing, known, own), decoded.photo)
            }
            is MealSlotPayload ->
                Mapped.MealSlot(SyncMapper.mealSlot(r.id, decoded, updatedAt, db.mealPlanDao().get(r.id)))
            is PantryItemPayload ->
                Mapped.Pantry(SyncMapper.pantryItem(r.id, decoded, updatedAt, db.pantryDao().get(r.id)))
            is ShoppingListPayload ->
                Mapped.ShoppingList(SyncMapper.shoppingList(r.id, decoded, updatedAt, db.shoppingDao().getList(r.id)))
            is ShoppingItemPayload ->
                Mapped.ShoppingItem(SyncMapper.shoppingItem(r.id, decoded, updatedAt, db.shoppingDao().getItem(r.id)))
            is TagebuchPayload ->
                Mapped.Tagebuch(SyncMapper.tagebuch(r.id, decoded, updatedAt, db.tagebuchDao().get(r.id)))
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

    /** Gibt es den Datensatz lokal? */
    internal suspend fun exists(type: RecordType, id: String): Boolean = when (type) {
        RecordType.INGREDIENT -> db.ingredientDao().get(id) != null
        RecordType.RECIPE -> db.recipeDao().get(id) != null
        RecordType.MEAL_SLOT -> db.mealPlanDao().get(id) != null
        RecordType.PANTRY_ITEM -> db.pantryDao().get(id) != null
        RecordType.SHOPPING_LIST -> db.shoppingDao().getList(id) != null
        RecordType.SHOPPING_ITEM -> db.shoppingDao().getItem(id) != null
        RecordType.TAGEBUCH_EINTRAG -> db.tagebuchDao().get(id) != null
    }

    private suspend fun applyLive(r: SyncRecord, now: Long, c: Counters, selfQueued: MutableMap<Pair<String, String>, Long>) {
        val (mapped, refs) = map(r) ?: return problem(r, "invalid_payload", now, c)
        if (refs.any { (type, id) -> !exists(type, id) }) return problem(r, "missing_reference", now, c)
        when (mapped) {
            is Mapped.Ingredient -> writeIngredient(mapped.entity, c, selfQueued)
            is Mapped.Recipe -> {
                val rd = db.recipeDao()
                rd.upsert(mapped.parts.recipe)
                rd.deleteIngredients(r.id)
                rd.deleteSteps(r.id)
                rd.insertIngredients(mapped.parts.lines)
                rd.insertSteps(mapped.parts.steps)
                // Fehlendes Foto vormerken (der Abruf folgt später), sonst einen alten Wunsch löschen
                mapped.parts.wantedPhoto
                    ?.let { dao.upsertPhotoWanted(SyncPhotoWantedEntity(r.id, it)) }
                    ?: dao.deletePhotoWanted(r.id)
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
            is Mapped.Tagebuch -> db.tagebuchDao().upsert(mapped.entity)
        }
        dao.setRev(SyncRecordRevEntity(r.type.wire, r.id, r.rev ?: 0L))
        // Das Problem „Foto nicht übertragbar“ bleibt, solange der Server-Stand kein Foto hat
        val keepProblem = mapped is Mapped.Recipe && mapped.remotePhoto == null &&
            mapped.parts.recipe.imageUri != null && hasUnsyncableProblem(r.id)
        if (!keepProblem) dao.clearProblem(r.type.wire, r.id)
        c.applied++
    }

    private suspend fun hasUnsyncableProblem(recipeId: String): Boolean =
        dao.hasProblem(RecordType.RECIPE.wire, recipeId, SyncLocalStore.PHOTO_UNSYNCABLE)

    /**
     * Schreibt eine Server-Zutat. Gibt es lokal eine andere Zutat mit gleichem Namen (ohne Groß-/Kleinschreibung),
     * wird sie in die Server-Zutat zusammengeführt (Server-ID gewinnt).
     */
    private suspend fun writeIngredient(remote: IngredientEntity, c: Counters, selfQueued: MutableMap<Pair<String, String>, Long>) {
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
        val queuedBefore = dao.outbox().associate { (it.type to it.recordId) to it.queuedAt }
        dao.setApplyingRemote(false)
        mergeIngredient(ing, renamed, remote, System.currentTimeMillis())
        dao.setApplyingRemote(true)
        for (e in dao.outbox()) {
            val key = e.type to e.recordId
            if (queuedBefore[key] != e.queuedAt) selfQueued[key] = e.queuedAt
        }
        dao.dequeue(RecordType.INGREDIENT.wire, local.id)
        // Hat die Zusammenführung die Server-Zutat nicht ergänzt, muss sie nicht zurückgesendet werden
        if (remote.fillFrom(renamed) == remote) dao.dequeue(RecordType.INGREDIENT.wire, remote.id)
        c.merged++
    }

    /**
     * Würde die Löschung lokal noch gebrauchte Daten reißen (FK RESTRICT oder Kaskade über offene Änderungen)?
     * Gilt für Server-Löschungen und den Voll-Abgleich (`SyncEngine`) gleichermaßen.
     */
    internal suspend fun isStillNeeded(type: RecordType, id: String): Boolean = when (type) {
        RecordType.INGREDIENT -> db.ingredientDao().usageCount(id) > 0 || dao.hasQueuedPantryFor(id) ||
            dao.hasQueuedShoppingItemsFor(id)
        RecordType.RECIPE -> dao.hasQueuedSlotsFor(id)
        RecordType.SHOPPING_LIST -> dao.hasQueuedItemsFor(id)
        else -> false
    }

    /** Löscht den lokalen Datensatz (Kinder per Kaskade); der Aufrufer setzt `applyingRemote`. */
    internal suspend fun deleteLocal(type: RecordType, id: String) {
        when (type) {
            RecordType.INGREDIENT -> db.ingredientDao().delete(id)
            RecordType.RECIPE -> {
                db.recipeDao().delete(id)
                dao.deletePhotoWanted(id)
            }
            RecordType.MEAL_SLOT -> db.mealPlanDao().delete(id)
            RecordType.PANTRY_ITEM -> db.pantryDao().delete(id)
            RecordType.SHOPPING_LIST -> db.shoppingDao().deleteList(id)
            RecordType.SHOPPING_ITEM -> db.shoppingDao().deleteItem(id)
            RecordType.TAGEBUCH_EINTRAG -> db.tagebuchDao().delete(id)
        }
    }

    private suspend fun applyDeletion(r: SyncRecord, c: Counters) {
        val rev = SyncRecordRevEntity(r.type.wire, r.id, r.rev ?: 0L)
        if (!exists(r.type, r.id)) {
            dao.setRev(rev)
            dao.clearProblem(r.type.wire, r.id)
            return
        }
        if (isStillNeeded(r.type, r.id)) {
            // Noch von Rezeptzeilen verwendet oder von Kaskade mit offener lokaler Änderung betroffen (Vorrat,
            // Planposition, Einkaufseintrag): nicht löschen, beim nächsten Push wiederbeleben (baseRev = rev)
            dao.setRev(rev)
            dao.enqueue(SyncOutboxEntity(r.type.wire, r.id, deleted = false, queuedAt = System.currentTimeMillis()))
            c.revived++
            return
        }
        deleteLocal(r.type, r.id)
        dao.setRev(rev)
        dao.clearProblem(r.type.wire, r.id)
        c.applied++
    }
}

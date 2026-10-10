package de.foody.app.sync

import androidx.room.withTransaction
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.RecipeEntity
import de.foody.app.data.db.SyncOutboxEntity
import de.foody.app.data.db.SyncStateEntity
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ein zu sendender Datensatz mit dem `queuedAt` des Outbox-Eintrags zum Zeitpunkt des Bauens. Nur wenn der Eintrag
 * nach dem Push noch dasselbe `queuedAt` hat, wurde nichts nachträglich geändert und er darf entfernt werden.
 */
data class PendingRecord(val record: SyncRecord, val queuedAt: Long, val photoProblem: String? = null) {
    /** Identität dieser Fassung des Eintrags: ein erneutes Vormerken ergibt ein anderes `queuedAt`. */
    val key: Triple<RecordType, String, Long> get() = Triple(record.type, record.id, queuedAt)
}

/**
 * Lokale Seite des Syncs: schaltet ihn ein/aus und baut aus der Outbox die Datensätze für den Push.
 * Die Outbox selbst füllen die Trigger (siehe `SyncTriggers`); das Entfernen nach erfolgreichem Push
 * ist Sache des späteren Sync-Ablaufs, [pendingRecords] verändert die Outbox nicht.
 */
@Singleton
class SyncLocalStore @Inject constructor(private val db: FoodyDatabase, private val photoIndex: PhotoIndex) {
    private val dao get() = db.syncDao()

    /**
     * Schaltet den Sync ein (Cursor zurück auf 0). Mit [uploadExisting] werden zusätzlich alle vorhandenen
     * Datensätze zum Senden vorgemerkt. Alles in einer Transaktion.
     */
    suspend fun activate(serverUrl: String, householdId: String, uploadExisting: Boolean) {
        db.withTransaction {
            val state = dao.getState() ?: SyncStateEntity()
            dao.upsertState(
                state.copy(
                    active = true,
                    serverUrl = serverUrl,
                    householdId = householdId,
                    cursor = 0,
                    lastError = null,
                ),
            )
            if (uploadExisting) enqueueAll()
        }
    }

    /** Schaltet den Sync aus und verwirft Outbox, Revisionen und Probleme. */
    suspend fun deactivate() {
        db.withTransaction {
            val state = dao.getState() ?: SyncStateEntity()
            dao.upsertState(state.copy(active = false, serverUrl = null, householdId = null, cursor = 0, lastError = null))
            dao.clearOutbox()
            dao.clearRevs()
            dao.clearProblems()
        }
    }

    /** Merkt jeden Wurzeldatensatz aller sechs Typen als lebend vor. */
    suspend fun enqueueAll() {
        dao.enqueueAllRoots(System.currentTimeMillis())
    }

    /**
     * Datensätze für den nächsten Push: lebende in [RecordType]-Reihenfolge (Abhängigkeiten zuerst), danach
     * Löschungen in umgekehrter Reihenfolge; höchstens [limit]. Eine lebend vorgemerkte Zeile, die inzwischen
     * fehlt, geht als Löschung hinaus.
     */
    suspend fun pendingRecords(limit: Int = Protocol.MAX_PUSH_RECORDS): List<SyncRecord> =
        pendingBatch(limit).map { it.record }

    /**
     * Wie [pendingRecords], mit dem zugehörigen `queuedAt` je Datensatz. Fassungen aus [exclude] (bereits in diesem
     * Lauf gesendet) werden übersprungen, damit dauerhaft vorgemerkte Einträge den Batch nicht blockieren.
     */
    suspend fun pendingBatch(
        limit: Int = Protocol.MAX_PUSH_RECORDS,
        exclude: Set<Triple<RecordType, String, Long>> = emptySet(),
    ): List<PendingRecord> {
        // Foto-Hashes vor der Lese-Transaktion bilden (Datei-I/O bis 10 MB je Foto darf keine Transaktion halten).
        val hashes = hashRecipePhotos(exclude)
        return db.withTransaction { buildBatch(limit, exclude, hashes) }
    }

    /**
     * Foto eines Rezepts zum Zeitpunkt des Hashens: Link und Zustand. [absent] = die Datei gibt es nicht (das Gerät hat
     * kein Foto); sonst [hash], oder `null` bei einem Lesefehler.
     */
    private class HashedPhoto(val uri: String, val hash: String?, val absent: Boolean, val unsyncable: Boolean = false)

    /** Hash je eigenem Foto der vorgemerkten, lebenden Rezepte (Schlüssel: Rezept-ID). Läuft ohne Transaktion. */
    private suspend fun hashRecipePhotos(exclude: Set<Triple<RecordType, String, Long>>): Map<String, HashedPhoto> {
        val result = HashMap<String, HashedPhoto>()
        for (e in dao.outbox()) {
            if (e.type != RecordType.RECIPE.wire || e.deleted) continue
            if (exclude.isNotEmpty() && Triple(RecordType.RECIPE, e.recordId, e.queuedAt) in exclude) continue
            val uri = db.recipeDao().get(e.recordId)?.imageUri ?: continue
            if (!photoIndex.isOwnPhoto(uri)) continue
            val file = photoIndex.fileOf(uri)?.takeIf { it.isFile }
            if (file == null) {
                result[e.recordId] = HashedPhoto(uri, null, absent = true)
                continue
            }
            // Was der Server dauerhaft ablehnt (zu groß, kein JPEG), erst verkleinern; hilft das nicht, geht das Rezept
            // ohne Foto raus (Problem `photo_unsyncable`), statt den Abgleich zu blockieren.
            var syncable = isSyncable(file)
            if (syncable == false && photoIndex.shrink(file)) syncable = isSyncable(file)
            if (syncable == null) { // unlesbar: zurückstellen
                result[e.recordId] = HashedPhoto(uri, null, absent = false)
                continue
            }
            if (!syncable) {
                result[e.recordId] = HashedPhoto(uri, null, absent = false, unsyncable = true)
                continue
            }
            result[e.recordId] = HashedPhoto(uri, photoIndex.hashOf(uri), absent = false)
        }
        return result
    }

    /** Höchstens 10 MB, nicht leer und mit JPEG-Signatur `FF D8 FF` – sonst lehnt der Server das Foto ab; `null` = unlesbar. */
    private fun isSyncable(file: java.io.File): Boolean? = try {
        val size = file.length()
        size in 1..Protocol.MAX_PHOTO_BYTES && file.inputStream().use { input ->
            val head = ByteArray(3)
            input.read(head) == 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()
        }
    } catch (_: java.io.IOException) {
        null // unlesbar (kein Dauerzustand): zurückstellen
    }

    private suspend fun buildBatch(
        limit: Int,
        exclude: Set<Triple<RecordType, String, Long>>,
        hashes: Map<String, HashedPhoto>,
    ): List<PendingRecord> {
        val types = RecordType.entries
        // Innerhalb eines Typs bleibt die Outbox-Reihenfolge (queuedAt) erhalten.
        val outbox = dao.outbox().filter { e ->
            exclude.isEmpty() || Triple(types.first { it.wire == e.type }, e.recordId, e.queuedAt) !in exclude
        }
        val byType = outbox.groupBy { e -> types.first { it.wire == e.type } }
        val live = types.flatMap { t -> byType[t].orEmpty().filter { !it.deleted }.map { t to it } }
        val deletions = types.flatMap { t -> byType[t].orEmpty().filter { it.deleted }.map { t to it } }

        val records = ArrayList<PendingRecord>()
        val vanished = ArrayList<Pair<RecordType, SyncOutboxEntity>>()
        for ((type, entry) in live) {
            if (records.size >= limit) break
            when (val built = build(type, entry, hashes)) {
                is Built.Record -> records += PendingRecord(built.record, entry.queuedAt, built.photoProblem)
                Built.Gone -> vanished += type to entry
                // Foto nicht bestimmbar: dieses Mal nicht senden, der Eintrag bleibt in der Outbox
                Built.Deferred -> Unit
            }
        }
        val allDeletions = (deletions + vanished).sortedWith(
            compareByDescending<Pair<RecordType, SyncOutboxEntity>> { it.first.ordinal }.thenBy { it.second.queuedAt },
        )
        for ((type, entry) in allDeletions) {
            if (records.size >= limit) break
            records += PendingRecord(
                SyncRecord(
                    id = entry.recordId,
                    type = type,
                    deleted = true,
                    updatedAt = entry.queuedAt,
                    baseRev = dao.revOf(type.wire, entry.recordId),
                ),
                entry.queuedAt,
            )
        }
        return records
    }

    private sealed interface Built {
        data class Record(val record: SyncRecord, val photoProblem: String? = null) : Built
        data object Gone : Built
        data object Deferred : Built
    }

    /**
     * Foto-Hash eines Rezepts, oder `null` = zurückstellen. Ein offener Foto-Wunsch geht vor (das Gerät hat das Foto
     * noch nicht geladen und darf es nicht als „kein Foto“ überschreiben); sonst der Hash des eigenen Fotos, `Optional.empty()`
     * ohne Foto oder bei fremdem Link. Ist ein eigenes Foto nicht hashbar (Datei fehlt kurz, Link seit dem Hashen
     * geändert), wird zurückgestellt – nie `photo = null` für ein eigenes Foto senden.
     */
    private suspend fun photoFor(recipe: RecipeEntity, hashes: Map<String, HashedPhoto>): Optional<String>? {
        dao.photoWanted(recipe.id)?.let { return Optional.of(it.sha256) }
        val uri = recipe.imageUri ?: return Optional.empty<String>()
        if (!photoIndex.isOwnPhoto(uri)) return Optional.empty<String>()
        val hashed = hashes[recipe.id]?.takeIf { it.uri == uri } ?: return null
        if (hashed.absent || hashed.unsyncable) return Optional.empty<String>() // Datei fehlt / nicht übertragbar
        return Optional.of(hashed.hash ?: return null)
    }

    /** Baut den lebenden Datensatz; [Built.Gone], wenn die Zeile nicht mehr existiert. */
    private suspend fun build(type: RecordType, e: SyncOutboxEntity, hashes: Map<String, HashedPhoto>): Built {
        val id = e.recordId
        var photoProblem: String? = null
        val (updatedAt, payload) = when (type) {
            RecordType.INGREDIENT -> db.ingredientDao().get(id)?.let { it.updatedAt to SyncMapper.ingredient(it) }
            RecordType.RECIPE -> db.recipeDao().get(id)?.let {
                val photo = photoFor(it, hashes) ?: return Built.Deferred
                if (hashes[id]?.unsyncable == true && it.imageUri == hashes[id]?.uri && dao.photoWanted(id) == null) {
                    photoProblem = PHOTO_UNSYNCABLE
                }
                it.updatedAt to SyncMapper.recipe(
                    it, db.recipeDao().getIngredients(id), db.recipeDao().getSteps(id), photo.orElse(null),
                )
            }
            RecordType.MEAL_SLOT -> db.mealPlanDao().get(id)?.let { it.updatedAt to SyncMapper.mealSlot(it) }
            RecordType.PANTRY_ITEM -> db.pantryDao().get(id)?.let { it.updatedAt to SyncMapper.pantryItem(it) }
            RecordType.SHOPPING_LIST -> db.shoppingDao().getList(id)?.let { it.updatedAt to SyncMapper.shoppingList(it) }
            RecordType.SHOPPING_ITEM -> db.shoppingDao().getItem(id)?.let {
                it.updatedAt to SyncMapper.shoppingItem(it, db.shoppingDao().getSources(id))
            }
            RecordType.TAGEBUCH_EINTRAG -> db.tagebuchDao().get(id)?.let { it.updatedAt to SyncMapper.tagebuch(it) }
        } ?: return Built.Gone
        return Built.Record(
            SyncRecord(
                id = id,
                type = type,
                deleted = false,
                updatedAt = updatedAt,
                baseRev = dao.revOf(type.wire, id),
                payload = SyncMapper.toJson(payload),
            ),
            photoProblem,
        )
    }

    companion object {
        /** Problem-Code: das eigene Foto ist dauerhaft nicht übertragbar, das Rezept geht ohne Foto raus. */
        const val PHOTO_UNSYNCABLE = "photo_unsyncable"
    }
}

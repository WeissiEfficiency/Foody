package de.foody.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Warteschlange lokaler Änderungen, die noch an den Server gesendet werden müssen. Wird ausschließlich von den
 * SQLite-Triggern in [SyncTriggers] befüllt. [type] = `RecordType.wire`.
 */
@Entity(tableName = "sync_outbox", primaryKeys = ["type", "recordId"])
data class SyncOutboxEntity(
    val type: String,
    val recordId: String,
    /** true = Datensatz wurde lokal gelöscht. */
    val deleted: Boolean,
    val queuedAt: Long,
)

/** Zuletzt vom Server bestätigte Revision je Datensatz (Grundlage für `baseRev` beim Senden). */
@Entity(tableName = "sync_record_rev", primaryKeys = ["type", "recordId"])
data class SyncRecordRevEntity(
    val type: String,
    val recordId: String,
    val rev: Long,
)

/** Genau eine Zeile (`id = 1`): Zustand der Synchronisation. Ohne `active = 1` bleibt der Sync vollständig inaktiv. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(defaultValue = "0") val active: Boolean = false,
    /** Solange 1, queuen die Trigger nichts (Server-Daten werden gerade angewendet). */
    @ColumnInfo(defaultValue = "0") val applyingRemote: Boolean = false,
    val serverUrl: String? = null,
    val householdId: String? = null,
    @ColumnInfo(defaultValue = "0") val cursor: Long = 0,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
)

/** Datensatz, den der Server abgelehnt hat oder der lokal nicht angewendet werden konnte. */
@Entity(tableName = "sync_problem", primaryKeys = ["type", "recordId"])
data class SyncProblemEntity(
    val type: String,
    val recordId: String,
    val code: String,
    val at: Long,
)

package de.foody.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Sync-Tabellen von foody.db, Version 10 – wie Schema 9, nur `sync_state.kontoId` statt `householdId`.
 */

/**
 * Lokale Änderung, die noch an den Server gesendet werden muss (`sync_outbox`). Befüllt von den SQLite-Triggern;
 * [type] ist der Datensatztyp im Sync-Protokoll.
 */
@Entity(tableName = "sync_outbox", primaryKeys = ["type", "recordId"])
data class SyncOutboxEntity(
    val type: String,
    val recordId: String,
    /** true = Datensatz wurde lokal gelöscht. */
    val deleted: Boolean,
    val queuedAt: Long,
)

/** Zuletzt vom Server bestätigte Revision je Datensatz (`sync_record_rev`), Grundlage für `baseRev` beim Senden. */
@Entity(tableName = "sync_record_rev", primaryKeys = ["type", "recordId"])
data class SyncRevisionEntity(
    val type: String,
    val recordId: String,
    val rev: Long,
)

/**
 * Zustand der Synchronisation (`sync_state`), genau eine Zeile mit `id = 1`. Ohne `active = 1` bleibt der Sync
 * vollständig inaktiv.
 */
@Entity(tableName = "sync_state")
data class SyncZustandEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(defaultValue = "0") val active: Boolean = false,
    /** Solange true, merken die Trigger nichts vor (Server-Daten werden gerade angewendet). */
    @ColumnInfo(defaultValue = "0") val applyingRemote: Boolean = false,
    val serverUrl: String? = null,
    /** Konto beim Server; wird bei der Anmeldung gesetzt, der Sync selbst braucht es nicht. */
    val kontoId: String? = null,
    @ColumnInfo(defaultValue = "0") val cursor: Long = 0,
    val lastSyncAt: Long? = null,
    /** Feste Fehlerkennung des letzten Laufs, nie Token oder Antwort-Bodies. */
    val lastError: String? = null,
)

/** Datensatz, den der Server abgelehnt hat oder der lokal nicht angewendet werden konnte (`sync_problem`). */
@Entity(tableName = "sync_problem", primaryKeys = ["type", "recordId"])
data class SyncProblemEntity(
    val type: String,
    val recordId: String,
    val code: String,
    val at: Long,
)

/** Ein Eintrag aus `sync_problem` mit dem Namen des lokalen Datensatzes ([SyncDao.problemeMitNamen]). */
data class SyncProblemMitName(val typ: String, val id: String, val code: String, val seit: Long, val name: String?)

/**
 * Hash-Zwischenspeicher je lokaler Fotodatei (`sync_photo_local`): [sha256] gilt, solange [size] und [modifiedAt]
 * (Änderungszeit in ms) zur Datei passen. Kein Sync-Datensatz, die Trigger ignorieren die Tabelle.
 */
@Entity(tableName = "sync_photo_local", indices = [Index("sha256")])
data class SyncFotoLokalEntity(
    @PrimaryKey val uri: String,
    val sha256: String,
    val size: Long,
    val modifiedAt: Long,
)

/** Foto, das ein vom Server übernommenes Rezept braucht, das lokal aber (noch) fehlt (`sync_photo_wanted`). */
@Entity(tableName = "sync_photo_wanted")
data class SyncFotoGewuenschtEntity(
    @PrimaryKey val recipeId: String,
    val sha256: String,
)

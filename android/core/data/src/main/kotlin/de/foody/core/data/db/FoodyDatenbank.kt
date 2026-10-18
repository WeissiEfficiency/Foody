package de.foody.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Lokale Datenbank der V3 (`foody.db`). Version 10 = Schema 9 der v1 ohne `pantry_item` und mit
 * `sync_state.kontoId` statt `householdId`; sie entsteht auf dem Handy per Migration 9 → 10 aus der v1-Datenbank.
 * Schemata liegen unter `schemas/`; nie destruktive Migrationen.
 */
@Database(
    entities = [
        ZutatEntity::class,
        RezeptEntity::class,
        RezeptZutatEntity::class,
        ArbeitsschrittEntity::class,
        PlanEintragEntity::class,
        EinkaufslisteEntity::class,
        EinkaufsartikelEntity::class,
        EinkaufsartikelQuelleEntity::class,
        TagebuchEintragEntity::class,
        SyncOutboxEntity::class,
        SyncRevisionEntity::class,
        SyncZustandEntity::class,
        SyncProblemEntity::class,
        SyncFotoLokalEntity::class,
        SyncFotoGewuenschtEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FoodyDatenbank : RoomDatabase() {
    abstract fun zutatDao(): ZutatDao

    abstract fun rezeptDao(): RezeptDao

    abstract fun rezeptZutatDao(): RezeptZutatDao

    abstract fun arbeitsschrittDao(): ArbeitsschrittDao

    abstract fun planEintragDao(): PlanEintragDao

    abstract fun einkaufslisteDao(): EinkaufslisteDao

    abstract fun einkaufsartikelDao(): EinkaufsartikelDao

    abstract fun einkaufsartikelQuelleDao(): EinkaufsartikelQuelleDao

    abstract fun tagebuchDao(): TagebuchDao

    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "foody.db"

        /** Pflicht beim Bauen: legt bei einer neuen Datenbank `sync_state` und die [SyncTrigger] an. */
        val CALLBACK: RoomDatabase.Callback = SyncTrigger.Callback
    }
}

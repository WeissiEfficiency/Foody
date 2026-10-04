package de.foody.server

import de.foody.server.auth.AccountStore
import de.foody.server.auth.LoginThrottle
import de.foody.server.auth.PasswordHasher
import de.foody.server.db.Database
import de.foody.server.sync.RecordStore
import de.foody.server.sync.SyncService
import java.time.Clock

/** Abhängigkeiten des Servers; spätere Tasks ergänzen Felder und die Fabrik [create]. */
class ServerDeps(
    val config: ServerConfig,
    val db: Database,
    val clock: Clock,
    val hasher: PasswordHasher,
    val accounts: AccountStore,
    val throttle: LoginThrottle,
    val sync: SyncService,
) {
    companion object {
        /** Einzige Konstruktionsstelle, genutzt von `main` und der Testhilfe. */
        fun create(config: ServerConfig, db: Database, clock: Clock = Clock.systemUTC()): ServerDeps {
            val hasher = PasswordHasher()
            return ServerDeps(
                config, db, clock, hasher, AccountStore(db, clock, hasher), LoginThrottle(clock),
                SyncService(db, RecordStore(), clock),
            )
        }
    }
}

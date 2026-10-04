package de.foody.server

import de.foody.server.db.Database
import java.time.Clock

/** Abhängigkeiten des Servers; spätere Tasks ergänzen Felder und die Fabrik [create]. */
class ServerDeps(
    val config: ServerConfig,
    val db: Database,
    val clock: Clock,
) {
    companion object {
        /** Einzige Konstruktionsstelle, genutzt von `main` und der Testhilfe. */
        fun create(config: ServerConfig, db: Database, clock: Clock = Clock.systemUTC()): ServerDeps =
            ServerDeps(config, db, clock)
    }
}

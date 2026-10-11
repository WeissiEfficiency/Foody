package de.foody.server

/** Konfiguration des Servers, gelesen aus Umgebungsvariablen. */
data class ServerConfig(
    val dbPath: String,
    val port: Int,
    val adminUser: String?,
    val adminPassword: String?,
    /** Wurzelordner der Fotoablage (`FOODY_PHOTO_DIR`). */
    val photoDir: String = "/data/photos",
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig = ServerConfig(
            dbPath = env["FOODY_DB_PATH"] ?: "/data/foody.db",
            port = env["FOODY_PORT"]?.toIntOrNull() ?: 8080,
            adminUser = env["FOODY_ADMIN_USER"],
            adminPassword = env["FOODY_ADMIN_PASSWORD"],
            photoDir = env["FOODY_PHOTO_DIR"] ?: "/data/photos",
        )
    }
}

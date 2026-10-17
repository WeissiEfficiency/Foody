package de.foody.server

/** Konfiguration des Servers, gelesen aus Umgebungsvariablen. */
data class ServerConfig(
    val dbPath: String,
    val port: Int,
    val adminUser: String?,
    val adminPassword: String?,
    /** Wurzelordner der Fotoablage (`FOODY_PHOTO_DIR`). */
    val photoDir: String = "/data/photos",
    /** Schlüssel des Sprachmodell-Dienstes (`FOODY_KI_KEY`); `null` = KI aus. Nie loggen oder ausgeben. */
    val kiKey: String? = null,
    /** KI-Anfragen je Konto und Stunde (`FOODY_KI_PRO_STUNDE`). */
    val kiProStunde: Int = KI_PRO_STUNDE_STANDARD,
) {
    /** Ohne Geheimnisse: Schlüssel und Passwort erscheinen nur als gesetzt/nicht gesetzt. */
    override fun toString(): String =
        "ServerConfig(dbPath=$dbPath, port=$port, adminUser=$adminUser, adminPassword=${verdeckt(adminPassword)}, " +
            "photoDir=$photoDir, kiKey=${verdeckt(kiKey)}, kiProStunde=$kiProStunde)"

    companion object {
        const val KI_PRO_STUNDE_STANDARD = 30

        private fun verdeckt(wert: String?) = if (wert == null) "null" else "***"

        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig = ServerConfig(
            dbPath = env["FOODY_DB_PATH"] ?: "/data/foody.db",
            port = env["FOODY_PORT"]?.toIntOrNull() ?: 8080,
            adminUser = env["FOODY_ADMIN_USER"],
            adminPassword = env["FOODY_ADMIN_PASSWORD"],
            photoDir = env["FOODY_PHOTO_DIR"] ?: "/data/photos",
            kiKey = env["FOODY_KI_KEY"]?.trim()?.takeIf { it.isNotEmpty() },
            // Ungültig oder nicht positiv: Standard statt Absturz.
            kiProStunde = env["FOODY_KI_PRO_STUNDE"]?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: KI_PRO_STUNDE_STANDARD,
        )
    }
}

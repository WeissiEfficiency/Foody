package de.foody.server.auth

import de.foody.server.ApiException
import de.foody.server.db.Database
import de.foody.sync.protocol.ErrorCode
import io.ktor.http.HttpStatusCode
import java.sql.Connection
import java.time.Clock
import java.util.UUID

/** Authentifiziertes Gerät des Kontos [kontoId] (= `user.id`). */
data class DevicePrincipal(val deviceId: String, val kontoId: String)

/** Konten, Anmeldung und Gerätetoken. */
class AccountStore(
    private val db: Database,
    private val clock: Clock,
    private val hasher: PasswordHasher,
) {
    /** Fester Hash, gegen den bei unbekanntem Benutzer geprüft wird, damit die Laufzeit nichts verrät. */
    private val dummyHash: String by lazy { hasher.hash("dummy-password-for-timing") }

    /** Legt das erste Konto (ohne Sonderrechte) an, aber nur wenn noch kein Konto existiert. */
    fun bootstrapAdmin(username: String?, password: String?): Boolean {
        if (username.isNullOrBlank() || password.isNullOrEmpty()) return false
        if (hasUsers()) return false
        createUser(username, password)
        return true
    }

    /** Gibt es schon mindestens einen Benutzer? */
    fun hasUsers(): Boolean = db.tx { c ->
        c.createStatement().use { s -> s.executeQuery("SELECT COUNT(*) FROM user").use { it.next(); it.getInt(1) > 0 } }
    }

    /** Legt ein Konto an und liefert seine ID. */
    fun createUser(username: String, password: String): String {
        validateUsername(username)
        validatePassword(password)
        // Hashen außerhalb der Transaktion: Argon2 ist teuer, die Verbindung soll frei bleiben.
        val hash = hasher.hash(password)
        return db.tx { c -> insertUser(c, username, hash) }
    }

    /** Fügt ein Konto in der laufenden Transaktion ein; `409 username_taken` bei Namenskollision. */
    private fun insertUser(c: Connection, username: String, hash: String): String {
        val taken = c.prepareStatement("SELECT 1 FROM user WHERE username_lower = ?").use { st ->
            st.setString(1, username.lowercase())
            st.executeQuery().use { it.next() }
        }
        if (taken) throw ApiException(HttpStatusCode.Conflict, ErrorCode.USERNAME_TAKEN)
        val id = UUID.randomUUID().toString()
        c.prepareStatement(
            "INSERT INTO user (id, username, username_lower, password_hash, display_name, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
        ).use { st ->
            st.setString(1, id)
            st.setString(2, username)
            st.setString(3, username.lowercase())
            st.setString(4, hash)
            st.setString(5, username)
            st.setLong(6, clock.millis())
            st.executeUpdate()
        }
        return id
    }

    /** Liefert die Konto-ID bei richtigem Passwort, sonst `null`. */
    fun authenticate(username: String, password: String): String? {
        val row = db.tx { c ->
            c.prepareStatement("SELECT id, password_hash FROM user WHERE username_lower = ?").use { st ->
                st.setString(1, username.lowercase())
                st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) to rs.getString(2) else null }
            }
        }
        if (row == null) {
            hasher.verify(password, dummyHash)
            return null
        }
        return if (hasher.verify(password, row.second)) row.first else null
    }

    /** Legt ein Gerät des Kontos an und liefert das Klartext-Token (gespeichert wird nur der Hash). */
    fun createDevice(kontoId: String, name: String): String = db.tx { c ->
        val token = Tokens.newToken()
        val now = clock.millis()
        c.prepareStatement(
            "INSERT INTO device (id, user_id, name, token_hash, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?, ?)",
        ).use { st ->
            st.setString(1, UUID.randomUUID().toString())
            st.setString(2, kontoId)
            st.setString(3, name)
            st.setString(4, Tokens.sha256Hex(token))
            st.setLong(5, now)
            st.setLong(6, now)
            st.executeUpdate()
        }
        token
    }

    /**
     * Löst ein Token auf. `null` bei unbekanntem oder widerrufenem Token oder wenn das Konto nicht mehr existiert.
     * `last_seen_at` wird höchstens einmal pro Minute geschrieben.
     */
    fun deviceForToken(token: String): DevicePrincipal? = db.tx { c ->
        val now = clock.millis()
        val row = c.prepareStatement(
            "SELECT d.id, d.user_id, d.last_seen_at " +
                "FROM device d JOIN user u ON u.id = d.user_id WHERE d.token_hash = ? AND d.revoked_at IS NULL",
        ).use { st ->
            st.setString(1, Tokens.sha256Hex(token))
            st.executeQuery().use { rs ->
                if (rs.next()) DevicePrincipal(rs.getString(1), rs.getString(2)) to rs.getLong(3) else null
            }
        } ?: return@tx null
        if (now - row.second >= LAST_SEEN_INTERVAL_MS) {
            c.prepareStatement("UPDATE device SET last_seen_at = ? WHERE id = ?").use { st ->
                st.setLong(1, now)
                st.setString(2, row.first.deviceId)
                st.executeUpdate()
            }
        }
        row.first
    }

    /** Widerruft das Gerät [deviceId] („Trennen“); ein schon widerrufenes bleibt unverändert. */
    fun abmelden(deviceId: String) {
        db.tx { c ->
            c.prepareStatement("UPDATE device SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL").use { st ->
                st.setLong(1, clock.millis())
                st.setString(2, deviceId)
                st.executeUpdate()
            }
        }
    }

    /**
     * Ändert das Passwort und widerruft alle anderen Geräte des Kontos.
     * Falsches [old] → `401 invalid_credentials`.
     */
    fun changePassword(kontoId: String, currentDeviceId: String, old: String, new: String) {
        validatePassword(new)
        val current = db.tx { c ->
            c.prepareStatement("SELECT password_hash FROM user WHERE id = ?").use { st ->
                st.setString(1, kontoId)
                st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        } ?: throw ApiException(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
        if (!hasher.verify(old, current)) {
            throw ApiException(HttpStatusCode.Unauthorized, ErrorCode.INVALID_CREDENTIALS)
        }
        val hash = hasher.hash(new)
        db.tx { c ->
            c.prepareStatement("UPDATE user SET password_hash = ? WHERE id = ?").use { st ->
                st.setString(1, hash)
                st.setString(2, kontoId)
                st.executeUpdate()
            }
            c.prepareStatement(
                "UPDATE device SET revoked_at = ? WHERE user_id = ? AND id <> ? AND revoked_at IS NULL",
            ).use { st ->
                st.setLong(1, clock.millis())
                st.setString(2, kontoId)
                st.setString(3, currentDeviceId)
                st.executeUpdate()
            }
        }
    }

    /**
     * Setzt das Passwort des Benutzers (Name ohne Beachtung der Groß-/Kleinschreibung) neu und widerruft alle
     * seine Geräte. `false`, wenn der Benutzer nicht existiert. Für die Admin-CLI.
     */
    fun resetPassword(username: String, newPassword: String): Boolean {
        validatePassword(newPassword)
        val hash = hasher.hash(newPassword)
        return db.tx { c ->
            val userId = c.prepareStatement("SELECT id FROM user WHERE username_lower = ?").use { st ->
                st.setString(1, username.lowercase())
                st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            } ?: return@tx false
            c.prepareStatement("UPDATE user SET password_hash = ? WHERE id = ?").use { st ->
                st.setString(1, hash)
                st.setString(2, userId)
                st.executeUpdate()
            }
            c.prepareStatement("UPDATE device SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL").use { st ->
                st.setLong(1, clock.millis())
                st.setString(2, userId)
                st.executeUpdate()
            }
            true
        }
    }

    private fun validateUsername(username: String) {
        if (!USERNAME.matches(username)) throw ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INPUT)
    }

    private fun validatePassword(password: String) {
        if (password.length !in 10..200) throw ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INPUT)
    }

    private companion object {
        val USERNAME = Regex("^[A-Za-z0-9._-]{3,32}$")
        const val LAST_SEEN_INTERVAL_MS = 60_000L
    }
}

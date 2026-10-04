package de.foody.server.auth

import de.foody.server.ApiException
import de.foody.server.db.Database
import de.foody.sync.protocol.ErrorCode
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.util.UUID

/** Authentifiziertes Gerät; [householdId] ist `null`, solange es keinem Haushalt zugeordnet ist. */
data class DevicePrincipal(val deviceId: String, val userId: String, val householdId: String?)

/** Benutzer, Anmeldung und Gerätetoken. */
class AccountStore(
    private val db: Database,
    private val clock: Clock,
    private val hasher: PasswordHasher,
) {
    /** Fester Hash, gegen den bei unbekanntem Benutzer geprüft wird, damit die Laufzeit nichts verrät. */
    private val dummyHash: String by lazy { hasher.hash("dummy-password-for-timing") }

    /** Legt den ersten Admin an, aber nur wenn noch kein Benutzer existiert. */
    fun bootstrapAdmin(username: String?, password: String?): Boolean {
        if (username.isNullOrBlank() || password.isNullOrEmpty()) return false
        if (hasUsers()) return false
        createUser(username, password, isAdmin = true)
        return true
    }

    /** Gibt es schon mindestens einen Benutzer? */
    fun hasUsers(): Boolean = db.tx { c ->
        c.createStatement().use { s -> s.executeQuery("SELECT COUNT(*) FROM user").use { it.next(); it.getInt(1) > 0 } }
    }

    /** Legt einen Benutzer an und liefert seine ID. */
    fun createUser(username: String, password: String, isAdmin: Boolean = false): String {
        validateUsername(username)
        validatePassword(password)
        // Hashen außerhalb der Transaktion: Argon2 ist teuer, die Verbindung soll frei bleiben.
        val hash = hasher.hash(password)
        val id = UUID.randomUUID().toString()
        db.tx { c ->
            val taken = c.prepareStatement("SELECT 1 FROM user WHERE username_lower = ?").use { st ->
                st.setString(1, username.lowercase())
                st.executeQuery().use { it.next() }
            }
            if (taken) throw ApiException(HttpStatusCode.Conflict, ErrorCode.USERNAME_TAKEN)
            c.prepareStatement(
                "INSERT INTO user (id, username, username_lower, password_hash, display_name, is_admin, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
            ).use { st ->
                st.setString(1, id)
                st.setString(2, username)
                st.setString(3, username.lowercase())
                st.setString(4, hash)
                st.setString(5, username)
                st.setInt(6, if (isAdmin) 1 else 0)
                st.setLong(7, clock.millis())
                st.executeUpdate()
            }
        }
        return id
    }

    /** Liefert die Benutzer-ID bei richtigem Passwort, sonst `null`. */
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

    /** Haushalt, in dem der Benutzer Mitglied ist (der älteste), sonst `null`. */
    fun householdOf(userId: String): String? = db.tx { c ->
        c.prepareStatement(
            "SELECT household_id FROM membership WHERE user_id = ? ORDER BY created_at, household_id LIMIT 1",
        ).use { st ->
            st.setString(1, userId)
            st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    /** Legt ein Gerät an und liefert das Klartext-Token (gespeichert wird nur der Hash). */
    fun createDevice(userId: String, householdId: String?, name: String): String {
        val token = Tokens.newToken()
        val now = clock.millis()
        db.tx { c ->
            c.prepareStatement(
                "INSERT INTO device (id, user_id, household_id, name, token_hash, created_at, last_seen_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
            ).use { st ->
                st.setString(1, UUID.randomUUID().toString())
                st.setString(2, userId)
                st.setString(3, householdId)
                st.setString(4, name)
                st.setString(5, Tokens.sha256Hex(token))
                st.setLong(6, now)
                st.setLong(7, now)
                st.executeUpdate()
            }
        }
        return token
    }

    /**
     * Löst ein Token auf. `null` bei unbekanntem oder widerrufenem Token oder wenn der Benutzer nicht
     * mehr Mitglied des Haushalts des Geräts ist. `last_seen_at` wird höchstens einmal pro Minute geschrieben.
     */
    fun deviceForToken(token: String): DevicePrincipal? = db.tx { c ->
        val now = clock.millis()
        val row = c.prepareStatement(
            "SELECT d.id, d.user_id, d.household_id, d.last_seen_at, " +
                "(SELECT COUNT(*) FROM membership m WHERE m.user_id = d.user_id AND m.household_id = d.household_id) " +
                "FROM device d JOIN user u ON u.id = d.user_id WHERE d.token_hash = ? AND d.revoked_at IS NULL",
        ).use { st ->
            st.setString(1, Tokens.sha256Hex(token))
            st.executeQuery().use { rs ->
                if (!rs.next()) return@use null
                val household = rs.getString(3)
                if (household != null && rs.getInt(5) == 0) return@use null
                DevicePrincipal(rs.getString(1), rs.getString(2), household) to rs.getLong(4)
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

    /** Geräte des Benutzers (ohne widerrufene), älteste zuerst. */
    fun devicesOf(userId: String): List<DeviceRow> = db.tx { c ->
        c.prepareStatement(
            "SELECT id, name, last_seen_at FROM device WHERE user_id = ? AND revoked_at IS NULL ORDER BY created_at, id",
        ).use { st ->
            st.setString(1, userId)
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(DeviceRow(rs.getString(1), rs.getString(2), rs.getLong(3))) }
            }
        }
    }

    /**
     * Ändert das Passwort und widerruft alle anderen Geräte des Benutzers.
     * Falsches [old] → `401 invalid_credentials`.
     */
    fun changePassword(userId: String, currentDeviceId: String, old: String, new: String) {
        validatePassword(new)
        val current = db.tx { c ->
            c.prepareStatement("SELECT password_hash FROM user WHERE id = ?").use { st ->
                st.setString(1, userId)
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
                st.setString(2, userId)
                st.executeUpdate()
            }
            c.prepareStatement(
                "UPDATE device SET revoked_at = ? WHERE user_id = ? AND id <> ? AND revoked_at IS NULL",
            ).use { st ->
                st.setLong(1, clock.millis())
                st.setString(2, userId)
                st.setString(3, currentDeviceId)
                st.executeUpdate()
            }
        }
    }

    private fun validateUsername(username: String) {
        if (!USERNAME.matches(username)) throw ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INPUT)
    }

    private fun validatePassword(password: String) {
        if (password.length !in 10..200) throw ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INPUT)
    }

    /** Zeile der Geräteliste. */
    data class DeviceRow(val id: String, val name: String, val lastSeenAt: Long)

    private companion object {
        val USERNAME = Regex("^[A-Za-z0-9._-]{3,32}$")
        const val LAST_SEEN_INTERVAL_MS = 60_000L
    }
}

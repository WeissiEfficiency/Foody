package de.foody.server.auth

import de.foody.server.ApiException
import de.foody.server.db.Database
import de.foody.sync.protocol.ErrorCode
import de.foody.sync.protocol.AuthResponse
import de.foody.sync.protocol.DeviceDto
import de.foody.sync.protocol.HouseholdDto
import de.foody.sync.protocol.InviteDto
import io.ktor.http.HttpStatusCode
import java.security.SecureRandom
import java.sql.Connection
import java.time.Clock
import java.time.Duration
import java.util.UUID

/** Authentifiziertes Gerät; [householdId] ist `null`, solange es keinem Haushalt zugeordnet ist. */
data class DevicePrincipal(val deviceId: String, val userId: String, val householdId: String?)

/** Benutzer, Anmeldung und Gerätetoken. */
class AccountStore(
    private val db: Database,
    private val clock: Clock,
    private val hasher: PasswordHasher,
) {
    private val random = SecureRandom()

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
        return db.tx { c -> insertUser(c, username, hash, isAdmin) }
    }

    /** Fügt einen Benutzer in der laufenden Transaktion ein; `409 username_taken` bei Namenskollision. */
    private fun insertUser(c: Connection, username: String, hash: String, isAdmin: Boolean): String {
        val taken = c.prepareStatement("SELECT 1 FROM user WHERE username_lower = ?").use { st ->
            st.setString(1, username.lowercase())
            st.executeQuery().use { it.next() }
        }
        if (taken) throw ApiException(HttpStatusCode.Conflict, ErrorCode.USERNAME_TAKEN)
        val id = UUID.randomUUID().toString()
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

    /** Haushalt des Benutzers, aber nur bei genau einer Mitgliedschaft; sonst `null` (Gerät bleibt ungebunden). */
    fun soleHouseholdOf(userId: String): String? = db.tx { c ->
        c.prepareStatement("SELECT household_id FROM membership WHERE user_id = ? LIMIT 2").use { st ->
            st.setString(1, userId)
            st.executeQuery().use { rs ->
                val first = if (rs.next()) rs.getString(1) else null
                if (first != null && !rs.next()) first else null
            }
        }
    }

    /** Legt ein Gerät an und liefert das Klartext-Token (gespeichert wird nur der Hash). */
    fun createDevice(userId: String, householdId: String?, name: String): String =
        db.tx { c -> insertDevice(c, userId, householdId, name) }

    private fun insertDevice(c: Connection, userId: String, householdId: String?, name: String): String {
        val token = Tokens.newToken()
        val now = clock.millis()
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

    /** Geräte des Benutzers (ohne widerrufene), älteste zuerst; [currentDeviceId] ist als `current` markiert. */
    fun devices(userId: String, currentDeviceId: String): List<DeviceDto> = db.tx { c ->
        c.prepareStatement(
            "SELECT id, name, last_seen_at FROM device WHERE user_id = ? AND revoked_at IS NULL ORDER BY created_at, id",
        ).use { st ->
            st.setString(1, userId)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val id = rs.getString(1)
                        add(DeviceDto(id, rs.getString(2), rs.getLong(3), current = id == currentDeviceId))
                    }
                }
            }
        }
    }

    /** Widerruft ein Gerät des Benutzers; fremde, unbekannte oder schon widerrufene → `404 not_found`. */
    fun revokeDevice(userId: String, deviceId: String) {
        val changed = db.tx { c ->
            c.prepareStatement(
                "UPDATE device SET revoked_at = ? WHERE id = ? AND user_id = ? AND revoked_at IS NULL",
            ).use { st ->
                st.setLong(1, clock.millis())
                st.setString(2, deviceId)
                st.setString(3, userId)
                st.executeUpdate()
            }
        }
        if (changed == 0) throw ApiException(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND)
    }

    /**
     * Legt einen Haushalt an, macht [userId] zum `owner` und bindet das aufrufende Gerät [deviceId] daran.
     * Der Name wird getrimmt und muss 1–100 Zeichen lang sein.
     */
    fun createHousehold(userId: String, deviceId: String, name: String): HouseholdDto {
        val trimmed = name.trim()
        if (trimmed.length !in 1..MAX_HOUSEHOLD_NAME) {
            throw ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INPUT)
        }
        val id = UUID.randomUUID().toString()
        db.tx { c ->
            c.prepareStatement("INSERT INTO household (id, name, created_by, created_at) VALUES (?, ?, ?, ?)").use { st ->
                st.setString(1, id)
                st.setString(2, trimmed)
                st.setString(3, userId)
                st.setLong(4, clock.millis())
                st.executeUpdate()
            }
            insertMembership(c, userId, id, ROLE_OWNER)
            bindDevice(c, deviceId, userId, id)
        }
        return HouseholdDto(id, trimmed, ROLE_OWNER)
    }

    /** Haushalte, in denen [userId] Mitglied ist, mit seiner Rolle. */
    fun households(userId: String): List<HouseholdDto> = db.tx { c ->
        c.prepareStatement(
            "SELECT h.id, h.name, m.role FROM membership m JOIN household h ON h.id = m.household_id " +
                "WHERE m.user_id = ? ORDER BY h.created_at, h.id",
        ).use { st ->
            st.setString(1, userId)
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(HouseholdDto(rs.getString(1), rs.getString(2), rs.getString(3))) }
            }
        }
    }

    /** Bindet das Gerät an einen Haushalt des Benutzers; ohne Mitgliedschaft `403 forbidden`. */
    fun selectHousehold(deviceId: String, userId: String, householdId: String) {
        db.tx { c ->
            if (roleOf(c, userId, householdId) == null) {
                throw ApiException(HttpStatusCode.Forbidden, ErrorCode.FORBIDDEN)
            }
            bindDevice(c, deviceId, userId, householdId)
        }
    }

    /**
     * Erzeugt eine einmalig nutzbare Einladung (7 Tage gültig); gespeichert wird nur der SHA-256
     * der 8 normalisierten Zeichen. Ohne [householdId] landet der Eingeladene in keinem Haushalt.
     */
    fun createInvite(createdBy: String, householdId: String?): InviteDto {
        val code = InviteCodes.generate(random)
        val expiresAt = clock.instant().plus(INVITE_VALIDITY).toEpochMilli()
        db.tx { c ->
            c.prepareStatement(
                "INSERT INTO invite (id, code_hash, household_id, created_by, expires_at) VALUES (?, ?, ?, ?, ?)",
            ).use { st ->
                st.setString(1, UUID.randomUUID().toString())
                st.setString(2, Tokens.sha256Hex(InviteCodes.normalize(code)!!))
                st.setString(3, householdId)
                st.setString(4, createdBy)
                st.setLong(5, expiresAt)
                st.executeUpdate()
            }
        }
        return InviteDto(code, expiresAt)
    }

    /**
     * Löst eine Einladung ein: prüft sie, legt Benutzer, Mitgliedschaft und Gerät an und verbraucht die
     * Einladung, alles in einer Transaktion. Scheitert irgendein Schritt, bleibt die Einladung unbenutzt.
     * Unbekannt, abgelaufen oder benutzt → `400 invalid_invite`.
     */
    fun redeemInvite(code: String, username: String, password: String, deviceName: String): AuthResponse {
        val normalized = InviteCodes.normalize(code)
            ?: throw ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INVITE)
        validateUsername(username)
        validatePassword(password)
        val hash = hasher.hash(password)
        val now = clock.millis()
        return db.tx { c ->
            val invite = c.prepareStatement(
                "SELECT id, household_id, expires_at, used_at, created_by FROM invite WHERE code_hash = ?",
            ).use { st ->
                st.setString(1, Tokens.sha256Hex(normalized))
                st.executeQuery().use { rs ->
                    if (!rs.next()) return@use null
                    InviteRow(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getObject(4) != null, rs.getString(5))
                }
            }
            if (invite == null || invite.used || invite.expiresAt <= now) {
                throw ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INVITE)
            }
            // Einladungen entfernter Mitglieder sind ungültig (Haushalts-lose Admin-Einladungen ausgenommen).
            if (invite.householdId != null && roleOf(c, invite.createdBy, invite.householdId) == null) {
                throw ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INVITE)
            }
            val userId = insertUser(c, username, hash, isAdmin = false)
            if (invite.householdId != null) insertMembership(c, userId, invite.householdId, ROLE_MEMBER)
            c.prepareStatement("UPDATE invite SET used_at = ? WHERE id = ?").use { st ->
                st.setLong(1, now)
                st.setString(2, invite.id)
                st.executeUpdate()
            }
            val token = insertDevice(c, userId, invite.householdId, deviceName)
            AuthResponse(token, userId, invite.householdId)
        }
    }

    /**
     * Entfernt [memberId] aus dem Haushalt und widerruft dessen Geräte für diesen Haushalt.
     * Nur der `owner` darf das (sonst `403 forbidden`); den Owner selbst zu entfernen ist `400 invalid_input`,
     * ein Nicht-Mitglied `404 not_found`.
     */
    fun removeMember(ownerId: String, householdId: String, memberId: String) {
        db.tx { c ->
            if (roleOf(c, ownerId, householdId) != ROLE_OWNER) {
                throw ApiException(HttpStatusCode.Forbidden, ErrorCode.FORBIDDEN)
            }
            if (memberId == ownerId) throw ApiException(HttpStatusCode.BadRequest, ErrorCode.INVALID_INPUT)
            val removed = c.prepareStatement("DELETE FROM membership WHERE user_id = ? AND household_id = ?").use { st ->
                st.setString(1, memberId)
                st.setString(2, householdId)
                st.executeUpdate()
            }
            if (removed == 0) throw ApiException(HttpStatusCode.NotFound, ErrorCode.NOT_FOUND)
            c.prepareStatement("DELETE FROM invite WHERE created_by = ? AND household_id = ? AND used_at IS NULL").use { st ->
                st.setString(1, memberId)
                st.setString(2, householdId)
                st.executeUpdate()
            }
            c.prepareStatement(
                "UPDATE device SET revoked_at = ? WHERE user_id = ? AND household_id = ? AND revoked_at IS NULL",
            ).use { st ->
                st.setLong(1, clock.millis())
                st.setString(2, memberId)
                st.setString(3, householdId)
                st.executeUpdate()
            }
        }
    }

    private fun roleOf(c: Connection, userId: String, householdId: String): String? =
        c.prepareStatement("SELECT role FROM membership WHERE user_id = ? AND household_id = ?").use { st ->
            st.setString(1, userId)
            st.setString(2, householdId)
            st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    private fun insertMembership(c: Connection, userId: String, householdId: String, role: String) {
        c.prepareStatement(
            "INSERT INTO membership (user_id, household_id, role, created_at) VALUES (?, ?, ?, ?)",
        ).use { st ->
            st.setString(1, userId)
            st.setString(2, householdId)
            st.setString(3, role)
            st.setLong(4, clock.millis())
            st.executeUpdate()
        }
    }

    private fun bindDevice(c: Connection, deviceId: String, userId: String, householdId: String) {
        c.prepareStatement("UPDATE device SET household_id = ? WHERE id = ? AND user_id = ?").use { st ->
            st.setString(1, householdId)
            st.setString(2, deviceId)
            st.setString(3, userId)
            st.executeUpdate()
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

    private class InviteRow(val id: String, val householdId: String?, val expiresAt: Long, val used: Boolean, val createdBy: String)

    private companion object {
        val USERNAME = Regex("^[A-Za-z0-9._-]{3,32}$")
        const val LAST_SEEN_INTERVAL_MS = 60_000L
        const val MAX_HOUSEHOLD_NAME = 100
        const val ROLE_OWNER = "owner"
        const val ROLE_MEMBER = "member"
        val INVITE_VALIDITY: Duration = Duration.ofDays(7)
    }
}

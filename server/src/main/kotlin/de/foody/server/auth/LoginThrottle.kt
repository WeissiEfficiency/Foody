package de.foody.server.auth

import de.foody.server.ApiException
import de.foody.sync.protocol.ErrorCode
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Drosselt Anmeldungen: 5 Fehlversuche je (IP, Benutzername klein) sperren für 15 Minuten.
 * Der Zustand liegt nur im Speicher; abgelaufene Einträge werden bei jeder Prüfung entfernt.
 */
class LoginThrottle(private val clock: Clock) {
    private class Entry(var failures: Int, var windowStart: Instant, var lockedUntil: Instant?)

    private val entries = HashMap<String, Entry>()

    private fun key(ip: String, username: String) = "$ip|${username.lowercase()}"

    /**
     * Reserviert atomar einen Versuch: Der Versuch wird sofort als Fehlversuch gezählt, damit parallele
     * Anfragen das Limit nicht überschreiten. Wirft `429 throttled`, solange die Sperre läuft.
     * Ein erfolgreicher Login macht die Reservierung über [success] rückgängig.
     */
    @Synchronized
    fun check(ip: String, username: String) {
        val now = clock.instant()
        entries.values.removeIf { isExpired(it, now) }
        val entry = entries.getOrPut(key(ip, username)) { Entry(0, now, null) }
        val lock = entry.lockedUntil
        if (lock != null && now.isBefore(lock)) {
            throw ApiException(HttpStatusCode.TooManyRequests, ErrorCode.THROTTLED)
        }
        entry.failures++
        if (entry.failures >= MAX_FAILURES) entry.lockedUntil = now.plus(LOCK)
    }

    /** Der Fehlversuch ist bereits in [check] vorgemerkt; hier ist nichts mehr zu tun. */
    @Synchronized
    @Suppress("UNUSED_PARAMETER")
    fun failure(ip: String, username: String) = Unit

    @Synchronized
    fun success(ip: String, username: String) {
        entries.remove(key(ip, username))
    }

    /** Ein Eintrag ist erledigt, wenn weder Sperre noch Fehlerfenster laufen. */
    private fun isExpired(entry: Entry, now: Instant): Boolean {
        val lock = entry.lockedUntil
        if (lock != null) return !now.isBefore(lock)
        return !now.isBefore(entry.windowStart.plus(LOCK))
    }

    private companion object {
        const val MAX_FAILURES = 5
        val LOCK: Duration = Duration.ofMinutes(15)
    }
}

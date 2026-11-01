package de.foody.server.auth

import de.foody.api.protocol.ErrorCode
import de.foody.server.ApiException
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Drosselt Anmeldungen: 5 Fehlversuche je (IP, Benutzername klein) sperren für 15 Minuten, ebenso 20 Fehlversuche
 * einer IP über alle Namen (N4). Der Zustand liegt nur im Speicher; abgelaufene Einträge werden bei jeder Prüfung
 * entfernt. Jede neue Sperre steht im Log.
 */
class LoginThrottle(private val clock: Clock) {
    private class Entry(var failures: Int, var windowStart: Instant, var lockedUntil: Instant?)

    private val entries = HashMap<String, Entry>()
    private val log = LoggerFactory.getLogger(LoginThrottle::class.java)

    private fun key(ip: String, username: String) = "$ip|${username.lowercase()}"

    /** Schlüssel der IP über alle Namen; Benutzernamen sind nie leer, deshalb keine Verwechslung mit [key]. */
    private fun ipKey(ip: String) = "$ip|"

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
        val ipEntry = entries.getOrPut(ipKey(ip)) { Entry(0, now, null) }
        if (gesperrt(entry, now) || gesperrt(ipEntry, now)) {
            throw ApiException(HttpStatusCode.TooManyRequests, ErrorCode.THROTTLED)
        }
        entry.failures++
        if (entry.failures >= MAX_FAILURES) {
            entry.lockedUntil = now.plus(LOCK)
            log.warn("Anmeldung für {} von {} nach {} Fehlversuchen 15 Minuten gesperrt", username, ip, entry.failures)
        }
        ipEntry.failures++
        if (ipEntry.failures >= MAX_FAILURES_IP) {
            ipEntry.lockedUntil = now.plus(LOCK)
            log.warn("Anmeldungen von {} nach {} Fehlversuchen 15 Minuten gesperrt", ip, ipEntry.failures)
        }
    }

    private fun gesperrt(entry: Entry, now: Instant): Boolean = entry.lockedUntil?.let { now.isBefore(it) } == true

    @Synchronized
    fun success(ip: String, username: String) {
        entries.remove(key(ip, username))
        // Die Reservierung aus [check] auch für die IP zurücknehmen
        val ipEntry = entries[ipKey(ip)] ?: return
        ipEntry.failures--
        if (ipEntry.failures <= 0 && ipEntry.lockedUntil == null) entries.remove(ipKey(ip))
    }

    /** Anzahl gemerkter Schlüssel (für Tests). */
    @Synchronized
    fun size(): Int = entries.size

    /** Ein Eintrag ist erledigt, wenn weder Sperre noch Fehlerfenster laufen. */
    private fun isExpired(entry: Entry, now: Instant): Boolean {
        val lock = entry.lockedUntil
        if (lock != null) return !now.isBefore(lock)
        return !now.isBefore(entry.windowStart.plus(LOCK))
    }

    private companion object {
        const val MAX_FAILURES = 5
        const val MAX_FAILURES_IP = 20
        val LOCK: Duration = Duration.ofMinutes(15)
    }
}

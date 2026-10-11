package de.foody.server.web

import de.foody.server.ServerDeps
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import org.slf4j.LoggerFactory

private const val STANDARD_ZONE = "Europe/Berlin"

/**
 * Zeitzone für „heute“ in der Web-Oberfläche: `FOODY_ZEITZONE` (z. B. `Europe/Vienna`), sonst Europe/Berlin. Ein ungültiger
 * Wert führt zu einer Warnung und Europe/Berlin – kein Abbruch des Servers wegen einer Anzeigefrage.
 */
internal fun standardZone(wert: String? = System.getenv("FOODY_ZEITZONE")): ZoneId {
    if (wert.isNullOrBlank()) return ZoneId.of(STANDARD_ZONE)
    return try {
        ZoneId.of(wert.trim())
    } catch (_: DateTimeException) {
        LoggerFactory.getLogger("de.foody.server.web").warn("FOODY_ZEITZONE ungültig, verwende {}", STANDARD_ZONE)
        ZoneId.of(STANDARD_ZONE)
    }
}

/** „Heute“ in [zone] – die Server-Uhr läuft in UTC, ein Haushalt lebt in seiner Ortszeit. */
internal fun ServerDeps.heute(zone: ZoneId): LocalDate = clock.instant().atZone(zone).toLocalDate()

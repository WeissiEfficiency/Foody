package de.foody.core.sync

import java.net.URI
import java.net.URISyntaxException

/** Prüft und vereinheitlicht die vom Nutzer eingegebene Server-Adresse. */
object ServerAdresse {
    private val LOKALE_HOSTS = setOf("10.0.2.2", "localhost")

    /**
     * Liefert die Adresse ohne abschließenden `/` oder `null`, wenn sie nicht erlaubt ist. Erlaubt ist `https://`
     * (auch mit Pfad für einen Reverse-Proxy-Unterpfad); mit [debug] (Debug-Build) zusätzlich `http://` für
     * `10.0.2.2` (Emulator) und `localhost`. Zugangsdaten, Query und Fragment in der Adresse werden abgelehnt.
     */
    fun pruefen(eingabe: String, debug: Boolean): String? {
        val text = eingabe.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val uri = try {
            URI(text)
        } catch (_: URISyntaxException) {
            return null
        }
        val schema = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        when (schema) {
            "https" -> Unit
            "http" -> if (!debug || host !in LOKALE_HOSTS) return null
            else -> return null
        }
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val pfad = uri.rawPath.orEmpty().trimEnd('/')
        return "$schema://$host$port$pfad"
    }
}

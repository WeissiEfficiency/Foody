package de.foody.app.sync

import java.net.URI
import java.net.URISyntaxException

/** Prüft und vereinheitlicht die vom Nutzer eingegebene Server-Adresse. */
object ServerUrl {
    private val LOCAL_HOSTS = setOf("10.0.2.2", "localhost")

    /**
     * Liefert die Adresse ohne abschließenden `/` oder `null`, wenn sie nicht erlaubt ist. Erlaubt ist `https://`
     * (ein Pfad für einen Reverse-Proxy-Unterpfad ist möglich); mit [allowLocalHttp] (Debug-Build) zusätzlich
     * `http://` für `10.0.2.2` und `localhost`. Zugangsdaten, Query und Fragment in der Adresse werden abgelehnt.
     */
    fun normalize(input: String, allowLocalHttp: Boolean): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val uri = try {
            URI(text)
        } catch (_: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        when (scheme) {
            "https" -> Unit
            "http" -> if (!allowLocalHttp || host !in LOCAL_HOSTS) return null
            else -> return null
        }
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val path = uri.rawPath.orEmpty().trimEnd('/')
        return "$scheme://$host$port$path"
    }
}

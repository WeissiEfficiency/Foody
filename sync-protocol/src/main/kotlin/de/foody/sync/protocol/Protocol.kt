package de.foody.sync.protocol

import kotlinx.serialization.json.Json

/** Konstanten und gemeinsame JSON-Konfiguration des Sync-Protokolls (Server und App). */
object Protocol {
    const val VERSION = 1
    const val MIN_VERSION = 1

    /** Header, den jede `/api/v1`-Anfrage mit der Protokollversion des Clients trägt. */
    const val HEADER = "X-Foody-Protocol"
    const val MAX_PUSH_RECORDS = 500
    const val MAX_PUSH_BYTES = 5_242_880L
    const val MAX_PULL_LIMIT = 500

    /** Unbekannte Felder werden ignoriert (Vorwärtskompatibilität); `null`-Felder entfallen im JSON. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
}

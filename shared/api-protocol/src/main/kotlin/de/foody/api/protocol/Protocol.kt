package de.foody.api.protocol

import kotlinx.serialization.json.Json

/** Konstanten und gemeinsame JSON-Konfiguration des API-Protokolls (Server und App). */
object Protocol {
    /**
     * 3: Konto statt Haushalt, ohne Vorrat (ADR 0011).
     */
    const val VERSION = 3
    const val MIN_VERSION = 3

    /** Header, den jede `/api/v3`-Anfrage mit der Protokollversion des Clients trägt. */
    const val HEADER = "X-Foody-Protocol"
    const val MAX_PUSH_RECORDS = 500
    const val MAX_PUSH_BYTES = 5_242_880L
    const val MAX_PULL_LIMIT = 500

    /** Größte erlaubte Fotodatei (Upload und Download). */
    const val MAX_PHOTO_BYTES = 10_485_760L

    /** Unbekannte Felder werden ignoriert (Vorwärtskompatibilität); `null`-Felder entfallen im JSON. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
}

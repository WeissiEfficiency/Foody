package de.foody.feature.heute

/**
 * Tipp auf ein Homescreen-Widget: womit das Eintragen-Blatt in „Heute“ gleich beginnt. [id] ist je Tipp neu, damit
 * auch der zweite Tipp wieder startet. Als Text ([alsText]) liegt die Anfrage im SavedStateHandle der Navigation.
 */
data class Schnellstart(val art: Art, val id: String) {
    enum class Art {
        /** Widget „Scannen“: „Frei“ mit dem Strichcode-Scan. */
        SCANNEN,

        /** Widget „KI-Foto“: „Frei“ mit der Kamera für die Fotoerkennung. */
        FOTO,

        /** Widget „Schnell eintragen“: das Blatt wie über „+“ (Suchen, Scannen, Foto zur Wahl). */
        EINTRAGEN,
    }

    fun alsText(): String = "${art.name}:$id"

    companion object {
        /** Umkehrung von [alsText]; Unbekanntes → `null`. */
        fun ausText(text: String?): Schnellstart? {
            val teile = text?.split(':', limit = 2)?.takeIf { it.size == 2 } ?: return null
            return Art.entries.firstOrNull { it.name == teile[0] }?.let { Schnellstart(it, teile[1]) }
        }
    }
}

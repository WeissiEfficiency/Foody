package de.foody.katalog

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/** Die Seiten des Katalogs, je eine Komponentengruppe. */
enum class KatalogSeite(val titel: String) {
    FARBEN_SCHRIFT("Farben und Schrift"),
    GLAS("Glas"),
    REZEPTKARTE("Rezeptkarte"),
    KACHEL("Kachel"),
    NAEHRWERTE("Nährwertbalken"),
    SYNC("Sync-Status"),
    LEER("Leere Zustände"),
    GRUNDBAUSTEINE("Grundbausteine"),
}

enum class Darstellung { SYSTEM, HELL, DUNKEL }

/** Umschalter des Katalogs: Darstellung, Schrift 200 %, Glas ohne Unschärfe. */
data class KatalogEinstellung(
    val darstellung: Darstellung = Darstellung.SYSTEM,
    val schrift200: Boolean = false,
    val ohneUnschaerfe: Boolean = false,
)

const val INHALT_TAG = "katalog-inhalt"
const val SEITENLISTE_TAG = "katalog-seitenliste"
const val SEITENREIHE_TAG = "katalog-seitenreihe"

/** Aktive Darstellung („hell“/„dunkel“) am Inhalt – für Tests. */
val DarstellungKey = SemanticsPropertyKey<String>("Darstellung")
var SemanticsPropertyReceiver.darstellung by DarstellungKey

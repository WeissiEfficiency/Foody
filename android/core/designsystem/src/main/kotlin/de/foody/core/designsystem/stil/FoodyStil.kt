package de.foody.core.designsystem.stil

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Wählbarer Stil (Einstellungen › Darstellung). [MILCHGLAS] ist der Standard: die App wie bisher, mit Farbwelt und
 * Modus. Die übrigen Stile kommen aus dem Design-Canvas (Q bis V, `ToDoS/stile-plan.md`). Jeder hat einen festen
 * Grund und vier Akzente; [akzente] ist bei Milchglas leer, dort gilt die Farbwelt.
 */
enum class FoodyStil(val anzeigename: String, val akzente: List<StilAkzent>) {
    MILCHGLAS("Milchglas", emptyList()),
    SAGE(
        "Düstere Sage",
        listOf(
            StilAkzent("GOLD", "Gold", Color(0xFFC8A45C)),
            StilAkzent("SILBER", "Silber", Color(0xFFB8C2CC)),
            StilAkzent("GLUT", "Glut", Color(0xFFC46A4A)),
            StilAkzent("MOOS", "Moos", Color(0xFF8FB08A)),
        ),
    ),
    ANIME(
        "Anime",
        listOf(
            StilAkzent("ROSA", "Rosa", Color(0xFFFF7AAE)),
            StilAkzent("TUERKIS", "Türkis", Color(0xFF3EC6E0)),
            StilAkzent("ORANGE", "Orange", Color(0xFFFFB23E)),
            StilAkzent("LILA", "Lila", Color(0xFFA98BFF)),
        ),
    ),
    EDO(
        "Edo",
        listOf(
            StilAkzent("ZINNOBER", "Zinnober", Color(0xFFB8322A)),
            StilAkzent("INDIGO", "Indigo", Color(0xFF24375A)),
            StilAkzent("MOOS", "Moos", Color(0xFF6E7F45)),
            StilAkzent("OCKER", "Ocker", Color(0xFF8A5A1E)),
        ),
    ),
    BAYRISCH(
        "Bayrisch",
        listOf(
            StilAkzent("BLAU", "Blau", Color(0xFF1C6CB0)),
            StilAkzent("GRUEN", "Grün", Color(0xFF2E5E3A)),
            StilAkzent("ROT", "Rot", Color(0xFF9A2B22)),
            StilAkzent("BRAUN", "Braun", Color(0xFF2B1E14)),
        ),
    ),
    GRAUEN(
        "Kosmisches Grauen",
        listOf(
            StilAkzent("GLUT", "Glut", Color(0xFF8FD1A0)),
            StilAkzent("ROSE", "Rosé", Color(0xFFE58C98)),
            StilAkzent("BLAU", "Blau", Color(0xFF9DB6E8)),
            StilAkzent("LILA", "Lila", Color(0xFFC9A6DE)),
        ),
    ),
    KREUZER(
        "Sternenkreuzer",
        listOf(
            StilAkzent("AMBER", "Amber", Color(0xFFFFB000)),
            StilAkzent("ROT", "Rot", Color(0xFFFF5A4A)),
            StilAkzent("GRUEN", "Grün", Color(0xFF7CF29A)),
            StilAkzent("WEISS", "Weiß", Color(0xFFE9EEF2)),
        ),
    );

    /** Fester Grund des Stils: Q und V dunkel, R, S und T hell, U je nach [variante]. Milchglas folgt dem Modus. */
    fun dunkel(variante: StilVariante, modusDunkel: Boolean): Boolean = when (this) {
        MILCHGLAS -> modusDunkel
        SAGE, KREUZER -> true
        ANIME, EDO, BAYRISCH -> false
        GRAUEN -> variante == StilVariante.IMMERSIV
    }

    /** Akzent zum gespeicherten Namen; unbekannt oder fehlend = der erste (Standard des Stils), bei Milchglas `null`. */
    fun akzent(name: String?): StilAkzent? = akzente.firstOrNull { it.name == name } ?: akzente.firstOrNull()

    companion object {
        val Standard = MILCHGLAS

        /** Gespeicherter Name (`SAGE` …) zurück zum Stil; unbekannt oder fehlend = [Standard]. */
        fun ausName(name: String?): FoodyStil = entries.firstOrNull { it.name == name } ?: Standard
    }
}

/** Immersiv: volle Inszenierung (Muster, Verlauf, kräftige Kanten). Ruhig: Farben und Schrift des Stils, schlicht. */
enum class StilVariante(val anzeigename: String) {
    IMMERSIV("Immersiv"),
    RUHIG("Ruhig");

    companion object {
        val Standard = IMMERSIV

        fun ausName(name: String?): StilVariante = entries.firstOrNull { it.name == name } ?: Standard
    }
}

/** Eine der vier Akzentfarben eines Stils, wie im Entwurf; [name] wird gespeichert. */
@Immutable
data class StilAkzent(val name: String, val anzeigename: String, val farbe: Color)

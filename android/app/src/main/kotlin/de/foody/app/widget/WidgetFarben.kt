package de.foody.app.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.HoloBlau
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.stil.stilGrundlage

/**
 * Rohfarben eines Stils als ARGB, aus den Entwürfen im Designsystem ([stilGrundlage]): Ändert sich dort eine Farbe, ziehen
 * die Widgets mit. Die Widgets zeigen immer die Farben von Immersiv (Grauen also die dunkle Tafel); [immersiv] = false
 * nur für das Pergament des Grauen-Zettels. Was vom Akzent abhängt, kommt nicht von hier.
 */
internal class Entwurf(stil: FoodyStil, immersiv: Boolean = true) {
    private val g = stilGrundlage(stil, immersiv, akzent = Color.Transparent)
    val grund = g.grund.toArgb()
    val text = g.text.toArgb()
    val neben = g.neben.toArgb()
    val glas = g.glas.toArgb()
    val linie = g.linie.toArgb()
    val kante = g.kante.toArgb()
    val oben = g.oben.toArgb()
}

/** Die Entwürfe der Stile, einmal gelesen. */
internal object Entwuerfe {
    val sage = Entwurf(FoodyStil.SAGE)
    val anime = Entwurf(FoodyStil.ANIME)
    val edo = Entwurf(FoodyStil.EDO)
    val bayrisch = Entwurf(FoodyStil.BAYRISCH)
    val grauen = Entwurf(FoodyStil.GRAUEN)
    val pergament = Entwurf(FoodyStil.GRAUEN, immersiv = false)
    val kreuzer = Entwurf(FoodyStil.KREUZER)
}

/** Tinte der Anime-Entwürfe: Schrift, Rand und Schatten. */
internal val TINTE = Entwuerfe.anime.text

/** Holo-Blau des Sternenkreuzers (Ring, Tank, geplante Tage). */
internal val HOLO = HoloBlau.toArgb()

/** Schrift auf Amber (Kreuzer). */
internal val AUF_AMBER = Kreuzer.aufAmber.toArgb()

/** Türkis des Scan-Knopfs (Anime), der Akzent „Türkis“. */
internal val ANIME_TUERKIS = requireNotNull(FoodyStil.ANIME.akzent("TUERKIS")).farbe.toArgb()

// Nur in den Widget-Entwürfen: Gelb der Anime-Knöpfe, die helle Anime-Spur (Ring, vergangene Tage) und der
// vergilbte Zettel (Grauen, Einkauf)
internal const val ANIME_GELB = 0xFFFFE45C.toInt()
internal const val ANIME_SPUR = 0xFFF2EEF4.toInt()
internal const val ZETTEL = 0xFFDCCB98.toInt()
internal const val ZETTEL_RAND = 0xFF8C7A4E.toInt()

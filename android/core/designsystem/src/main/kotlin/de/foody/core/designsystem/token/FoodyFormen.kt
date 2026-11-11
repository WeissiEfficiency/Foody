package de.foody.core.designsystem.token

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/**
 * Ecken eines Stils: klein für Chips und Felder, mittel für Kacheln und Bilder, groß für Karten und Glasflächen,
 * [leiste] für die schwebende Navigation, [knopf] für Knöpfe und Chips (bei Milchglas Pillen). Gelesen wird über
 * `FoodyTheme.formen`; Milchglas nutzt [Rund].
 */
@Immutable
data class FoodyFormen(
    val klein: CornerBasedShape,
    val mittel: CornerBasedShape,
    val gross: CornerBasedShape,
    val leiste: CornerBasedShape,
    val knopf: CornerBasedShape = CircleShape,
) {
    companion object {
        /** Milchglas: weich und rund. */
        val Rund = FoodyFormen(
            klein = RoundedCornerShape(14.dp),
            mittel = RoundedCornerShape(23.dp),
            gross = RoundedCornerShape(32.dp),
            leiste = RoundedCornerShape(39.dp),
        )
    }
}

/** Abstände im 4-dp-Raster. */
object FoodyAbstaende {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
}

/** Breiten für große Bildschirme (Tablet). */
object FoodyBreiten {
    /** Ab dieser Fensterbreite zwei Spalten und die Navigation links (Material „expanded“). */
    val zweispaltig = 840.dp

    /** Lesebreite: Eine Inhaltsspalte wird nicht breiter und steht auf dem Tablet mittig. */
    val inhalt = 720.dp

    /** Höchstbreite der Seiten mit zwei Bereichen nebeneinander (Woche, Einkauf). */
    val zweiBereiche = 1100.dp
}

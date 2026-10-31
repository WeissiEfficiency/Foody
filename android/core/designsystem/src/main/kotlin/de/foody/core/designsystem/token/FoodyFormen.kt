package de.foody.core.designsystem.token

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Ecken: klein für Chips und Felder, mittel für Kacheln und Bilder, groß für Karten und Glasflächen. */
object FoodyFormen {
    val klein = RoundedCornerShape(12.dp)
    val mittel = RoundedCornerShape(20.dp)
    val gross = RoundedCornerShape(28.dp)
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

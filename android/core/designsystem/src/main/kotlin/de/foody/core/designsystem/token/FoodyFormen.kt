package de.foody.core.designsystem.token

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Ecken „Rund“: klein für Chips und Felder, mittel für Kacheln und Bilder, groß für Karten und Glasflächen, [leiste]
 * für die schwebende Navigation. Knöpfe und Chips sind Pillen (`CircleShape`).
 */
object FoodyFormen {
    val klein = RoundedCornerShape(14.dp)
    val mittel = RoundedCornerShape(23.dp)
    val gross = RoundedCornerShape(32.dp)
    val leiste = RoundedCornerShape(39.dp)
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

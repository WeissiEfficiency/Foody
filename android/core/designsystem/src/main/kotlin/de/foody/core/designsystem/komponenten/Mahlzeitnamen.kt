package de.foody.core.designsystem.komponenten

import de.foody.domain.Mahlzeit

/** Anzeigename einer Mahlzeit („Mittagessen“). Gespeichert wird der Enum-Name, die Texte stehen nur hier. */
fun Mahlzeit.anzeigename(): String = when (this) {
    Mahlzeit.FRUEHSTUECK -> "Frühstück"
    Mahlzeit.MITTAGESSEN -> "Mittagessen"
    Mahlzeit.SNACK -> "Snack"
    Mahlzeit.ABENDESSEN -> "Abendessen"
}

/** Kurzer Name für enge Stellen („Mittag“, „Abend“). */
fun Mahlzeit.kurzname(): String = when (this) {
    Mahlzeit.FRUEHSTUECK -> "Frühstück"
    Mahlzeit.MITTAGESSEN -> "Mittag"
    Mahlzeit.SNACK -> "Snack"
    Mahlzeit.ABENDESSEN -> "Abend"
}

/** Anzeigename eines gespeicherten Plan-Slots; alte Freitexte bleiben, wie sie sind. */
fun slotName(slot: String): String = Mahlzeit.ausText(slot)?.anzeigename() ?: slot

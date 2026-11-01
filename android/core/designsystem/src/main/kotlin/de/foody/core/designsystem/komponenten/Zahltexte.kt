package de.foody.core.designsystem.komponenten

import java.util.Locale

/** Ganzzahl mit deutschem Tausenderpunkt (1240 → „1.240“). */
fun ganzzahlDeutsch(wert: Int): String = String.format(Locale.GERMAN, "%,d", wert)

/** „1.234 kcal“, unvollständig „≥ 717 kcal“, ohne Werte „– kcal“ – überall gleich geschrieben. */
fun kcalText(kcal: Int?, vollstaendig: Boolean = true): String = when {
    kcal == null -> "– kcal"
    !vollstaendig -> "≥ ${ganzzahlDeutsch(kcal)} kcal"
    else -> "${ganzzahlDeutsch(kcal)} kcal"
}

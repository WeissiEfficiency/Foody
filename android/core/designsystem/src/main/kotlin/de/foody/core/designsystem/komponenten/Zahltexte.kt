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

/** kcal-Zahl ohne Einheit; unvollständig „≥ 860“, denn fehlende Werte machen die Summe nur größer (Regel 24). */
fun kcalZahl(kcal: Int, vollstaendig: Boolean = true): String = (if (vollstaendig) "" else "≥ ") + ganzzahlDeutsch(kcal)

/** Rest bis zum Ziel; bei unvollständiger Summe höchstens so viel („≤ 1.140“). */
fun kcalRest(rest: Int, vollstaendig: Boolean = true): String = (if (vollstaendig) "" else "≤ ") + ganzzahlDeutsch(rest)

/** Tagesstand für TalkBack: „860 von 2.000 kcal“, ohne Ziel „860 kcal“, unvollständig mit „≥“. */
fun kcalStand(kcal: Int, ziel: Int?, vollstaendig: Boolean = true): String =
    if (ziel != null && ziel > 0) "${kcalZahl(kcal, vollstaendig)} von ${ganzzahlDeutsch(ziel)} kcal" else kcalText(kcal, vollstaendig)

/** „Eiweiß 42 g, Kohlenhydrate 96 g, Fett 31 g“ – so liest TalkBack die Nährwerte überall; [trenner] für sichtbaren Text. */
fun makroSatz(eiweiss: Int, kohlenhydrate: Int, fett: Int, trenner: String = ", "): String =
    "Eiweiß $eiweiss g${trenner}Kohlenhydrate $kohlenhydrate g${trenner}Fett $fett g"

/** „1 Portion“, „4 Portionen“. */
fun portionenText(n: Int): String = if (n == 1) "1 Portion" else "$n Portionen"

/** Römische Zahl für Schritte und Kapitel (I … XXXIX), ab 40 und unter 1 die arabische. */
fun roemisch(n: Int): String {
    if (n !in 1..39) return n.toString()
    return "X".repeat(n / 10) + listOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX")[n % 10]
}

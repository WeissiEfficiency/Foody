package de.foody.feature.woche

import de.foody.domain.Mahlzeit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

/** Montag der Woche, in der [datum] liegt. */
fun montagVon(datum: LocalDate): LocalDate = datum.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** „KW 44 · 26.10.–1.11.“ – Kalenderwoche nach ISO 8601. */
fun wochenTitel(montag: LocalDate): String {
    val sonntag = montag.plusDays(6)
    val kw = montag.get(WeekFields.ISO.weekOfWeekBasedYear())
    return "KW $kw · ${montag.dayOfMonth}.${montag.monthValue}.–${sonntag.dayOfMonth}.${sonntag.monthValue}."
}

/** Anzeigename einer Mahlzeit; eigene Namen unverändert. */
fun mahlzeitAnzeige(text: String): String = Mahlzeit.ausText(text)?.let(::mahlzeitAnzeige) ?: text

fun mahlzeitAnzeige(m: Mahlzeit): String = when (m) {
    Mahlzeit.FRUEHSTUECK -> "Frühstück"
    Mahlzeit.MITTAGESSEN -> "Mittagessen"
    Mahlzeit.SNACK -> "Snack"
    Mahlzeit.ABENDESSEN -> "Abendessen"
}

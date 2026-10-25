package de.foody.core.data.woche

import de.foody.domain.DayNutrition
import de.foody.domain.Durchschnitt
import de.foody.domain.Mahlzeit
import java.time.LocalDate

/** Ein Eintrag im Wochenplan; [mahlzeit] wie gespeichert (Enum-Name oder eigener Name). */
data class PlanEintrag(
    val id: String,
    val datum: LocalDate,
    val mahlzeit: String,
    val rezeptId: String,
    val rezeptName: String,
    val bild: String?,
    val kcalJePortion: Int?,
    val portionen: Int,
    val gekocht: Boolean,
)

/** Ein Tag der Woche; [naehrwerte] je Person, `null` ohne Einträge (nicht zu verwechseln mit `PlanTag` aus `:domain`). */
data class WochenTag(val datum: LocalDate, val eintraege: List<PlanEintrag>, val naehrwerte: DayNutrition?)

/** Die Woche ab [montag]; [durchschnitt] nach `PlanDurchschnitt`, `null`, wenn kein Tag zählt. */
data class WochenAnsicht(val montag: LocalDate, val tage: List<WochenTag>, val durchschnitt: Durchschnitt?)

/** Frühstück 0, Mittagessen 1, Snack 2, Abendessen 3, eigene Namen 4. */
fun mahlzeitReihenfolge(text: String): Int = when (Mahlzeit.ausText(text)) {
    Mahlzeit.FRUEHSTUECK -> 0
    Mahlzeit.MITTAGESSEN -> 1
    Mahlzeit.SNACK -> 2
    Mahlzeit.ABENDESSEN -> 3
    null -> 4
}

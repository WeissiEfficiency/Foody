package de.foody.domain

import java.math.BigDecimal
import java.math.RoundingMode

data class PlanTag(val slotTypes: List<String>, val naehrwerte: DayNutrition)

data class Durchschnitt(val kcal: Int, val tage: Int, val vollstaendig: Boolean)

/**
 * „Ø kcal pro Tag“ im Planer: Gezählt werden nur Tage mit Abendessen und mindestens einer weiteren Mahlzeit –
 * ein Tag mit nur einem Gericht drückte sonst den Schnitt. `null`, wenn kein Tag zählt.
 */
object PlanDurchschnitt {
    fun kcal(tage: List<PlanTag>): Durchschnitt? {
        val gezaehlt = tage.filter { t ->
            t.slotTypes.size >= 2 && t.slotTypes.any { Mahlzeit.ausText(it) == Mahlzeit.ABENDESSEN }
        }
        if (gezaehlt.isEmpty()) return null
        val schnitt = BigDecimal(gezaehlt.sumOf { it.naehrwerte.kcal })
            .divide(BigDecimal(gezaehlt.size), 0, RoundingMode.HALF_UP).toInt()
        return Durchschnitt(schnitt, gezaehlt.size, gezaehlt.all { it.naehrwerte.complete })
    }
}

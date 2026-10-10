package de.foody.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlanDurchschnittTest {
    private fun tag(kcal: Int, vararg slots: String, complete: Boolean = true) =
        PlanTag(slots.toList(), DayNutrition(kcal, 0, 0, 0, complete))

    @Test fun nurAbendZaehltNicht() = assertNull(PlanDurchschnitt.kcal(listOf(tag(2000, "ABENDESSEN"))))

    @Test fun abendPlusWeitereZaehlt() = assertEquals(
        Durchschnitt(1500, 2, true),
        PlanDurchschnitt.kcal(
            listOf(
                tag(2000, "ABENDESSEN", "MITTAGESSEN"),
                tag(1000, "Abendessen", "FRUEHSTUECK"),
                tag(500, "MITTAGESSEN"),
            ),
        ),
    )

    @Test fun unvollstaendigMarkiert() = assertEquals(
        false,
        PlanDurchschnitt.kcal(listOf(tag(1800, "ABENDESSEN", "SNACK", complete = false)))?.vollstaendig,
    )
}

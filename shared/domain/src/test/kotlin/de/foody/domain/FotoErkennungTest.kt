package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Vorschlag für einen freien Tagebucheintrag aus der Antwort des Bild-Sprach-Modells (Regel 29). */
class FotoErkennungTest {
    private fun BigDecimal?.klar() = this?.stripTrailingZeros()?.toPlainString()

    @Test fun antwortWieImTrainingVerschachteltImCodezaun() {
        val v = FotoErkennung.vorschlag(
            """```json
            {"dish_name": "Leberkässemmel", "nutritional_summary": {"calories_kcal": 480, "protein_g": 18.0, "carbohydrate_g": 40, "fat_g": 27}, "portion_size": {"Leberkäse": 120, "Semmel": 60}}
            ```""",
        )!!
        assertEquals("Leberkässemmel", v.name)
        assertEquals(480, v.kcal)
        assertEquals(listOf("18", "40", "27"), listOf(v.eiweiss, v.kohlenhydrate, v.fett).map { it.klar() })
    }

    @Test fun andereSchluesselUndZahlenMitEinheit() {
        val v = FotoErkennung.vorschlag(
            """{"dish": "Strawberry Salad", "ingredients": ["Strawberries"], "nutrition": {"calories": 250, "fat": 15g, "protein": 3g, "carbohydrates": 20g}}""",
        )!!
        assertEquals("Strawberry Salad", v.name)
        assertEquals(250, v.kcal)
        assertEquals(listOf("3", "20", "15"), listOf(v.eiweiss, v.kohlenhydrate, v.fett).map { it.klar() })
    }

    @Test fun kcalWirdGerundetGesaettigtesFettZaehltNicht() {
        val v = FotoErkennung.vorschlag("""{"dish_name": "Breze", "calories": 251.6, "saturated_fat_g": 1, "fat_g": 4}""")!!
        assertEquals(252, v.kcal)
        assertEquals("4", v.fett.klar())
    }

    @Test fun unplausibleWerteBleibenLeer() {
        val v = FotoErkennung.vorschlag("""{"dish_name": "Riesenpizza", "calories_kcal": 9000, "protein_g": -3, "fat_g": 400, "carbs_g": 300}""")!!
        assertNull(v.kcal)
        assertNull(v.eiweiss)
        assertNull(v.fett)
        assertEquals("300", v.kohlenhydrate.klar())
    }

    @Test fun ohneNameLeerOhneKcalLeer() {
        assertEquals("", FotoErkennung.vorschlag("""{"calories_kcal": 300}""")!!.name)
        assertNull(FotoErkennung.vorschlag("""{"dish_name": "Suppe"}""")!!.kcal)
    }

    @Test fun ohneNameUndKcalKeinVorschlag() {
        assertNull(FotoErkennung.vorschlag("""{"portion_size": {"Reis": 200}}"""))
        assertNull(FotoErkennung.vorschlag("""{"dish_name": "  ", "calories_kcal": 0}"""))
    }

    @Test fun ohneJsonKeinVorschlag() {
        assertNull(FotoErkennung.vorschlag("Ich sehe einen Teller mit Nudeln."))
    }
}

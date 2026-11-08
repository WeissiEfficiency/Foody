package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    /** Antwortform von Qwen3-VL-4B auf [FotoAnweisung.GERICHT]: Zutaten mit Gramm, Werte oben (ADR 0017, Nachtrag). */
    @Test fun antwortDesVierBModellsMitZutatenliste() {
        val v = FotoErkennung.vorschlag(
            """{
              "dish_name": "Würstchen mit Sauerkraut",
              "ingredients": [{"name": "Bratwurst", "grams": 150}, {"name": "Sauerkraut", "grams": 200}],
              "calories_kcal": 550, "protein_g": 25, "carbs_g": 30, "fat_g": 35
            }""",
        )!!
        assertEquals("Würstchen mit Sauerkraut", v.name)
        assertEquals(550, v.kcal)
        assertEquals(listOf("25", "30", "35"), listOf(v.eiweiss, v.kohlenhydrate, v.fett).map { it.klar() })
    }

    /** Die Anweisung verlangt genau die Felder, die [FotoErkennung] liest, und den Namen auf Deutsch. */
    @Test fun anweisungVerlangtDeutschenNamenUndDieGelesenenFelder() {
        listOf("\"dish_name\"", "\"calories_kcal\"", "\"protein_g\"", "\"carbs_g\"", "\"fat_g\"", "in German").forEach {
            assertTrue(it in FotoAnweisung.GERICHT, it)
        }
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

    @Test fun portionRechnetKcalGanzzahligUndMakrosAufEinZehntel() {
        val v = FotoVorschlag("Leberkässemmel", 485, BigDecimal("18.5"), BigDecimal("40"), null)
        val klein = v.mal(FotoPortion.KLEIN.faktor)
        assertEquals(340, klein.kcal, "485 × 0,7 = 339,5 → kaufmännisch 340")
        assertEquals(listOf("13", "28", null), listOf(klein.eiweiss, klein.kohlenhydrate, klein.fett).map { it.klar() }, "18,5 × 0,7 = 12,95 → 13,0")
        val gross = v.mal(FotoPortion.GROSS.faktor)
        assertEquals(679, gross.kcal)
        assertEquals(listOf("25.9", "56"), listOf(gross.eiweiss, gross.kohlenhydrate).map { it.klar() })
        assertEquals(v, v.mal(FotoPortion.NORMAL.faktor), "normal ändert nichts")
        assertEquals("Leberkässemmel", gross.name)
    }

    @Test fun portionOhneKcalBleibtOhne() {
        assertNull(FotoVorschlag("Salat", null, null, null, null).mal(FotoPortion.GROSS.faktor).kcal)
    }

    @Test fun schluesselIgnoriertGrossKleinUndLeerraum() {
        assertEquals("pizza margherita", FotoErkennung.schluessel("  Pizza   Margherita "))
        assertEquals(FotoErkennung.schluessel("Leberkässemmel"), FotoErkennung.schluessel("LEBERKÄSSEMMEL"))
    }
}

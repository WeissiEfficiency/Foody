package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Vorschlag aus der Antwort des Bild-Sprach-Modells: Rezept, Teller oder Frei (Regel 29). */
class FotoErkennungTest {
    private val kontext = FotoKontext(
        rezepte = listOf(
            FotoKontext.Eintrag("r1", "Linsencurry"),
            FotoKontext.Eintrag("r2", "Spaghetti Bolognese"),
            FotoKontext.Eintrag("r3", "Käsespätzle"),
            FotoKontext.Eintrag("r4", "Käsespätzle"),
        ),
        planRezepte = setOf("r4"),
        zutaten = listOf(
            FotoKontext.Eintrag("z1", "Reis (roh)"),
            FotoKontext.Eintrag("z2", "Hähnchenbrust"),
            FotoKontext.Eintrag("z3", "Brokkoli"),
            FotoKontext.Eintrag("z4", "Zwiebel"),
        ),
    )

    private fun vorschlag(antwort: String) = FotoErkennung.vorschlag(antwort, kontext)

    private fun BigDecimal?.klar() = this?.stripTrailingZeros()?.toPlainString()

    // Rezept

    @Test fun auswahlTrifftRezept() {
        val v = assertIs<FotoVorschlag.Rezept>(vorschlag("""{"choice": "Linsencurry", "portions": 1.5}"""))
        assertEquals("r1", v.rezeptId)
        assertEquals("1.5", v.portionen.klar())
    }

    @Test fun auswahlOhneRuecksichtAufGrossKleinUndLeerzeichen() {
        val v = assertIs<FotoVorschlag.Rezept>(vorschlag("""{"choice": "  spaghetti   bolognese "}"""))
        assertEquals("r2", v.rezeptId)
    }

    @Test fun ohnePortionenEine() {
        val v = assertIs<FotoVorschlag.Rezept>(vorschlag("""{"choice": "Linsencurry"}"""))
        assertEquals("1", v.portionen.klar())
    }

    @Test fun gleicherNameDerAusDemPlanGewinnt() {
        val v = assertIs<FotoVorschlag.Rezept>(vorschlag("""{"choice": "Käsespätzle"}"""))
        assertEquals("r4", v.rezeptId)
    }

    @Test fun portionenAufViertelGerundetUndBegrenzt() {
        assertEquals("1.25", assertIs<FotoVorschlag.Rezept>(vorschlag("""{"choice": "Linsencurry", "portions": 1.3}""")).portionen.klar())
        assertEquals("1", assertIs<FotoVorschlag.Rezept>(vorschlag("""{"choice": "Linsencurry", "portions": 12}""")).portionen.klar())
        assertEquals("1", assertIs<FotoVorschlag.Rezept>(vorschlag("""{"choice": "Linsencurry", "portions": 0}""")).portionen.klar())
        assertEquals("0.25", assertIs<FotoVorschlag.Rezept>(vorschlag("""{"choice": "Linsencurry", "portions": 0.25}""")).portionen.klar())
    }

    @Test fun keinsDavonIstKeinRezept() {
        assertIs<FotoVorschlag.Frei>(vorschlag("""{"choice": "none", "dish_name": "Leberkässemmel", "calories_kcal": 480}"""))
        assertIs<FotoVorschlag.Frei>(vorschlag("""{"choice": "Gulasch", "dish_name": "Gulasch"}"""))
    }

    // Teller

    @Test fun tellerAusKatalogzutaten() {
        val v = assertIs<FotoVorschlag.Teller>(
            vorschlag("""{"dish_name": "Reisteller", "ingredients": [{"name": "Reis", "grams": 200}, {"name": "Hähnchenbrustfilet", "grams": "150 g"}]}"""),
        )
        assertEquals(listOf("z1", "z2"), v.zeilen.map { it.zutatId })
        assertEquals(listOf("Reis (roh)", "Hähnchenbrust"), v.zeilen.map { it.name })
        assertEquals(listOf("200", "150"), v.zeilen.map { it.gramm.klar() })
    }

    @Test fun tellerAusPortionsTabelleMitPlural() {
        val v = assertIs<FotoVorschlag.Teller>(vorschlag("""{"portion_size": {"Zwiebeln": 80, "Broccoli": 120}}"""))
        assertEquals(listOf("z4", "z3"), v.zeilen.map { it.zutatId })
        assertEquals(listOf("80", "120"), v.zeilen.map { it.gramm.klar() })
    }

    @Test fun eineUnbekannteZutatMachtFrei() {
        val v = assertIs<FotoVorschlag.Frei>(
            vorschlag("""{"dish_name": "Reis mit Tofu", "calories_kcal": 550, "ingredients": [{"name": "Reis", "grams": 200}, {"name": "Tofu", "grams": 150}]}"""),
        )
        assertEquals("Reis mit Tofu", v.name)
        assertEquals(550, v.kcal)
    }

    @Test fun eineZutatAlleinIstKeinTeller() {
        assertIs<FotoVorschlag.Frei>(vorschlag("""{"dish_name": "Reis", "ingredients": [{"name": "Reis", "grams": 200}]}"""))
    }

    @Test fun unplausibleGrammBleibenLeer() {
        val v = assertIs<FotoVorschlag.Teller>(
            vorschlag("""{"ingredients": [{"name": "Reis", "grams": 5000}, {"name": "Brokkoli", "grams": 0}, {"name": "Zwiebel"}]}"""),
        )
        assertEquals(listOf(null, null, null), v.zeilen.map { it.gramm })
    }

    // Frei

    @Test fun freiMitVerschachteltenWerten() {
        val v = assertIs<FotoVorschlag.Frei>(
            vorschlag(
                """```json
                {"dish_name": "Leberkässemmel", "nutritional_summary": {"calories_kcal": 480, "protein_g": 18.0, "carbohydrate_g": 40, "fat_g": 27}}
                ```""",
            ),
        )
        assertEquals("Leberkässemmel", v.name)
        assertEquals(480, v.kcal)
        assertEquals(listOf("18", "40", "27"), listOf(v.eiweiss, v.kohlenhydrate, v.fett).map { it.klar() })
    }

    @Test fun freiMitEinheitenWieNutriScan() {
        val v = assertIs<FotoVorschlag.Frei>(
            vorschlag("""{"dish": "Strawberry Salad", "ingredients": ["Strawberries"], "nutrition": {"calories": 250, "fat": 15g, "protein": 3g, "carbohydrates": 20g}}"""),
        )
        assertEquals("Strawberry Salad", v.name)
        assertEquals(250, v.kcal)
        assertEquals(listOf("3", "20", "15"), listOf(v.eiweiss, v.kohlenhydrate, v.fett).map { it.klar() })
    }

    @Test fun unplausibleWerteBleibenLeer() {
        val v = assertIs<FotoVorschlag.Frei>(vorschlag("""{"dish_name": "Riesenpizza", "calories_kcal": 9000, "protein_g": -3, "fat_g": 400, "carbs_g": 300}"""))
        assertNull(v.kcal)
        assertNull(v.eiweiss)
        assertNull(v.fett)
        assertEquals("300", v.kohlenhydrate.klar())
    }

    @Test fun ohneNameLeer() {
        val v = assertIs<FotoVorschlag.Frei>(vorschlag("""{"calories_kcal": 300}"""))
        assertEquals("", v.name)
        assertEquals(300, v.kcal)
    }

    @Test fun ohneJsonKeinVorschlag() {
        assertNull(vorschlag("Ich sehe einen Teller mit Nudeln."))
    }
}

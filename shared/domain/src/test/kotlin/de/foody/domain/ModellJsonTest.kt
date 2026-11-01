package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Nachsichtiger JSON-Leser für Antworten des Bild-Sprach-Modells (Fotoerkennung, Regel 29). */
class ModellJsonTest {
    private fun ModellJson.Objekt.zahl(schluessel: String) = (this[schluessel] as? ModellJson.Zahl)?.wert?.stripTrailingZeros()?.toPlainString()

    private fun ModellJson.Objekt.text(schluessel: String) = (this[schluessel] as? ModellJson.Text)?.wert

    @Test fun reinesJson() {
        val o = ModellJson.lesen("""{"dish_name": "Spaghetti Bolognese", "calories_kcal": 600, "vegan": false, "notiz": null}""")!!
        assertEquals("Spaghetti Bolognese", o.text("dish_name"))
        assertEquals("600", o.zahl("calories_kcal"))
        assertEquals(ModellJson.Falsch, o["vegan"])
        assertEquals(ModellJson.Leer, o["notiz"])
    }

    @Test fun codezaunUndTextDrumherum() {
        val o = ModellJson.lesen("Hier ist das Ergebnis:\n```json\n{\"choice\": \"Linsencurry\", \"portions\": 1.5}\n```\nGuten Appetit!")!!
        assertEquals("Linsencurry", o.text("choice"))
        assertEquals("1.5", o.zahl("portions"))
    }

    @Test fun zahlenMitEinheitWerdenRepariert() {
        val o = ModellJson.lesen("""{"nutrition": {"calories": 250 kcal, "fat": 15g, "protein": 3 g, "carbs": 20,5g}}""")!!
        val n = o["nutrition"] as ModellJson.Objekt
        assertEquals("250", n.zahl("calories"))
        assertEquals("15", n.zahl("fat"))
        assertEquals("3", n.zahl("protein"))
        assertEquals("20.5", n.zahl("carbs"))
    }

    @Test fun kommaVorKlammerUndListen() {
        val o = ModellJson.lesen("""{"ingredients": [{"name": "rice", "grams": 200,}, {"name": "chicken", "grams": "150 g"},],}""")!!
        val zutaten = (o["ingredients"] as ModellJson.Liste).werte.map { it as ModellJson.Objekt }
        assertEquals(listOf("rice", "chicken"), zutaten.map { it.text("name") })
        assertEquals("200", zutaten[0].zahl("grams"))
        assertEquals("150 g", zutaten[1].text("grams"))
    }

    @Test fun schluesselOhneRuecksichtAufGrossKlein() {
        val o = ModellJson.lesen("""{"Dish_Name": "Breze"}""")!!
        assertEquals("Breze", o.text("dish_name"))
    }

    @Test fun textMitEscapesUndKlammern() {
        val o = ModellJson.lesen("""{"name": "Käse \"Gouda\" {jung}", "x": "a\\b\nc"}""")!!
        assertEquals("Käse \"Gouda\" {jung}", o.text("name"))
        assertEquals("a\\b\nc", o.text("x"))
    }

    @Test fun zahlZeigtSichAlsBigDecimal() {
        val o = ModellJson.lesen("""{"a": -1.25e2}""")!!
        assertEquals(BigDecimal("-125"), (o["a"] as ModellJson.Zahl).wert.stripTrailingZeros())
    }

    @Test fun ohneObjektOderKaputtNull() {
        assertNull(ModellJson.lesen("Das ist ein Teller Nudeln."))
        assertNull(ModellJson.lesen("""{"dish_name": "Nudeln", "calories": """))
        assertNull(ModellJson.lesen(""))
    }

    @Test fun nimmtDasErsteLesbareObjekt() {
        val o = ModellJson.lesen("""{kaputt} und dann {"dish_name": "Breze"}""")!!
        assertEquals("Breze", o.text("dish_name"))
    }
}

package de.foody.feature.kochen

import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.rezepte.RezeptVoll
import de.foody.core.data.rezepte.RezeptZeile
import de.foody.domain.Einordnung
import de.foody.domain.MeasureUnit
import de.foody.domain.NutritionResult
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KochSchritteTest {
    private fun zeile(name: String, menge: String, einheit: MeasureUnit, hinweis: String? = null) =
        RezeptZeile("z-$name", name, BigDecimal(menge), einheit, hinweis, false, true)

    private fun voll(
        zeilen: List<RezeptZeile> = listOf(
            zeile("Reis", "400", MeasureUnit.GRAM),
            zeile("Salz", "0", MeasureUnit.PIECE, "1 Prise"),
            zeile("Zwiebel", "2", MeasureUnit.PIECE),
        ),
        schritte: List<String> = listOf("150 g Reis waschen.", "Reis und Zwiebeln 20 Minuten garen.", "Abschmecken."),
    ) = RezeptVoll(
        rezept = RezeptEntity(
            id = "r1", name = "Reistopf", defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = null,
            notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
        ),
        zeilen = zeilen, schritte = schritte, naehrwerte = NutritionResult(emptyMap(), emptyMap(), 2),
        einordnung = Einordnung(emptySet(), emptySet(), true, true),
    )

    @Test
    fun teilmengeAusSchritt() {
        val reis = kochSchritte(voll(), 2)[0].zutaten.single()
        assertEquals("150 g", reis.menge)
        assertEquals("400 g", reis.gesamt)
        assertEquals(0, reis.zeilenIndex)
    }

    @Test
    fun gesamtOhneTeilmenge() {
        val zutaten = kochSchritte(voll(), 2)[1].zutaten
        val reis = zutaten.first { it.name == "Reis" }
        assertEquals("400 g", reis.menge)
        assertNull(reis.gesamt)
        assertEquals(listOf("Reis", "Zwiebel"), zutaten.map { it.name })
    }

    @Test
    fun skaliert() {
        val schritte = kochSchritte(voll(), 4)
        assertEquals("300 g", schritte[0].zutaten.single().menge)
        assertEquals("800 g", schritte[0].zutaten.single().gesamt)
        assertEquals("800 g", bereitlegen(voll(), 4)[0].menge)
    }

    @Test
    fun ohneMenge() {
        val salz = bereitlegen(voll(), 2)[1]
        assertNull(salz.menge)
        assertEquals("1 Prise", salz.hinweis)
        assertEquals("Salz", salz.name)
    }

    @Test
    fun salzUndPfeffer() {
        val v = voll(zeilen = listOf(zeile("Salz und Pfeffer", "0", MeasureUnit.PIECE)), schritte = listOf("Mit Pfeffer würzen."))
        assertEquals(listOf("Salz und Pfeffer"), kochSchritte(v, 2)[0].zutaten.map { it.name })
    }

    @Test
    fun schrittOhneZutat() {
        assertTrue(kochSchritte(voll(), 2)[2].zutaten.isEmpty())
    }

    @Test
    fun timerGrenzen() {
        val v = voll(schritte = listOf("0 Minuten ruhen.", "25 Stunden marinieren.", "20 Minuten garen."))
        val timer = kochSchritte(v, 2).map { it.timer.size }
        assertEquals(listOf(0, 0, 1), timer)
    }
}

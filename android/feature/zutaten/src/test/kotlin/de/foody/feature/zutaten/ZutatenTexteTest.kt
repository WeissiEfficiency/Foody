package de.foody.feature.zutaten

import de.foody.core.data.zutaten.Angleichung
import de.foody.core.data.zutaten.ZutatKurz
import org.junit.Test
import kotlin.test.assertEquals

/** Texte des Zutatenkatalogs, mit Einzahl und Mehrzahl. */
class ZutatenTexteTest {
    private fun kurz(kcal: Int?) = ZutatKurz("x", "X", null, kcal, 0, null, false)

    @Test
    fun verwendung() {
        assertEquals("unbenutzt", verwendungText(0))
        assertEquals("in 1 Rezept", verwendungText(1))
        assertEquals("in 3 Rezepten", verwendungText(3))
    }

    @Test
    fun zaehlzeile() {
        assertEquals("1 Zutat", zaehlzeile(listOf(kurz(100))))
        assertEquals("3 Zutaten · 2 ohne Nährwerte", zaehlzeile(listOf(kurz(100), kurz(null), kurz(null))))
        assertEquals("0 Zutaten", zaehlzeile(emptyList()))
    }

    @Test
    fun kcal() {
        assertEquals("359 kcal", kcalText(359))
        assertEquals("– kcal", kcalText(null))
    }

    @Test
    fun vorschau() {
        val schritte = listOf(
            Angleichung.Umbenennen("a", "Zwiebeln", "Zwiebel"),
            Angleichung.KategorieErgaenzen("a", "Zwiebel", "Obst & Gemüse"),
            Angleichung.Zusammenfuehren("b", "Tomaten", "c", "Tomate"),
        )
        assertEquals(listOf("Zwiebeln → Zwiebel", "Tomaten → Tomate (zusammenführen)"), vorschauZeilen(schritte))
        val viele = (1..10).map { Angleichung.Umbenennen("$it", "A$it", "B$it") }
        val zeilen = vorschauZeilen(viele)
        assertEquals(9, zeilen.size)
        assertEquals("A8 → B8", zeilen[7])
        assertEquals("und 2 weitere", zeilen[8])
    }

    @Test
    fun kategorienUndAngepasst() {
        assertEquals("1 Kategorie ergänzt", kategorienText(1))
        assertEquals("4 Kategorien ergänzt", kategorienText(4))
        assertEquals("1 Zutat angepasst", angepasstText(1))
        assertEquals("12 Zutaten angepasst", angepasstText(12))
    }

    @Test
    fun verwendetIn() {
        assertEquals("Noch in keinem Rezept", verwendetInText(emptyList()))
        assertEquals("Verwendet in: A", verwendetInText(listOf("A")))
        assertEquals("Verwendet in: A, B", verwendetInText(listOf("A", "B")))
        assertEquals("Verwendet in: A, B und 1 weiteres", verwendetInText(listOf("A", "B", "C")))
        assertEquals("Verwendet in: A, B und 3 weitere", verwendetInText(listOf("A", "B", "C", "D", "E")))
    }

    @Test
    fun zusammenfuehrenFrageText() {
        assertEquals("„Zwiebeln“ in „Zwiebel“ zusammenführen? „Zwiebeln“ wird gelöscht.", zusammenfuehrenFrage("Zwiebeln", "Zwiebel", 0))
        assertEquals(
            "„Zwiebeln“ in „Zwiebel“ zusammenführen? 1 Rezept wird umgestellt, „Zwiebeln“ wird gelöscht.",
            zusammenfuehrenFrage("Zwiebeln", "Zwiebel", 1),
        )
        assertEquals(
            "„Zwiebeln“ in „Zwiebel“ zusammenführen? 3 Rezepte werden umgestellt, „Zwiebeln“ wird gelöscht.",
            zusammenfuehrenFrage("Zwiebeln", "Zwiebel", 3),
        )
    }

    @Test
    fun inVerwendung() {
        assertEquals("In 1 Rezept verwendet. Zum Entfernen mit einer anderen Zutat zusammenführen.", inVerwendungText(1))
        assertEquals("In 3 Rezepten verwendet. Zum Entfernen mit einer anderen Zutat zusammenführen.", inVerwendungText(3))
    }
}

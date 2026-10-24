package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZutatZeileTest {
    /** Zeile → (Mengenteil, Namensteil) in der Zweizeilen-Form des Markdown-Imports. */
    private val beispiele = listOf(
        "200 g Mehl, gesiebt" to ("200 g" to "Mehl, gesiebt"),
        "1 Dose Tomaten" to ("1 Dose" to "Tomaten"),
        "2-3 EL Olivenöl" to ("2-3 EL" to "Olivenöl"),
        "1 Prise Salz" to ("1 Prise" to "Salz"),
        "½ Bund Dill, gehackt" to ("½ Bund" to "Dill, gehackt"),
        "2 große Zwiebeln" to ("2 große" to "Zwiebeln"),
        "1,5 l Milch" to ("1,5 l" to "Milch"),
        "3 Eier" to ("3" to "Eier"),
    )

    private fun markdown(menge: String, name: String): ImportedIngredient =
        MarkdownRecipeImporter.parse("# T\n## Zutaten\n$menge\n$name\n").ingredients.single()

    @Test
    fun gleichWieMarkdownZweizeilig() {
        for ((zeile, teile) in beispiele) {
            assertEquals(markdown(teile.first, teile.second), ZutatZeile.lesen(zeile), zeile)
        }
    }

    @Test
    fun ohneMenge() {
        val sp = ZutatZeile.lesen("Salz und Pfeffer")!!
        assertEquals("Salz und Pfeffer", sp.name)
        assertNull(sp.amount)
        assertNull(sp.unit)
        val p = ZutatZeile.lesen("etwas Petersilie")!!
        assertEquals("Petersilie", p.name)
        assertEquals("etwas", p.note)
    }

    @Test
    fun optional() {
        val a = ZutatZeile.lesen("200 g Mehl (optional)")!!
        assertTrue(a.optional)
        assertNull(a.note)
        assertEquals("Mehl", a.name)
        val b = ZutatZeile.lesen("Zucker, optional")!!
        assertTrue(b.optional)
        assertNull(b.note)
    }

    @Test
    fun leer() {
        assertNull(ZutatZeile.lesen("   "))
        assertNull(ZutatZeile.lesen("200 g"))
    }

    @Test
    fun rundweg() {
        for ((zeile, _) in beispiele + listOf("Salz und Pfeffer" to ("" to ""), "etwas Petersilie" to ("" to ""), "200 g Mehl (optional)" to ("" to ""))) {
            val a = ZutatZeile.lesen(zeile)!!
            val text = ZutatZeile.schreiben(a.amount, a.unit, a.name, a.note, a.optional)
            assertEquals(a, ZutatZeile.lesen(text), "$zeile → $text")
        }
    }

    @Test
    fun schreibenLesbar() {
        assertEquals("200 g Mehl, gesiebt", ZutatZeile.schreiben(BigDecimal("200"), MeasureUnit.GRAM, "Mehl", "gesiebt", false))
        assertEquals("2 große Zwiebeln", ZutatZeile.schreiben(BigDecimal(2), MeasureUnit.PIECE, "Zwiebeln", "große", false))
        assertEquals("1 Prise Salz", ZutatZeile.schreiben(null, null, "Salz", "1 Prise", false))
        assertEquals("1,5 l Milch (optional)", ZutatZeile.schreiben(BigDecimal("1.50"), MeasureUnit.LITER, "Milch", null, true))
    }

    @Test
    fun zeilenUeberspringtLeere() {
        assertEquals(2, ZutatZeile.zeilen("200 g Mehl\n\n3 Eier\n").size)
    }
}

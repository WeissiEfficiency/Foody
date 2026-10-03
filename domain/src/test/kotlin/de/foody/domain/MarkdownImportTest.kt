package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarkdownImportTest {
    // Gekürzter Ausschnitt im Format der Rezept-Markdown-Exporte
    private val sample = """
        # Chili con Carne – klassisch von Koch123

        _Quelle: https://example.org/rezepte/1/Chili.html_

        ## Nährwerte

        Als Basis nutzen wir …

        ## Zutaten

        2 große

        Zwiebel(n)

        1 Zehe/n

        Knoblauch

        Öl zum Braten

        1 TL, gehäuft

        Kreuzkümmelpulver

        800 g

        Rinderhackfleisch

        3 Dose/n

        Tomaten, geschälte à ca. 400 g

        Salz und Pfeffer

        ### Zutaten für den Stampf:

        1.5 kg

        Kartoffeln, mehligkochende

        n. B.

        Milch

        ## Zubereitung

        Zwiebeln würfeln und anschwitzen.

        Alles 60 Minuten köcheln lassen.

        - copy Zu einer Sammlung hinzufügen
        - email Per E-Mail teilen

        ## Kommentare
    """.trimIndent()

    private val r = MarkdownRecipeImporter.parse(sample)

    private fun ing(name: String) = r.ingredients.single { it.name == name }

    @Test fun titleAndSource() {
        assertEquals("Chili con Carne – klassisch", r.name)
        assertEquals("https://example.org/rezepte/1/Chili.html", r.sourceUrl)
    }

    @Test fun parsesAmountsAndUnits() {
        assertEquals(0, BigDecimal("800").compareTo(ing("Rinderhackfleisch").amount))
        assertEquals(MeasureUnit.GRAM, ing("Rinderhackfleisch").unit)
        assertEquals(MeasureUnit.CAN, ing("Tomaten").unit)
        assertEquals("geschälte à ca. 400 g", ing("Tomaten").note)
        assertEquals(MeasureUnit.TEASPOON, ing("Kreuzkümmelpulver").unit)
        assertEquals("gehäuft", ing("Kreuzkümmelpulver").note)
        assertEquals(0, BigDecimal("1.5").compareTo(ing("Kartoffeln").amount))
        assertEquals(MeasureUnit.KILOGRAM, ing("Kartoffeln").unit)
    }

    @Test fun nonStandardUnitsBecomePiecesWithNote() {
        assertEquals(MeasureUnit.PIECE, ing("Zwiebel").unit)
        assertEquals("große", ing("Zwiebel").note)
        assertEquals("Zehe/n", ing("Knoblauch").note)
    }

    @Test fun ingredientsWithoutAmount() {
        assertNull(ing("Öl").amount)
        assertEquals("zum Braten", ing("Öl").note)
        assertNull(ing("Salz und Pfeffer").amount)
        assertNull(ing("Milch").amount)
        assertEquals("n. B.", ing("Milch").note)
    }

    @Test fun adjectivesGoToNoteAndOptionalIsDetected() {
        val r2 = MarkdownRecipeImporter.parse(
            "# X\n\n## Zutaten\n\n1\n\nPaprikaschote(n) rote\n\nSambal Oelek optional\n\n## Zubereitung\n\nA",
        )
        assertEquals("Paprikaschote", r2.ingredients[0].name)
        assertEquals("rote", r2.ingredients[0].note)
        assertEquals("Sambal Oelek", r2.ingredients[1].name)
        assertEquals(true, r2.ingredients[1].optional)
        val r3 = MarkdownRecipeImporter.parse(
            "# X\n\n## Zutaten\n\nevtl.\n\nOlivenöl\n\n250 g\n\nLeber(n) durchgedreht, macht evtl. der Metzger\n\n## Zubereitung\n\nA",
        )
        assertEquals(true, r3.ingredients[0].optional)
        assertEquals(false, r3.ingredients[1].optional)
    }

    @Test fun groupsAndSteps() {
        assertEquals("Zutaten für den Stampf", ing("Kartoffeln").group)
        assertNull(ing("Knoblauch").group)
        assertEquals(listOf("Zwiebeln würfeln und anschwitzen.", "Alles 60 Minuten köcheln lassen."), r.steps)
        assertEquals(9, r.ingredients.size)
    }
}

class MarkdownImportCorpusTest {
    @Test fun pieceUnitWordsBecomePiecesWithoutNote() {
        val r = MarkdownRecipeImporter.parse("# X\n\n## Zutaten\n\n1 Stück(e)\n\nIngwerwurzel ca. 2 cm\n\n2 Stk.\n\nEi(er)\n\n## Zubereitung\n\nA")
        assertEquals(MeasureUnit.PIECE, r.ingredients[0].unit)
        assertEquals("ca. 2 cm", r.ingredients[0].note)
        assertEquals(MeasureUnit.PIECE, r.ingredients[1].unit)
        assertNull(r.ingredients[1].note)
    }

    @Test fun vagueAmountWordsStayAmounts() {
        val r = MarkdownRecipeImporter.parse(
            "# X\n\n## Zutaten\n\nviel\n\nKümmel\n\nreichlich\n\nSalz, grobes\n\netwas\n\nPfeffer\n\n## Zubereitung\n\nA",
        )
        assertEquals(listOf("Kümmel", "Salz", "Pfeffer"), r.ingredients.map { it.name })
        assertEquals(listOf<BigDecimal?>(null, null, null), r.ingredients.map { it.amount })
        assertEquals("viel", r.ingredients[0].note)
    }

    @Test fun stepsStopBeforeSiteActions() {
        val r = MarkdownRecipeImporter.parse(
            "# X\n\n## Zutaten\n\n1\n\nEi\n\n## Zubereitung\n\nEi kochen.\n\n- copy Zu einer Sammlung hinzufügen\n\n## Kommentare\n\nToll!",
        )
        assertEquals(listOf("Ei kochen."), r.steps)
    }
}

class StepIngredientMatcherTest {
    @Test fun findsIngredientsMentionedInStepIncludingPlurals() {
        val names = mapOf("k" to "Kartoffel", "z" to "Zwiebel", "s" to "Salz und Pfeffer", "m" to "Milch")
        val hits = StepIngredientMatcher.match("Die Kartoffeln schälen, Zwiebeln würfeln und mit Pfeffer würzen.", names)
        assertEquals(listOf("k", "z", "s"), hits)
    }
}

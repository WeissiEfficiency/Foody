package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Import-Rechnung, die App und Server gemeinsam nutzen (vorher in `RecipeImportRepository` der App). */
class RezeptImportTest {
    private fun bd(s: String) = BigDecimal(s)
    private val rind = Ingredient("rind", "Rindergulasch", category = "Fleisch & Fisch")
    private val salz = Ingredient("salz", "Salz")
    private val mehl = Ingredient("mehl", "Weizenmehl", nutrients = NutrientProfile(NutrientBasis.PER_100_G, mapOf(Nutrient.ENERGY_KJ to bd("1450"))))

    @Test fun geschaetztePortionen() {
        val r = ImportedRecipe("Gulasch", null, listOf(ImportedIngredient("Rindergulasch", bd("1000"), MeasureUnit.GRAM, null, null)), emptyList())
        val e = RezeptImport.vorbereiten(r, listOf(rind), vorgabePortionen = 4)
        assertEquals(ServingsEstimator.estimate("Gulasch", 1000.0, 0.0, null), e.portionen)
        assertTrue(e.geschaetzt)
    }

    @Test fun fleischStueckeEntscheiden() {
        val r = ImportedRecipe("Rindersteaks", null, listOf(ImportedIngredient("Rindergulasch", bd("4"), MeasureUnit.PIECE, null, null)), emptyList())
        assertEquals(ServingsEstimator.estimate("Rindersteaks", 0.0, 0.0, 4), RezeptImport.vorbereiten(r, listOf(rind), 4).portionen)
    }

    @Test fun vorgabeWennNichtSchaetzbar() {
        val r = ImportedRecipe("Suppe", null, listOf(ImportedIngredient("Salz", null, null, null, null)), emptyList())
        val e = RezeptImport.vorbereiten(r, listOf(salz), vorgabePortionen = 4)
        assertEquals(4, e.portionen)
        assertFalse(e.geschaetzt)
    }

    @Test fun zeilenWieInDerApp() {
        val r = ImportedRecipe("Kuchen", null, listOf(
            ImportedIngredient("Salz", null, null, null, null),
            ImportedIngredient("Weizenmehl", bd("250"), MeasureUnit.GRAM, "gesiebt", "Für den Teig", optional = true),
        ), emptyList())
        val z = RezeptImport.vorbereiten(r, listOf(salz, mehl), 4).zeilen
        assertEquals(RezeptImport.Zeile("salz", BigDecimal.ZERO, MeasureUnit.PIECE, null, false), z[0])
        assertEquals(RezeptImport.Zeile("mehl", bd("250"), MeasureUnit.GRAM, "Für den Teig · gesiebt", true), z[1])
    }

    @Test fun portionenAusDerDateiStattSchaetzung() {
        val r = ImportedRecipe("Gulasch", null, listOf(ImportedIngredient("Rindergulasch", bd("1000"), MeasureUnit.GRAM, null, null)), emptyList(), servings = 5)
        val e = RezeptImport.vorbereiten(r, listOf(rind), vorgabePortionen = 4)
        assertEquals(5, e.portionen)
        assertFalse(e.geschaetzt)
        assertTrue(e.ausDatei)
    }

    @Test fun eineGanzeBackwareWirdGeschaetzt() {
        // Backrezepte nennen oft „Portionen: 1“ für den ganzen Kuchen – gegessen wird ein Stück
        val r = ImportedRecipe("Apfelkuchen", null, listOf(ImportedIngredient("Weizenmehl", bd("1000"), MeasureUnit.GRAM, null, null)), emptyList(), servings = 1)
        val e = RezeptImport.vorbereiten(r, listOf(mehl), vorgabePortionen = 4)
        assertEquals(ServingsEstimator.estimate("Apfelkuchen", 1000.0, 3465.6, null), e.portionen)
        assertTrue(e.geschaetzt)
        assertFalse(e.ausDatei)
    }

    @Test fun einePortionAusDerDateiGiltSonst() {
        val r = ImportedRecipe("Lachsfilet", null, listOf(ImportedIngredient("Rindergulasch", bd("1000"), MeasureUnit.GRAM, null, null)), emptyList(), servings = 1)
        assertEquals(1, RezeptImport.vorbereiten(r, listOf(rind), vorgabePortionen = 4).portionen)
    }

    @Test fun hinweisTexte() {
        assertEquals("Quelle: https://x.example/r", RezeptImport.hinweis("https://x.example/r", 3, geschaetzt = false, ausDatei = true))
        assertEquals("", RezeptImport.hinweis(null, 3, geschaetzt = false, ausDatei = true))
        assertEquals(
            "Quelle: https://x.example/r\nPortionenzahl war in der Datei nicht angegeben – aus den Mengen auf 3 geschätzt, bitte prüfen.",
            RezeptImport.hinweis("https://x.example/r", 3, geschaetzt = true),
        )
        assertEquals("Portionenzahl war in der Datei nicht angegeben – auf 4 gesetzt, bitte prüfen.", RezeptImport.hinweis(null, 4, geschaetzt = false))
    }
}

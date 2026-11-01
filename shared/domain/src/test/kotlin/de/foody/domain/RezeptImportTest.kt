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

    private val spaetzle = Ingredient("spaetzle", "Eierspätzle")
    private val tomaten = Ingredient("tomate", "Dosentomaten", conversion = ConversionInfo(canWeightG = bd("400")))

    private fun zeile(ing: ImportedIngredient, z: Ingredient) =
        RezeptImport.vorbereiten(ImportedRecipe("R", null, listOf(ing), emptyList(), servings = 2), listOf(z), 4).zeilen.single()

    @Test fun gewichtAusDemHinweisFuerUnbekanntePackungen() {
        // „1 Pck. Eierspätzle (400 g)“: ohne Packungsgewicht der Zutat gilt die Angabe der Zeile
        val pck = zeile(ImportedIngredient("Eierspätzle", bd("2"), MeasureUnit.PACKAGE, "à 400 g", null), spaetzle)
        assertEquals(0, bd("800").compareTo(pck.menge))
        assertEquals(MeasureUnit.GRAM, pck.einheit)
        assertEquals("2 Pck., à 400 g", pck.hinweis)
        // Behälter im Hinweis: je Stück; Spanne: untere Grenze
        val becher = zeile(ImportedIngredient("Eierspätzle", bd("2"), MeasureUnit.PIECE, "Becher, ca. 200 - 250 g", null), spaetzle)
        assertEquals(0, bd("400").compareTo(becher.menge))
        assertEquals("2 Becher, ca. 200 - 250 g", becher.hinweis)
        // Ohne Behälter: das Gewicht gilt für die ganze Zeile
        val stueck = zeile(ImportedIngredient("Eierspätzle", bd("4"), MeasureUnit.PIECE, "ca. 600 g", null), spaetzle)
        assertEquals(0, bd("600").compareTo(stueck.menge))
        // Volumen bleibt Volumen
        assertEquals(MeasureUnit.MILLILITER, zeile(ImportedIngredient("Eierspätzle", bd("1"), MeasureUnit.PIECE, "Glas, ca. 200 ml", null), spaetzle).einheit)
    }

    @Test fun gewichtAusDemHinweisNurWennNoetig() {
        // Mit bekanntem Dosengewicht bleibt es bei „1 Dose“ (Einkaufsliste: Dosen statt Gramm)
        val dose = zeile(ImportedIngredient("Dosentomaten", bd("1"), MeasureUnit.CAN, "geschälte à ca. 400 g", null), tomaten)
        assertEquals(MeasureUnit.CAN, dose.einheit)
        // Mehrere verschiedene Angaben sind nicht eindeutig
        val zwei = zeile(ImportedIngredient("Eierspätzle", bd("1"), MeasureUnit.PACKAGE, "400 g oder 500 g", null), spaetzle)
        assertEquals(MeasureUnit.PACKAGE, zwei.einheit)
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

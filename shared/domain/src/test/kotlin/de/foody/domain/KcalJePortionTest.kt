package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * kcal je Portion vom Import bis zur Anzeige. Die Rezeptdateien nennen ihre Portionenzahl („Portionen: 3 · …“);
 * früher wurde sie überlesen und aus den Mengen geschätzt – bei Spießen aus 500 g Fleisch auf 1 Portion, sodass
 * jede Anzeige rund das Dreifache zeigte. Dazu je Anzeigeort ein Test, dass dieselbe Zahl herauskommt.
 */
class KcalJePortionTest {
    private fun bd(s: String) = BigDecimal(s)
    private fun g(kj: String, ml: Boolean = false, dichte: String? = null) = NutrientProfile(
        if (ml) NutrientBasis.PER_100_ML else NutrientBasis.PER_100_G, mapOf(Nutrient.ENERGY_KJ to bd(kj), Nutrient.PROTEIN_G to bd("10")),
    ) to ConversionInfo(densityGPerMl = dichte?.let(::bd))

    /** Werte wie in den Startdaten der App. */
    private val katalog = mapOf(
        "Honig" to g("1280", dichte = "1.4"),
        "Senf" to g("370", dichte = "1.1"),
        "Pflanzenöl" to g("3700", dichte = "0.92"),
        "Schweineschnitzel" to g("450"),
    ).map { (name, w) -> Ingredient(name.lowercase(), name, w.second, w.first, if (name == "Schweineschnitzel") "Fleisch & Fisch" else null) }

    // Aufbau wie die Rezeptdateien (Web-Clipper von Chefkoch): Kopfzeile mit Portionen, Zutaten im Volltext
    private val datei = """
        # Fleischspieße mit Honig von koch

        _Quelle: https://example.org/rezepte/7/Spiesse.html_

        ## Rezept: Fleischspieße mit Honig von koch

        Portionen: 3 · Vorbereitung: 30 min · Kochzeit: 10 min · Gesamt: 40 min · Kalorien: 299 kcal · Bewertung: 4.4 (319)

        ## Zutaten

        2 EL

        Honig

        2 EL

        Senf, mittelscharfer

        2 EL

        Öl

        500 g

        Schweineschnitzel

        ## Zubereitung

        Marinade rühren, Fleisch in Streifen auf Spieße stecken und grillen.
    """.trimIndent()

    private fun importiert(): Pair<Recipe, Map<String, Ingredient>> {
        val parsed = MarkdownRecipeImporter.parse(datei)
        val zutaten = parsed.ingredients.map { ing -> katalog.single { it.name == IngredientCatalog.canonicalName(ing.name) } }
        val e = RezeptImport.vorbereiten(parsed, zutaten, vorgabePortionen = 4)
        val rezept = Recipe("r", parsed.name, e.portionen, e.zeilen.mapIndexed { i, z -> RecipeIngredient("l$i", z.zutatId, z.menge, z.einheit, z.optional) })
        return rezept to zutaten.associateBy { it.id }
    }

    // 2 EL Honig 42 g (537,6 kJ) · 2 EL Senf 33 g (122,1 kJ) · 2 EL Öl 27,6 g (1.021,2 kJ) · 500 g Fleisch (2.250 kJ)
    // = 3.930,9 kJ ÷ 3 Portionen = 1.310,3 kJ = 313 kcal (geschätzt waren es 1 Portion und 940 kcal)
    private val erwartet = 313

    @Test fun portionenAusDerDatei() {
        val (rezept, _) = importiert()
        assertEquals(3, rezept.defaultServings)
    }

    @Test fun rezeptdetail() {
        val (rezept, zutaten) = importiert()
        // Detailseite: Nährwerte für die gewählte Portionenzahl, angezeigt je Portion
        for (portionen in listOf(rezept.defaultServings, 1, 2, 7)) {
            val n = NutritionCalculator.calculate(rezept, zutaten, portionen)
            assertEquals(erwartet, NutritionResult.kcal(n.perServing(Nutrient.ENERGY_KJ)!!), "bei $portionen Portionen")
        }
    }

    @Test fun rezeptliste() {
        val (rezept, zutaten) = importiert()
        assertEquals(erwartet, RecipeProfiles.profile(rezept, zutaten).kcalPerServing)
    }

    @Test fun planer() {
        val (rezept, zutaten) = importiert()
        // Die Portionen der Planposition (Haushaltsgröße) ändern die kcal pro Person nicht
        assertEquals(erwartet, DayNutrition.of(listOf(rezept), zutaten)!!.kcal)
        assertEquals(2 * erwartet, DayNutrition.of(listOf(rezept, rezept), zutaten)!!.kcal)
    }

    @Test fun tagebuch() {
        val (rezept, zutaten) = importiert()
        assertEquals(erwartet, Tagebuch.naehrwerteRezept(rezept, zutaten, BigDecimal.ONE).kcal)
        assertEquals(erwartet, Tagebuch.bilanz(listOf(EintragWerte(Mahlzeit.ABENDESSEN, Tagebuch.naehrwerteRezept(rezept, zutaten, BigDecimal.ONE)))).tag.kcal)
    }
}

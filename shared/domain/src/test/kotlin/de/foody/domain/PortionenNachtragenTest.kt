package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Vor Regel 27 importierte Rezepte: Portionen aus der Datei nachtragen, ohne Nutzer-Korrekturen anzufassen. */
class PortionenNachtragenTest {
    private val quelle = "https://www.chefkoch.de/rezepte/1/spitzel.html"
    private fun datei(name: String = "Spitzel", portionen: Int?) = ImportedRecipe(name, quelle, emptyList(), emptyList(), servings = portionen)
    private fun alteNotiz(n: Int) = RezeptImport.hinweis(quelle, n, geschaetzt = true)

    @Test fun geschaetztWirdDurchDateiErsetzt() {
        assertEquals(
            RezeptImport.Nachtrag(5, "Quelle: $quelle"),
            RezeptImport.portionenNachtragen(alteNotiz(1), aktuellePortionen = 1, datei = datei(portionen = 5)),
        )
    }

    @Test fun gleicheZahlEntferntNurDenHinweis() {
        assertEquals(
            RezeptImport.Nachtrag(4, "Quelle: $quelle"),
            RezeptImport.portionenNachtragen(alteNotiz(4), aktuellePortionen = 4, datei = datei(portionen = 4)),
        )
    }

    @Test fun eigeneNotizenBleiben() {
        val notiz = alteNotiz(2) + "\nSchmeckt mit Thymian besser."
        assertEquals(
            RezeptImport.Nachtrag(8, "Quelle: $quelle\nSchmeckt mit Thymian besser."),
            RezeptImport.portionenNachtragen(notiz, aktuellePortionen = 2, datei = datei(portionen = 8)),
        )
    }

    @Test fun ohneQuelleBleibtKeineNotiz() {
        val notiz = RezeptImport.hinweis(null, 2, geschaetzt = true)
        assertEquals(RezeptImport.Nachtrag(6, null), RezeptImport.portionenNachtragen(notiz, 2, datei(portionen = 6)))
    }

    @Test fun vomNutzerGeaendertePortionenBleiben() {
        // Notiz sagt 2, das Rezept hat inzwischen 3 – der Nutzer hat korrigiert
        assertNull(RezeptImport.portionenNachtragen(alteNotiz(2), aktuellePortionen = 3, datei = datei(portionen = 6)))
    }

    @Test fun ohneSchaetzhinweisNichts() {
        // Hinweis gelöscht (Nutzer hat geprüft), Datei-Portionen schon übernommen oder Vorgabe statt Schätzung
        assertNull(RezeptImport.portionenNachtragen("Quelle: $quelle", 2, datei(portionen = 6)))
        assertNull(RezeptImport.portionenNachtragen(null, 2, datei(portionen = 6)))
        assertNull(RezeptImport.portionenNachtragen(RezeptImport.hinweis(quelle, 4, geschaetzt = false), 4, datei(portionen = 6)))
    }

    @Test fun dateiOhnePortionenNichts() {
        assertNull(RezeptImport.portionenNachtragen(alteNotiz(2), 2, datei(portionen = null)))
    }

    @Test fun ganzeBackwareBleibtGeschaetzt() {
        // „Portionen: 1“ bei einem Kuchen schätzt auch der heutige Import – also nichts ändern
        assertNull(RezeptImport.portionenNachtragen(alteNotiz(12), 12, datei("Apfelkuchen", portionen = 1)))
        assertEquals(RezeptImport.Nachtrag(16, "Quelle: $quelle"), RezeptImport.portionenNachtragen(alteNotiz(12), 12, datei("Apfelkuchen", portionen = 16)))
    }

    @Test fun zahlMussGenauPassen() {
        // „auf 1 geschätzt“ darf nicht auf ein Rezept mit 12 Portionen passen und umgekehrt
        assertNull(RezeptImport.portionenNachtragen(alteNotiz(12), 1, datei(portionen = 5)))
        assertNull(RezeptImport.portionenNachtragen(alteNotiz(1), 12, datei(portionen = 5)))
    }

    // „Portionen: 1“ aus der Datei, die das ganze Blech meint (Prüfung der Chefkoch-Sammlung, 2026-11-08)
    private val mehl = Ingredient("mehl", "Weizenmehl", nutrients = NutrientProfile(NutrientBasis.PER_100_G, mapOf(Nutrient.ENERGY_KJ to BigDecimal("1450"))))
    private val katalog = mapOf("mehl" to mehl)
    private fun rezept(gramm: String) = Recipe("r", "x", 1, listOf(RecipeIngredient("l", "mehl", BigDecimal(gramm), MeasureUnit.GRAM)))
    private fun eine(name: String, schritte: List<String> = emptyList(), portionen: Int? = 1) = ImportedRecipe(name, quelle, emptyList(), schritte, servings = portionen)

    @Test fun einePortionWirdNachDemTextNachgetragen() {
        assertEquals(
            RezeptImport.Nachtrag(24, "Quelle: $quelle\nPortionenzahl aus dem Rezepttext: ergibt 24 Stück, bitte prüfen."),
            RezeptImport.einePortionNachtragen("Quelle: $quelle", 1, eine("Laugenstangen", listOf("24 Stücke abstechen.")), rezept("1000"), katalog),
        )
    }

    @Test fun reichhaltigeEinePortionWirdGeschaetzt() {
        val n = RezeptImport.einePortionNachtragen("Quelle: $quelle", 1, eine("Nussecken"), rezept("1000"), katalog)
        val erwartet = ServingsEstimator.estimate("Nussecken", 1000.0, 3465.6, null)!!
        assertEquals(RezeptImport.Nachtrag(erwartet, RezeptImport.hinweis(quelle, erwartet, geschaetzt = true)), n)
    }

    @Test fun einePortionBleibtWennSiePasst() {
        // 400 g Mehl ≈ 1.386 kcal, kein Gebäck, keine Stückzahl: Die 1 der Datei bleibt
        assertNull(RezeptImport.einePortionNachtragen("Quelle: $quelle", 1, eine("Smashed Kebab Burger"), rezept("400"), katalog))
    }

    @Test fun einePortionNurWennNochUnveraendert() {
        val d = eine("Laugenstangen", listOf("24 Stücke abstechen."))
        // Der Nutzer hat die Portionen schon geändert, oder die Datei nennt mehr als eine
        assertNull(RezeptImport.einePortionNachtragen("Quelle: $quelle", 2, d, rezept("1000"), katalog))
        assertNull(RezeptImport.einePortionNachtragen("Quelle: $quelle", 1, eine("Laugenstangen", listOf("24 Stücke abstechen."), 4), rezept("1000"), katalog))
        // Schon einmal geschätzt oder nachgetragen: Die Notiz trägt den Hinweis
        assertNull(RezeptImport.einePortionNachtragen(alteNotiz(1), 1, d, rezept("1000"), katalog))
    }

    @Test fun reichhaltigeEinePortionBleibtBeimAltenNachtragGeschaetzt() {
        // Vor Regel 27 auf 5 geschätzt, die Datei sagt 1 für über 1.500 kcal: Die Schätzung bleibt
        assertNull(RezeptImport.portionenNachtragen(alteNotiz(5), 5, eine("Schaschlik-Topf"), kcalGesamt = 3465.6))
        assertEquals(RezeptImport.Nachtrag(1, "Quelle: $quelle"), RezeptImport.portionenNachtragen(alteNotiz(5), 5, eine("Schaschlik-Topf"), kcalGesamt = 900.0))
    }
}

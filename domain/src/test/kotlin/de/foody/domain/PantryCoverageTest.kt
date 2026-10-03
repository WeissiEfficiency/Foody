package de.foody.domain

import de.foody.domain.PantryCoverage.Requirement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PantryCoverageTest {
    private val reqs = listOf(
        Requirement("suppe", "kartoffel", "Kartoffel"),
        Requirement("suppe", "lauch", "Lauch"),
        Requirement("suppe", "salz", "Salz"),
        Requirement("suppe", "wasser", "Wasser"),
        Requirement("pasta", "nudeln", "Spaghetti"),
        Requirement("pasta", "nudeln", "Spaghetti"), // doppelt im Rezept zählt einmal
        Requirement("pasta", "oel", "Olivenöl"),
    )

    @Test fun countsOnlyMissingRequiredIngredients() {
        val missing = PantryCoverage.missingByRecipe(reqs, inPantry = setOf("kartoffel"))
        assertEquals(1, missing["suppe"], "Nur Lauch fehlt; Salz und Wasser setzt Foody voraus")
        assertEquals(1, missing["pasta"], "Doppelte Zutat zählt einmal, Olivenöl ist Grundvorrat")
    }

    @Test fun everythingAtHomeMeansZero() {
        val missing = PantryCoverage.missingByRecipe(reqs, inPantry = setOf("kartoffel", "lauch", "nudeln"))
        assertEquals(mapOf("suppe" to 0, "pasta" to 0), missing)
    }

    @Test fun recipesWithoutRequirementsAreNotRated() {
        assertTrue(PantryCoverage.missingByRecipe(emptyList(), setOf("x")).isEmpty())
    }

    @Test fun basicsIncludeAliases() {
        assertTrue(IngredientCatalog.assumedAtHome("Pfeffer aus der Mühle"))
        assertTrue(IngredientCatalog.assumedAtHome("Leitungswasser"))
        assertFalse(IngredientCatalog.assumedAtHome("Butter"))
        assertFalse(IngredientCatalog.assumedAtHome("Sesamöl"))
    }
}

package de.foody.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IngredientCatalogTest {
    @Test fun mapsFrequentImportNamesToCanonicalIngredients() {
        assertEquals("Weizenmehl", IngredientCatalog.canonicalName("Mehl"))
        assertEquals("Milch 3,5 %", IngredientCatalog.canonicalName("Milch"))
        assertEquals("Knoblauch", IngredientCatalog.canonicalName("Knoblauchzehe"))
        assertEquals("Paprika", IngredientCatalog.canonicalName("Paprikaschote"))
        assertEquals("Reis (roh)", IngredientCatalog.canonicalName("Reis"))
        assertEquals("Pflanzenöl", IngredientCatalog.canonicalName("Öl"))
        assertEquals("Hähnchenbrust", IngredientCatalog.canonicalName("Hähnchenbrustfilet"))
    }

    @Test fun matchingIgnoresCaseSpacesAndPluralsButKeepsUnknownNames() {
        assertEquals("Zwiebel", IngredientCatalog.canonicalName("  zwiebeln "))
        assertEquals("Weizenmehl", IngredientCatalog.canonicalName("Weizenmehl"))
        // Unbekannt → unverändert (nur getrimmt)
        assertEquals("Drachenfrucht", IngredientCatalog.canonicalName(" Drachenfrucht"))
        // Kein Freitext-Fuzzy-Matching: saure Sahne ist nicht Sahne
        assertEquals("saure Sahne", IngredientCatalog.canonicalName("saure Sahne"))
    }

    @Test fun waterIsNeverBought() {
        assertTrue(IngredientCatalog.neverBuy("Wasser"))
        assertTrue(IngredientCatalog.neverBuy("Leitungswasser"))
        assertFalse(IngredientCatalog.neverBuy("Mineralwasser"))
    }

    @Test fun guessesSupermarketCategory() {
        assertEquals("Öle & Gewürze", IngredientCatalog.guessCategory("Paprikapulver"))
        assertEquals("Kühlregal", IngredientCatalog.guessCategory("Schmand"))
        assertEquals("Obst & Gemüse", IngredientCatalog.guessCategory("Zucchini"))
        assertEquals("Fleisch & Fisch", IngredientCatalog.guessCategory("Schweinebraten"))
        assertEquals("Konserven", IngredientCatalog.guessCategory("Kokosmilch"))
        assertNull(IngredientCatalog.guessCategory("Drachenfrucht"))
    }
}

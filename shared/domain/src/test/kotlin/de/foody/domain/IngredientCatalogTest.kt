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
        // Kein Freitext-Fuzzy-Matching: saure Sahne ist nicht Sahne (sondern eine eigene Startzutat)
        assertEquals("Saure Sahne", IngredientCatalog.canonicalName("saure Sahne"))
    }

    @Test fun breadAndPastriesHaveTheirOwnEntries() {
        // v9: Weißbrot ist kein Mischbrot, Toast kein Brötchen; Semmel bleibt Brötchen
        assertEquals("Weißbrot", IngredientCatalog.canonicalName("Weißbrot"))
        assertEquals("Weißbrot", IngredientCatalog.canonicalName("Scheibe Weißbrot"))
        assertEquals("Toastbrot", IngredientCatalog.canonicalName("Toast"))
        assertEquals("Brötchen", IngredientCatalog.canonicalName("Semmeln"))
        assertEquals("Brezel", IngredientCatalog.canonicalName("Laugenbrezel"))
        assertEquals("Brezel", IngredientCatalog.canonicalName("Brezn"))
        assertEquals("Laugenbrötchen", IngredientCatalog.canonicalName("Laugenstange"))
        assertEquals("Tortilla", IngredientCatalog.canonicalName("Wraps"))
        assertEquals("Brot", IngredientCatalog.canonicalName("Bauernbrot"))
    }

    @Test fun namesThatLostTheirBracketsStillMatch() {
        // Der Web-Clipper macht aus „Mehl (Weißmehl)“ „Mehl Weißmehl“ und aus „Brezel (TK)“ „Brezel TK“
        assertEquals("Weizenmehl", IngredientCatalog.canonicalName("Mehl Weißmehl"))
        assertEquals("Brezel", IngredientCatalog.canonicalName("Brezel TK"))
        assertEquals("Himbeeren", IngredientCatalog.canonicalName("Himbeeren TK"))
        assertEquals("Ei", IngredientCatalog.canonicalName("Ei K. M"))
        assertEquals("Parmesan", IngredientCatalog.canonicalName("Käse Parmesan"))
        // Cherrytomaten sind eigene Zutaten (Stückgewicht 15 g statt 100 g)
        assertEquals("Cherrytomate", IngredientCatalog.canonicalName("Kirschtomaten"))
        assertEquals("Tomate", IngredientCatalog.canonicalName("Strauchtomaten"))
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

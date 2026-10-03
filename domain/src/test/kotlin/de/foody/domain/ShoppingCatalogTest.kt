package de.foody.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShoppingCatalogTest {
    @Test fun noDuplicateItemsAcrossSections() {
        val keys = ShoppingCatalog.sections.flatMap { s -> s.items.map { IngredientCatalog.canonicalName(it.name).lowercase() } }
        val duplicates = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue(duplicates.isEmpty(), "Doppelt im Katalog: $duplicates")
    }

    @Test fun recipeIngredientsMatchCatalogTiles() {
        // Planer-Einträge tragen kanonische Namen – die passende Kachel muss sie erkennen
        assertTrue(ShoppingCatalog.sameItem("Zwiebeln", "Zwiebel"))
        assertTrue(ShoppingCatalog.sameItem("Mehl", "Weizenmehl"))
        assertEquals("🧅", ShoppingCatalog.emojiFor("Zwiebel"))
    }

    @Test fun sectionsComeFromCatalogThenFromOldCategories() {
        assertEquals("Obst & Gemüse", ShoppingCatalog.sectionFor(null, "Tomate"))
        assertEquals("Milch & Käse", ShoppingCatalog.sectionFor("Kühlregal", "Skyr"))
        assertEquals("Zutaten & Gewürze", ShoppingCatalog.sectionFor("Öle & Gewürze", "Garam Masala"))
        assertEquals(ShoppingCatalog.OWN_ITEMS, ShoppingCatalog.sectionFor(null, "schoki"))
    }

    @Test fun searchPrefersWordStart() {
        val hits = ShoppingCatalog.search("milch").map { it.first.name }
        assertEquals("Milch", hits.first())
        assertTrue("Hafermilch" in hits)
    }
}

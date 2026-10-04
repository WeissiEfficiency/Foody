package de.foody.app

import de.foody.app.data.db.SeedData
import de.foody.domain.IngredientCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeedCatalogTest {
    @Test fun everyAliasTargetUsedByImportsExistsInSeedData() {
        val seed = SeedData.names.toSet()
        listOf("Mehl", "Milch", "Reis", "Nudeln", "Knoblauchzehe", "Paprikaschote", "Möhre", "Kartoffeln", "Zwiebeln", "Eier",
            "Tomaten", "Tomaten, geschälte", "Rinderhackfleisch", "Hackfleisch", "Hähnchenbrustfilet", "Olivenöl", "Öl",
            "Schlagsahne", "Meersalz", "Pfeffer", "Zucker", "Wasser", "Gemüsebrühe", "Kokosmilch", "Eigelb",
        ).forEach { name ->
            val target = IngredientCatalog.canonicalName(name)
            assertTrue(target in seed, "„$name“ → „$target“ fehlt in SeedData")
        }
    }

    /** Schreibweisen aus importierten Chefkoch-Rezepten, die seit v8 Werte aus BLS 4.0 bzw. USDA bekommen. */
    @Test fun importedSpellingsFindOfficialValues() {
        val seed = SeedData.names.toSet()
        listOf("Zitronenabrieb", "Bio-Zitronenschale", "Orange Abrieb", "Kreuzkümmelpulver Cumin", "Sojasprossen", "Rübe",
            "HENGLEIN Frische Eierspätzle", "HENGLEIN Frischer Strudelteig", "Noilly Prat", "Tiroler Graukäse", "Fond Entenfond",
            "Suppengemüse Karotten", "Pellkartoffel", "Thai-Basilikum", "Brühepulver", "Zuckerschoten", "Okraschoten", "Fett",
            "Oregano", "Fischsauce", "Austernsauce", "Teriyakisauce", "Koriandergrün", "Gewürzmischung Bratengewürzsalz",
        ).forEach { name ->
            val target = IngredientCatalog.canonicalName(name)
            assertTrue(target in seed, "„$name“ → „$target“ fehlt in SeedData")
        }
    }

    @Test fun officialRowsNameTheirSource() {
        val official = SeedData.ingredients(0).filter { it.id in setOf("seed-oregano", "seed-mohn", "seed-mehlbutter") }
        assertEquals(3, official.size)
        official.forEach { val src = it.nutrientSource.orEmpty(); assertTrue("BLS 4.0" in src || "USDA" in src, src) }
    }

    @Test fun seedNamesAreTheirOwnCanonicalName() {
        SeedData.names.forEach { assertEquals(it, IngredientCatalog.canonicalName(it)) }
    }
}

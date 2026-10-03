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

    @Test fun seedNamesAreTheirOwnCanonicalName() {
        SeedData.names.forEach { assertEquals(it, IngredientCatalog.canonicalName(it)) }
    }
}

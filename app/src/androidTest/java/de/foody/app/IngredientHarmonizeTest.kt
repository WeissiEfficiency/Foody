package de.foody.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.db.PantryItemEntity
import de.foody.app.data.db.SeedData
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Vereinheitlichen und Zusammenführen von Zutaten gegen eine In-Memory-Room-Datenbank (inkl. Fremdschlüssel). */
@RunWith(AndroidJUnit4::class)
class IngredientHarmonizeTest {
    private lateinit var db: FoodyDatabase
    private lateinit var ingredients: IngredientRepository
    private lateinit var recipes: RecipeRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatabase::class.java).build()
        ingredients = IngredientRepository(db, db.ingredientDao(), context)
        recipes = RecipeRepository(db.recipeDao())
    }

    @After fun tearDown() = db.close()

    private fun ingredient(id: String, name: String, kj: String? = null) = IngredientEntity(
        id, name, null, null, null, if (kj != null) NutrientBasis.PER_100_G else null,
        kj?.let(::BigDecimal), null, null, null, null, null, null, null, 0, 0,
    )

    @Test fun importUsesCanonicalNames() = runTest {
        db.ingredientDao().upsert(ingredient("flour", "Weizenmehl", kj = "1450"))
        assertEquals("flour", ingredients.getOrCreate("Mehl").id)
        assertEquals("Knoblauch", ingredients.getOrCreate("Knoblauchzehe").canonicalName)
    }

    @Test fun harmonizeMergesDuplicatesAndRepointsReferences() = runTest {
        db.ingredientDao().upsert(ingredient("flour", "Weizenmehl", kj = "1450"))
        db.ingredientDao().upsert(ingredient("mehl", "Mehl"))
        db.ingredientDao().upsert(ingredient("zw", "Zwiebeln"))
        val cake = recipes.save(
            RecipeDraft(null, "Kuchen", 4, null, null, null, null, "",
                listOf(RecipeDraft.Line("mehl", BigDecimal("250"), MeasureUnit.GRAM, null, false)), emptyList()),
        )
        db.pantryDao().upsert(PantryItemEntity("p", "mehl", BigDecimal("1000"), MeasureUnit.GRAM, null, 0))

        val changed = ingredients.harmonizeNames()

        // Mehl zusammengeführt, Zwiebeln umbenannt, Weizenmehl bekommt seine fehlende Kategorie
        assertEquals(3, changed)
        assertEquals("Trockenwaren", db.ingredientDao().get("flour")?.category)
        assertNull(db.ingredientDao().get("mehl"))
        assertEquals("flour", db.recipeDao().getIngredients(cake).single().ingredientId)
        assertEquals("flour", db.pantryDao().getAll().single().ingredientId)
        assertEquals("Zwiebel", db.ingredientDao().get("zw")?.canonicalName)
        // Zweiter Lauf ändert nichts mehr
        assertEquals(0, ingredients.harmonizeNames())
    }

    @Test fun mergeKeepsTargetAndFillsMissingNutrients() = runTest {
        db.ingredientDao().upsert(ingredient("a", "Schalotte"))
        db.ingredientDao().upsert(ingredient("b", "Schalotten (frisch)", kj = "300"))
        ingredients.merge("b", "a")
        assertNull(db.ingredientDao().get("b"))
        val a = assertNotNull(db.ingredientDao().get("a"))
        assertEquals(0, BigDecimal("300").compareTo(a.energyKj))
    }

    @Test fun seedUpgradeCorrectsUntouchedSeedRowsOnly() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("foody", android.content.Context.MODE_PRIVATE).edit().clear().putInt("seedVersion", 2).commit()
        val oldOil = ingredient("seed-olivenoel", "Olivenöl", kj = "3700")
            .copy(nutrientBasis = NutrientBasis.PER_100_ML, nutrientSource = SeedData.SOURCE)
        val userButter = ingredient("seed-butter", "Butter", kj = "3000").copy(nutrientSource = "manuell")
        db.ingredientDao().upsert(oldOil)
        db.ingredientDao().upsert(userButter)

        ingredients.seedIfNeeded()

        assertEquals(NutrientBasis.PER_100_G, db.ingredientDao().get("seed-olivenoel")?.nutrientBasis)
        assertEquals(0, BigDecimal("3000").compareTo(db.ingredientDao().get("seed-butter")?.energyKj)) // Nutzerwert bleibt
        assertNotNull(db.ingredientDao().findByName("Pflanzenöl")) // neue Einträge aus v2 kommen dazu
    }
}

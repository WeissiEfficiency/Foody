package de.foody.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class RecipeSearchTest {
    private lateinit var db: FoodyDatabase
    private lateinit var recipes: RecipeRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FoodyDatabase::class.java).build()
        recipes = RecipeRepository(db.recipeDao())
    }

    @After fun tearDown() = db.close()

    private suspend fun recipe(name: String, ingredientId: String) = recipes.save(
        RecipeDraft(null, name, 2, null, null, null, null, "",
            listOf(RecipeDraft.Line(ingredientId, BigDecimal("1"), MeasureUnit.PIECE, null, false)), emptyList()),
    )

    @Test fun findsRecipesByIngredientAndTreatsWildcardsLiterally() = runTest {
        db.ingredientDao().upsert(IngredientEntity("z", "Zucchini", null, null, null, null, null, null, null, null, null, null, null, null, 0, 0))
        db.ingredientDao().upsert(IngredientEntity("k", "Kakao 100%", null, null, null, null, null, null, null, null, null, null, null, null, 0, 0))
        recipe("Gemüsepfanne", "z")
        recipe("Schokokuchen", "k")
        recipe("Pfannkuchen", "k")

        assertEquals(listOf("Gemüsepfanne"), recipes.observe("zucchini", false).first().map { it.name })
        assertEquals(listOf("Pfannkuchen", "Schokokuchen"), recipes.observe("100%", false).first().map { it.name })
        // „%“ allein ist kein Platzhalter mehr, der alles findet
        assertEquals(emptyList(), recipes.observe("1%0", false).first().map { it.name })
    }
}

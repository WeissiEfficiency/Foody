package de.foody.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.RecipeImportRepository
import de.foody.app.data.repo.RecipeRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class FavoritesAndDedupTest {
    private lateinit var db: FoodyDatabase
    private lateinit var recipes: RecipeRepository
    private lateinit var importer: RecipeImportRepository

    private val markdown = """
        # Kartoffelsuppe

        _Quelle: https://example.org/rezepte/1/Kartoffelsuppe.html_

        ## Zutaten

        1 kg

        Kartoffel(n)

        ## Zubereitung

        Kochen.
    """.trimIndent()

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatabase::class.java).build()
        recipes = RecipeRepository(db.recipeDao())
        importer = RecipeImportRepository(recipes, IngredientRepository(db, db.ingredientDao(), context), context)
    }

    @After fun tearDown() = db.close()

    private suspend fun import() = importer.importText(markdown, 4, "Ideen") { "Quelle: $it" }

    @Test fun importingTheSameSourceTwiceIsSkipped() = runTest {
        val first = assertNotNull(import())
        assertEquals("https://example.org/rezepte/1/Kartoffelsuppe.html", recipes.get(first)?.sourceUrl)
        assertNull(import()) // bereits vorhanden → übersprungen
        assertEquals(1, db.recipeDao().getAll().size)
    }

    @Test fun favoriteAndSourceSurviveEditingButNotDuplicating() = runTest {
        val id = assertNotNull(import())
        recipes.setFavorite(id, true)

        // Bearbeiten (Speichern über den Editor-Entwurf) darf Favorit und Quelle nicht verlieren
        val draft = recipes.draftOf(id)!!
        recipes.save(draft.copy(name = "Omas Kartoffelsuppe"))
        val edited = recipes.get(id)!!
        assertTrue(edited.favorite)
        assertEquals("https://example.org/rezepte/1/Kartoffelsuppe.html", edited.sourceUrl)

        // Eine Kopie ist kein Duplikat des Imports und kein Favorit
        val copy = recipes.get(recipes.duplicate(id, "(Kopie)")!!)!!
        assertNull(copy.sourceUrl)
        assertEquals(false, copy.favorite)
    }
}

package de.foody.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Eigene Fotos im App-Speicher: nur löschen, wenn kein Rezept mehr darauf zeigt. */
@RunWith(AndroidJUnit4::class)
class RecipePhotoStoreTest {
    private lateinit var db: FoodyDatabase
    private lateinit var photos: RecipePhotoStore
    private lateinit var recipes: RecipeRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatabase::class.java).build()
        photos = RecipePhotoStore(context, db.recipeDao())
        recipes = RecipeRepository(db.recipeDao())
    }

    @After fun tearDown() = db.close()

    @Test fun photoSharedByDuplicateSurvivesUntilLastRecipeIsGone() = runTest {
        val (file, _) = photos.newPhotoTarget()
        file.writeText("jpeg")
        val uri = photos.storedUri(file)
        val original = recipes.save(RecipeDraft(id = null, name = "Suppe", defaultServings = 2, imageUri = uri, ingredients = emptyList()))
        val copy = recipes.duplicate(original, "(Kopie)")!!

        recipes.delete(original)
        photos.deleteIfUnused(uri)
        assertTrue(file.exists(), "Die Kopie nutzt das Foto noch")

        recipes.delete(copy)
        photos.deleteIfUnused(uri)
        assertFalse(file.exists())
    }

    @Test fun foreignUrisAreNeverTouched() = runTest {
        val foreign = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "fremd.jpg").apply { writeText("x") }
        photos.deleteIfUnused(foreign.toURI().toString())
        photos.deleteIfUnused("content://media/external/images/1")
        assertTrue(foreign.exists())
    }
}

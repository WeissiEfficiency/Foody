package de.foody.app.sync

import androidx.room.Room
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.SYNC_CALLBACK
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Editor-Speichern darf ein zwischenzeitlich (per Sync) verlinktes Foto nicht überschreiben. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RecipeSaveImageTest {
    private lateinit var db: FoodyDatabase
    private lateinit var recipes: RecipeRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatabase::class.java)
            .addCallback(FoodyDatabase.SYNC_CALLBACK).allowMainThreadQueries().build()
        recipes = RecipeRepository(db.recipeDao())
    }

    @After
    fun tearDown() = db.close()

    private fun editorDraft(id: String, image: String?, original: String?, name: String) = RecipeDraft(
        id = id, name = name, defaultServings = 2, imageUri = image, ingredients = emptyList(),
        originalImageUri = original, trackOriginalImage = true,
    )

    @Test
    fun remotePhotoLinkedWhileEditingSurvivesSaveWithoutPhotoChange() = runTest {
        val id = recipes.save(RecipeDraft(id = null, name = "Suppe", defaultServings = 2, ingredients = emptyList()))
        // Editor öffnet ohne Foto; währenddessen verlinkt der Sync ein Foto.
        db.recipeDao().upsert(db.recipeDao().get(id)!!.copy(imageUri = "file:///fotos/x.jpg"))
        recipes.save(editorDraft(id, image = null, original = null, name = "Suppe (Tippfehler korrigiert)"))
        assertEquals("file:///fotos/x.jpg", db.recipeDao().get(id)!!.imageUri)
        assertEquals("Suppe (Tippfehler korrigiert)", db.recipeDao().get(id)!!.name)
    }

    @Test
    fun photoChangedInEditorIsSaved() = runTest {
        val id = recipes.save(RecipeDraft(id = null, name = "Suppe", defaultServings = 2, imageUri = "file:///a.jpg", ingredients = emptyList()))
        recipes.save(editorDraft(id, image = "file:///b.jpg", original = "file:///a.jpg", name = "Suppe"))
        assertEquals("file:///b.jpg", db.recipeDao().get(id)!!.imageUri)
        recipes.save(editorDraft(id, image = null, original = "file:///b.jpg", name = "Suppe"))
        assertNull(db.recipeDao().get(id)!!.imageUri)
    }
}

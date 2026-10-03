package de.foody.app

import android.content.Context
import androidx.core.net.toUri
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
import java.io.File
import kotlin.test.assertEquals

/** Rezeptimport liest Dateien begrenzt: Eine riesige Datei zählt als fehlgeschlagen, statt den Speicher zu füllen. */
@RunWith(AndroidJUnit4::class)
class ImportLimitsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: FoodyDatabase
    private lateinit var importer: RecipeImportRepository
    private lateinit var work: File

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatabase::class.java).build()
        val recipes = RecipeRepository(db.recipeDao())
        importer = RecipeImportRepository(db, recipes, IngredientRepository(db, db.ingredientDao(), context), context)
        work = File(context.cacheDir, "import-test-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun tearDown() {
        db.close()
        work.deleteRecursively()
    }

    @Test fun oversizedFileFailsOthersStillImport() = runTest {
        val ok = File(work, "ok.md").apply { writeText("# Tomatensuppe\n\n## Zutaten\n- 500 g Tomaten\n\n## Zubereitung\n1. Kochen.\n") }
        val huge = File(work, "riesig.md").apply {
            writeText("# Riesig\n\n## Zutaten\n- 1 Ei\n" + "x".repeat(2 shl 20))
        }

        val result = importer.import(listOf(huge.toUri(), ok.toUri()), defaultServings = 2, tag = "Test", notesTemplate = { "" })

        assertEquals(1, result.importedIds.size)
        assertEquals(1, result.failed)
    }
}

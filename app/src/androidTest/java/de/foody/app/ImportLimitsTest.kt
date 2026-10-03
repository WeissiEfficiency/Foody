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
import kotlin.test.assertTrue

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

        val result = importer.import(listOf(huge.toUri(), ok.toUri()), defaultServings = 2, tag = "Test", notesTemplate = { _, _, _ -> "" })

        assertEquals(1, result.importedIds.size)
        assertEquals(1, result.failed)
    }

    @Test fun importEstimatesServingsAndSaysSo() = runTest {
        // 2 kg Braten + Knödel: früher fest 4 Portionen, jetzt aus Gewicht und Energie geschätzt
        val roast = File(work, "braten.md").apply {
            writeText(
                """
                # Krustenbraten

                ## Zutaten

                2 kg

                Schweinebraten

                500 g

                Knödelbrot

                3

                Eier

                1 Liter

                Bier dunkles

                ## Zubereitung

                Braten.
                """.trimIndent(),
            )
        }
        val result = importer.import(listOf(roast.toUri()), defaultServings = 4, tag = "Test",
            notesTemplate = { _, servings, estimated -> "geschätzt=$estimated:$servings" })
        val recipe = db.recipeDao().getAll().single { it.id == result.importedIds.single() }
        assertTrue(recipe.defaultServings in 6..8, "Portionen: ${recipe.defaultServings}")
        assertEquals("geschätzt=true:${recipe.defaultServings}", recipe.notes)
    }
}

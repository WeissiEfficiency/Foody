package de.foody.app

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.repo.BackupRepository
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import de.foody.app.data.db.MealSlotEntity
import de.foody.app.data.db.TagebuchEintragEntity
import de.foody.domain.MeasureUnit
import de.foody.domain.TagebuchArt
import de.foody.domain.Mahlzeit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Sicherung als ZIP: Fotos überstehen Export → Löschen → Import; manipulierte Archive richten nichts an. */
@RunWith(AndroidJUnit4::class)
class BackupZipTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: FoodyDatabase
    private lateinit var photos: RecipePhotoStore
    private lateinit var recipes: RecipeRepository
    private lateinit var backup: BackupRepository
    private lateinit var work: File

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatabase::class.java).build()
        photos = RecipePhotoStore(context, db.recipeDao())
        recipes = RecipeRepository(db.recipeDao())
        backup = BackupRepository(db, context, photos)
        work = File(context.cacheDir, "backup-test-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun tearDown() {
        db.close()
        work.deleteRecursively()
    }

    private suspend fun recipeImage(id: String) = recipes.observeRecipe(id).first()?.imageUri

    @Test fun photoSurvivesExportDeleteImport() = runTest {
        val (file, _) = photos.newPhotoTarget()
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        val id = recipes.save(RecipeDraft(id = null, name = "Suppe", defaultServings = 2, imageUri = photos.storedUri(file), ingredients = emptyList()))
        val zip = File(work, "s.zip").toUri()

        backup.export(zip)
        backup.deleteAll()
        assertFalse(file.exists(), "Ohne Rezept wird das Foto aufgeräumt")

        backup.import(zip)
        val restored = recipeImage(id)!!
        assertNotEquals(photos.storedUri(file), restored, "Wiederhergestellt als neue Datei")
        assertTrue(File(restored.toUri().path!!).readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
    }

    @Test fun legacyJsonBackupStillImports() = runTest {
        val id = recipes.save(RecipeDraft(id = null, name = "Alt", defaultServings = 1, imageUri = null, ingredients = emptyList()))
        val zip = File(work, "s.zip")
        backup.export(zip.toUri())
        val json = File(work, "alt.json")
        java.util.zip.ZipFile(zip).use { z -> json.writeBytes(z.getInputStream(z.getEntry("backup.json")).readBytes()) }

        backup.deleteAll()
        backup.import(json.toUri())
        assertEquals("Alt", recipes.observeRecipe(id).first()?.name)
    }

    @Test fun maliciousEntriesAreIgnored() = runTest {
        val id = recipes.save(RecipeDraft(id = null, name = "Böse", defaultServings = 1, imageUri = null, ingredients = emptyList()))
        val clean = File(work, "clean.zip")
        backup.export(clean.toUri())
        val data = java.util.zip.ZipFile(clean).use { z -> z.getInputStream(z.getEntry("backup.json")).readBytes() }
            .decodeToString().replace("\"imageUri\": null", "\"imageUri\": \"foody-backup-photo:../../evil.jpg\"")

        val evil = File(work, "evil.zip")
        ZipOutputStream(evil.outputStream()).use { z ->
            for (name in listOf("photos/../../evil.jpg", "../evil.jpg", "/data/evil.jpg", "photos/x.sh")) {
                z.putNextEntry(ZipEntry(name)); z.write("boom".toByteArray()); z.closeEntry()
            }
            z.putNextEntry(ZipEntry("backup.json")); z.write(data.toByteArray()); z.closeEntry()
        }
        // Ab Android 14 lehnt schon ZipInputStream „../“-Pfade ab (Import bricht ab, Daten bleiben);
        // auf älteren Versionen greift der eigene Namensfilter. Beides muss sicher enden.
        runCatching { backup.import(evil.toUri()) }

        assertEquals("Böse", recipes.observeRecipe(id).first()?.name)
        assertNull(recipeImage(id), "Unbekannter Fotoverweis wird nicht übernommen")
        val evilFiles = context.filesDir.parentFile!!.walkTopDown().filter { it.name == "evil.jpg" || it.name == "x.sh" }.toList()
        assertTrue(evilFiles.isEmpty(), "Keine Datei außerhalb des Fotoordners: $evilFiles")
    }

    @Test fun brokenArchiveLeavesDataUntouched() = runTest {
        val id = recipes.save(RecipeDraft(id = null, name = "Bleibt", defaultServings = 1, imageUri = null, ingredients = emptyList()))
        val broken = File(work, "kaputt.zip")
        ZipOutputStream(broken.outputStream()).use { z -> z.putNextEntry(ZipEntry("photos/1.jpg")); z.write(1); z.closeEntry() }

        val photoDir = File(context.filesDir, "recipe_images")
        val before = photoDir.list().orEmpty().toSet()

        assertFailsWith<IllegalStateException> { backup.import(Uri.fromFile(broken)) }
        assertEquals("Bleibt", recipes.observeRecipe(id).first()?.name)
        assertEquals(before, photoDir.list().orEmpty().toSet(), "Vorgemerkte Fotos wieder entfernt")
    }

    @Test fun importedImageMayNotPointIntoPrivateStorage() = runTest {
        val own = recipes.save(RecipeDraft(id = null, name = "Ausspähen", defaultServings = 1, imageUri = null, ingredients = emptyList()))
        val gallery = recipes.save(RecipeDraft(id = null, name = "Galerie", defaultServings = 1, imageUri = null, ingredients = emptyList()))
        val zip = File(work, "s.zip")
        backup.export(zip.toUri())
        val dbPath = context.getDatabasePath("foody.db").toUri().toString()
        val json = File(work, "manipuliert.json")
        val text = java.util.zip.ZipFile(zip).use { z -> z.getInputStream(z.getEntry("backup.json")).readBytes().decodeToString() }
        // Erstes Rezept zeigt auf die eigene Datenbank, zweites auf ein Galeriebild
        json.writeText(
            text.replaceFirst("\"imageUri\": null", "\"imageUri\": \"$dbPath\"")
                .replaceFirst("\"imageUri\": null", "\"imageUri\": \"content://media/external/images/media/7\""),
        )

        backup.import(json.toUri())
        val images = listOf(recipeImage(own), recipeImage(gallery))
        assertTrue(dbPath !in images, "Pfad in den privaten Speicher wird verworfen: $images")
        assertTrue("content://media/external/images/media/7" in images, "Galerielinks bleiben erhalten")
    }

    @Test fun einordnungImBackupUndFreitextWirdZugeordnet() = runTest {
        val id = recipes.save(
            RecipeDraft(id = null, name = "Porridge", defaultServings = 1, ingredients = emptyList(),
                mahlzeiten = setOf(Mahlzeit.FRUEHSTUECK), einordnungUebernehmen = true),
        )
        val ohne = recipes.save(RecipeDraft(id = null, name = "Curry", defaultServings = 2, ingredients = emptyList()))
        // Alter Freitext, wie ihn eine Sicherung vor DB v7 enthält
        db.mealPlanDao().upsert(MealSlotEntity("s", java.time.LocalDate.of(2026, 10, 10), "Frühstück", id, 1, createdAt = 1, updatedAt = 1))
        val zip = File(work, "s.zip").toUri()

        backup.export(zip)
        backup.deleteAll()
        backup.import(zip)

        assertEquals("FRUEHSTUECK", recipes.get(id)?.mahlzeiten)
        assertNull(recipes.get(id)?.gaenge)
        assertNull(recipes.get(ohne)?.mahlzeiten)
        assertEquals("FRUEHSTUECK", db.mealPlanDao().get("s")?.slotType)
    }

    @Test fun tagebuchImBackup() = runTest {
        val e = TagebuchEintragEntity(
            id = "t1", datum = java.time.LocalDate.of(2026, 10, 10), mahlzeit = "SNACK", art = TagebuchArt.ZUTAT, name = "Joghurt",
            zutatId = "z", menge = java.math.BigDecimal("150"), einheit = MeasureUnit.GRAM, energieKj = java.math.BigDecimal("380.5"),
            vollstaendig = false, createdAt = 1, updatedAt = 2,
        )
        db.tagebuchDao().upsert(e)
        val zip = File(work, "t.zip")
        backup.export(zip.toUri())
        backup.deleteAll()
        assertNull(db.tagebuchDao().get("t1"))
        backup.import(zip.toUri())
        assertEquals(e, db.tagebuchDao().get("t1"))

        // Ältere Sicherung ohne Tagebuch bleibt lesbar
        val json = File(work, "alt.json")
        val text = java.util.zip.ZipFile(zip).use { z -> z.getInputStream(z.getEntry("backup.json")).readBytes().decodeToString() }
        json.writeText(text.replace(Regex(""",\s*"tagebuch"\s*:\s*\[[^\]]*\]"""), ""))
        assertFalse("tagebuch" in json.readText())
        backup.deleteAll()
        backup.import(json.toUri())
        assertNull(db.tagebuchDao().get("t1"))
    }

    @Test fun strichcodeImBackup() = runTest {
        db.ingredientDao().upsert(de.foody.app.data.db.IngredientEntity(id = "j", canonicalName = "Joghurt", barcode = "4006040002031", createdAt = 1, updatedAt = 1))
        val zip = File(work, "b.zip")
        backup.export(zip.toUri())
        backup.deleteAll()
        backup.import(zip.toUri())
        assertEquals("4006040002031", db.ingredientDao().get("j")?.barcode)
    }
}

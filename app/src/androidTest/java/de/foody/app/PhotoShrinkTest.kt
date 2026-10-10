package de.foody.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.db.FoodyDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Kamerafotos werden auf 1600 px verkleinert, gedreht nach EXIF und bleiben unter der alten Größe. */
@RunWith(AndroidJUnit4::class)
class PhotoShrinkTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: FoodyDatabase
    private lateinit var photos: RecipePhotoStore

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatabase::class.java).build()
        photos = RecipePhotoStore(context, db.recipeDao())
    }

    @After fun tearDown() = db.close()

    private fun bounds(path: String) = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        .also { BitmapFactory.decodeFile(path, it) }

    @Test fun largeCameraPhotoIsShrunkAndRotated() = runTest {
        val (file, _) = photos.newPhotoTarget()
        // Querformat-Sensorbild 4000 × 3000 mit EXIF „90° drehen“ – so speichern viele Kameras Hochformat-Fotos
        val bmp = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2E7D32.toInt()) }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        val before = file.length()

        assertTrue(photos.shrink(file))

        val b = bounds(file.path)
        assertEquals(1200, b.outWidth, "gedreht: jetzt Hochformat")
        assertEquals(1600, b.outHeight)
        assertTrue(file.length() < before, "${file.length()} < $before")
        assertFalse(java.io.File(file.path + ".tmp").exists())
        file.delete()
    }

    @Test fun smallPhotoStaysUntouched() = runTest {
        val (file, _) = photos.newPhotoTarget()
        Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).also { b -> file.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 80, it) } }
        val modified = file.lastModified()
        assertFalse(photos.shrink(file))
        assertEquals(modified, file.lastModified())
        file.delete()
    }

    /** Der Start verkleinert alte Fotos nur einmal (je Verkleinerungs-Version), nicht bei jedem Start erneut. */
    @Test fun startVerkleinertNurEinmal() = runTest {
        context.getSharedPreferences("foody", Context.MODE_PRIVATE).edit().remove(RecipePhotoStore.PREF_VERKLEINERT).commit()
        fun gross(): java.io.File {
            val (file, _) = photos.newPhotoTarget()
            val bmp = Bitmap.createBitmap(3200, 2400, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            return file
        }
        val erstes = gross()
        assertTrue(photos.shrinkAllEinmal() >= 1)
        val zweites = gross()
        assertEquals(0, photos.shrinkAllEinmal(), "zweiter Start liest die Fotos nicht mehr")
        assertEquals(3200, bounds(zweites.path).outWidth)
        erstes.delete(); zweites.delete()
    }
}

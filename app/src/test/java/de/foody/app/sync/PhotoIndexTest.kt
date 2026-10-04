package de.foody.app.sync

import androidx.room.Room
import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.db.FoodyDatabase
import de.foody.sync.protocol.PhotoHash
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PhotoIndexTest {
    private lateinit var db: FoodyDatabase
    private lateinit var store: RecipePhotoStore
    private lateinit var index: PhotoIndex
    private val created = ArrayList<File>()

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, FoodyDatabase::class.java).allowMainThreadQueries().build()
        store = RecipePhotoStore(app, db.recipeDao())
        index = PhotoIndex(db, store)
    }

    @After
    fun tearDown() {
        created.forEach { it.delete() }
        db.close()
    }

    private fun photo(content: ByteArray): File = store.newPhotoFile().also {
        it.writeBytes(content)
        created += it
    }

    private val jpeg1 = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)
    private val jpeg2 = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 9, 8, 7, 6, 5)

    @Test
    fun hashMatchesSha256OfFile() = runTest {
        val file = photo(jpeg1)
        assertEquals(PhotoHash.of(jpeg1), index.hashOf(store.storedUri(file)))
    }

    @Test
    fun contentUriHasNoHash() = runTest {
        assertNull(index.hashOf("content://media/external/images/1"))
        assertNull(index.hashOf(null))
        // Datei außerhalb des Fotoordners (auch per ..-Umweg) zählt nicht
        val outside = File(RuntimeEnvironment.getApplication().filesDir, "outside.jpg").also { it.writeBytes(jpeg1); created += it }
        assertNull(index.hashOf(store.storedUri(outside)))
        val sneaky = "file://" + store.fileOf(store.storedUri(photo(jpeg1)))!!.parent + "/../outside.jpg"
        assertNull(index.hashOf(sneaky))
    }

    @Test
    fun missingFileHasNoHash() = runTest {
        assertNull(index.hashOf(store.storedUri(store.newPhotoFile())))
    }

    @Test
    fun changedFileIsRehashed() = runTest {
        val file = photo(jpeg1)
        val uri = store.storedUri(file)
        val first = index.hashOf(uri)
        assertEquals(PhotoHash.of(jpeg1), first)
        // wie shrink: Datei wird ersetzt (andere Größe und Zeit)
        file.writeBytes(jpeg2)
        file.setLastModified(file.lastModified() + 5_000)
        val second = index.hashOf(uri)
        assertEquals(PhotoHash.of(jpeg2), second)
        assertNotEquals(first, second)
        assertEquals(second, db.syncDao().photoLocal(uri)?.sha256)
    }

    @Test
    fun cachedHashIsUsedWhileFileIsUnchanged() = runTest {
        val file = photo(jpeg1)
        val uri = store.storedUri(file)
        index.hashOf(uri)
        val cached = db.syncDao().photoLocal(uri)!!
        db.syncDao().upsertPhotoLocal(cached.copy(sha256 = "x".repeat(64)))
        assertEquals("x".repeat(64), index.hashOf(uri))
    }

    @Test
    fun uriForFindsCachedFile() = runTest {
        val file = photo(jpeg1)
        val uri = store.storedUri(file)
        val sha = index.hashOf(uri)!!
        assertEquals(uri, index.uriFor(sha))
        assertNull(index.uriFor(PhotoHash.of(jpeg2)))
        // Datei geändert: Cache-Eintrag nicht mehr aktuell
        file.writeBytes(jpeg2)
        file.setLastModified(file.lastModified() + 5_000)
        assertNull(index.uriFor(sha))
        // Datei weg
        file.delete()
        assertNull(index.uriFor(sha))
    }
}

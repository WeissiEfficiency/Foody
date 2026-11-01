package de.foody.core.data.fotos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import de.foody.core.data.Einstellungsschluessel
import de.foody.core.data.db.FoodyDatenbank
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fotoablage und Foto-Index: Kamerafotos werden verkleinert und nach EXIF gedreht, kleine bleiben byte-gleich,
 * der Nachlauf über alte Fotos läuft nur einmal, Hashes werden bis zur nächsten Dateiänderung gemerkt.
 * Robolectric mit echter Grafik (NATIVE), damit Bitmaps wirklich kodiert und dekodiert werden.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FotoAblageTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: FoodyDatenbank
    private lateinit var ablage: FotoAblage
    private lateinit var index: FotoIndex
    private val ordner get() = File(context.filesDir, Einstellungsschluessel.FOTO_ORDNER)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatenbank::class.java).allowMainThreadQueries().build()
        ablage = FotoAblage(context)
        index = FotoIndex(db.syncDao(), ablage)
        ordner.deleteRecursively()
        context.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        db.close()
        ordner.deleteRecursively()
    }

    private fun groesse(datei: File) = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        .also { BitmapFactory.decodeFile(datei.path, it) }

    /** Bild mit Farbverlauf (wie ein Foto nicht trivial komprimierbar, aber auch kein reines Rauschen). */
    private fun verlauf(breite: Int, hoehe: Int): Bitmap =
        Bitmap.createBitmap(breite, hoehe, Bitmap.Config.ARGB_8888).also { bmp ->
            val paint = Paint().apply {
                shader = LinearGradient(0f, 0f, breite.toFloat(), hoehe.toFloat(), Color.RED, Color.BLUE, Shader.TileMode.CLAMP)
            }
            Canvas(bmp).drawRect(0f, 0f, breite.toFloat(), hoehe.toFloat(), paint)
        }

    private fun quelle(bmp: Bitmap, qualitaet: Int, name: String = "quelle.jpg"): File =
        File(context.cacheDir, name).also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, qualitaet, it) } }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun grossesKamerafotoWirdVerkleinertUndGedreht() = runTest {
        // Querformat-Sensorbild 4000 × 3000 mit EXIF „90° drehen“ – so speichern viele Kameras Hochformat-Fotos
        val datei = quelle(verlauf(4000, 3000), 100)
        ExifInterface(datei.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val link = ablage.speichern(Uri.fromFile(datei))

        assertTrue(link.startsWith("file:"), link)
        val gespeichert = assertNotNull(ablage.datei(link))
        assertEquals(ordner.canonicalFile, gespeichert.parentFile)
        assertEquals(Uri.fromFile(gespeichert).toString(), link, "Link-Form wie v1 (file.toUri())")
        val b = groesse(gespeichert)
        assertEquals(1200, b.outWidth, "gedreht: jetzt Hochformat")
        assertEquals(1600, b.outHeight)
        assertTrue(gespeichert.length() <= 600L * 1024, "${gespeichert.length()} ≤ 600 KB")
        assertEquals(ExifInterface.ORIENTATION_UNDEFINED, ExifInterface(gespeichert.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
        assertTrue(ordner.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        assertTrue(datei.exists(), "Quelle bleibt unangetastet")
    }

    /**
     * Während [FotoAblage.speichern] liest, liegt im Fotoordner keine unfertige `.jpg`: der Startlauf, der nur `*.jpg`
     * anfasst, sieht die Arbeitsdatei also nie. Geprüft wird der Ordner in dem Moment, in dem die Quelle zu Ende
     * gelesen ist (die Arbeitsdatei existiert dann schon), und noch einmal danach.
     */
    @Test
    fun zwischendateienEndenNieAufJpg() = runTest {
        val bytes = quelle(verlauf(4000, 3000), 100).readBytes()
        val uri = Uri.parse("content://test.fotos/kamera/1")
        val waehrendDesLesens = mutableListOf<List<String>>()
        Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(uri) {
            object : FilterInputStream(bytes.inputStream()) {
                private fun merken(n: Int): Int {
                    if (n < 0) waehrendDesLesens += ordner.listFiles().orEmpty().map { it.name }
                    return n
                }
                override fun read(): Int = super.read().let { if (it < 0) merken(it) else it }
                override fun read(b: ByteArray, off: Int, len: Int): Int = merken(super.read(b, off, len))
            } as InputStream
        }

        val link = ablage.speichern(uri)

        assertTrue(waehrendDesLesens.isNotEmpty(), "Quelle wurde bis zum Ende gelesen")
        waehrendDesLesens.forEach { namen ->
            assertTrue(namen.isNotEmpty(), "Arbeitsdatei liegt im Fotoordner")
            assertTrue(namen.none { it.endsWith(".jpg") }, "keine unfertige .jpg: $namen")
            // eindeutiger Zwischenname <ziel>.jpg.<uuid>.tmp – nie derselbe wie der des Startlaufs
            assertTrue(namen.all { EINDEUTIGER_ZWISCHENNAME.matches(it) }, "Zwischenname: $namen")
        }
        val gespeichert = assertNotNull(ablage.datei(link))
        assertEquals(listOf(gespeichert.name), ordner.listFiles().orEmpty().map { it.name }, "nur das fertige Foto")
        assertEquals(1600, maxOf(groesse(gespeichert).outWidth, groesse(gespeichert).outHeight))
    }

    private companion object {
        val EINDEUTIGER_ZWISCHENNAME = Regex("""[0-9a-f-]{36}\.jpg\.[0-9a-f-]{36}\.tmp""")
    }

    @Test
    fun kleinesFotoBleibt() = runTest {
        val datei = quelle(verlauf(800, 600), 80)
        val link = ablage.speichern(Uri.fromFile(datei))
        val gespeichert = assertNotNull(ablage.datei(link))
        assertContentEquals(datei.readBytes(), gespeichert.readBytes(), "kleines Foto wird nur kopiert")
    }

    /** 1600 px, aber über 600 KB (Rauschen): wird weiter verkleinert, bis es unter die Grenze passt. */
    @Test
    fun rauschenWirdUnter600KBGebracht() = runTest {
        val zufall = Random(7)
        val bmp = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val pixel = IntArray(1600 * 1200) { zufall.nextInt() or 0xFF000000.toInt() }
        bmp.setPixels(pixel, 0, 1600, 0, 0, 1600, 1200)
        val datei = quelle(bmp, 100)
        assertTrue(datei.length() > 600L * 1024, "Vorbedingung: Quelle ist größer als 600 KB")

        val gespeichert = assertNotNull(ablage.datei(ablage.speichern(Uri.fromFile(datei))))

        assertTrue(gespeichert.length() <= 600L * 1024, "${gespeichert.length()} ≤ 600 KB")
        val b = groesse(gespeichert)
        assertTrue(b.outWidth in 1..1600 && b.outHeight in 1..1200, "${b.outWidth}×${b.outHeight}")
    }

    @Test
    fun keinBildWirdAbgelehnt() = runTest {
        val datei = File(context.cacheDir, "kaputt.jpg").also { it.writeText("kein Bild") }
        assertFailsWith<IllegalArgumentException> { ablage.speichern(Uri.fromFile(datei)) }
        assertTrue(ordner.listFiles().orEmpty().isEmpty(), "keine halbe Datei im Fotoordner")
    }

    /** Der Start verkleinert alte Fotos nur einmal (je Verkleinerungs-Version), nicht bei jedem Start erneut. */
    @Test
    fun verkleinerungLaeuftNurEinmal() = runTest {
        ordner.mkdirs()
        fun altesFoto(name: String) = File(ordner, name).also { f ->
            f.outputStream().use { verlauf(3200, 2400).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        val erstes = altesFoto("a.jpg")

        assertEquals(1, ablage.verkleinereAlleEinmal())
        assertEquals(1600, groesse(erstes).outWidth)
        val prefs = context.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)
        assertEquals(1, prefs.getInt(Einstellungsschluessel.FOTOS_VERKLEINERT, 0))

        val zweites = altesFoto("b.jpg")
        assertEquals(0, ablage.verkleinereAlleEinmal(), "zweiter Start liest die Fotos nicht mehr")
        assertEquals(3200, groesse(zweites).outWidth)
    }

    @Test
    fun hashWirdGemerktBisDateiSichAendert() = runTest {
        val datei = quelle(verlauf(400, 300), 80)
        val link = ablage.speichern(Uri.fromFile(datei))
        val gespeichert = assertNotNull(ablage.datei(link))
        val inhalt = gespeichert.readBytes()

        assertEquals(sha256(inhalt), index.hash(link))
        val eintrag = assertNotNull(db.syncDao().fotoLokal(link))
        assertEquals(sha256(inhalt), eintrag.sha256)
        assertEquals(gespeichert.length(), eintrag.size)
        assertEquals(gespeichert.lastModified(), eintrag.modifiedAt)

        // Inhalt ändert sich, Größe und Zeit bleiben: der gemerkte Hash gilt weiter (Datei wird nicht neu gelesen)
        val zeit = gespeichert.lastModified()
        val anders = inhalt.copyOf().also { it[it.size - 3] = (it[it.size - 3] + 1).toByte() }
        gespeichert.writeBytes(anders)
        gespeichert.setLastModified(zeit)
        assertEquals(sha256(inhalt), index.hash(link))

        // Änderungszeit ändert sich: neu hashen und neu merken
        gespeichert.setLastModified(zeit + 5_000)
        assertEquals(sha256(anders), index.hash(link))
        assertEquals(sha256(anders), db.syncDao().fotoLokal(link)?.sha256)

        // Datei weg: kein Hash
        gespeichert.delete()
        assertNull(index.hash(link))
    }

    @Test
    fun galerieLinkIstKeineEigeneDatei() = runTest {
        val galerie = "content://media/external/images/media/1"
        assertFalse(ablage.istEigeneDatei(galerie))
        assertNull(ablage.datei(galerie))
        assertNull(index.hash(galerie))

        // file:-Links außerhalb des Fotoordners – auch per ..-Umweg – zählen nicht
        val eigen = ablage.speichern(Uri.fromFile(quelle(verlauf(100, 100), 80)))
        assertTrue(ablage.istEigeneDatei(eigen))
        val draussen = File(context.filesDir, "draussen.jpg").also { it.writeText("x") }
        assertFalse(ablage.istEigeneDatei(Uri.fromFile(draussen).toString()))
        val umweg = "file://" + ablage.datei(eigen)!!.parent + "/../draussen.jpg"
        assertFalse(ablage.istEigeneDatei(umweg))
        assertNull(index.hash(umweg))
        assertFalse(ablage.istEigeneDatei("kein link"))
        draussen.delete()
    }
}

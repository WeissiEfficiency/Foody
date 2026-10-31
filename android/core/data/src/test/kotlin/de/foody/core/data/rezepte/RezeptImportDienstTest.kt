package de.foody.core.data.rezepte

import android.net.Uri
import androidx.room.Room
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.fotos.FotoAblage
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class RezeptImportDienstTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var dienst: RezeptImportDienst
    private val ordner get() = File(RuntimeEnvironment.getApplication().cacheDir, "import").apply { mkdirs() }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries().build()
        val kontext = RuntimeEnvironment.getApplication()
        dienst = RezeptImportDienst(kontext, db, RezeptRepository(db, FotoAblage(kontext), EmptyCoroutineContext) { 1_000L })
    }

    @After
    fun tearDown() = db.close()

    private fun datei(name: String, text: String): Uri = Uri.fromFile(File(ordner, name).apply { writeText(text) })

    private fun rezept(titel: String, quelle: String, portionen: Int? = 2) = buildString {
        appendLine("# $titel")
        appendLine("_Quelle: ${quelle}_")
        if (portionen != null) appendLine("Portionen: $portionen · Vorbereitung: 10 Min.")
        appendLine("## Zutaten")
        appendLine("400 g")
        appendLine("Reis")
        appendLine("### Für die Soße")
        appendLine("1 Prise")
        appendLine("Salz")
        appendLine("## Zubereitung")
        appendLine("Reis kochen.")
    }

    @Test
    fun importiertMitPortionenAusDatei() = runTest {
        val e = dienst.dateien(listOf(datei("a.md", rezept("Reispfanne", "https://x/1"))))
        val id = e.importiert.single()
        val r = assertNotNull(db.rezeptDao().nachId(id))
        assertEquals(2, r.defaultServings)
        assertEquals("Quelle: https://x/1", r.notes)
        assertEquals("https://x/1", r.sourceUrl)
        assertEquals(listOf("Reis kochen."), db.arbeitsschrittDao().nachRezept(id).map { it.text })
        val salz = db.rezeptZutatDao().nachRezept(id).last()
        assertEquals("Für die Soße · 1 Prise", salz.preparationNote)
    }

    @Test
    fun geschaetztMitHinweis() = runTest {
        val id = dienst.dateien(listOf(datei("b.md", rezept("Reispfanne", "https://x/2", portionen = null)))).importiert.single()
        val notiz = assertNotNull(db.rezeptDao().nachId(id)?.notes)
        assertTrue(notiz.startsWith("Quelle: https://x/2\nPortionenzahl war in der Datei nicht angegeben"), notiz)
    }

    @Test
    fun gleicheQuelleUebersprungen() = runTest {
        val uris = (1..3).map { datei("r$it.md", rezept("Rezept $it", "https://x/r$it")) }
        assertEquals(3, dienst.dateien(uris).importiert.size)
        val zweiter = dienst.dateien(uris)
        assertTrue(zweiter.importiert.isEmpty())
        assertEquals(3, zweiter.uebersprungen)
        assertEquals(3, db.rezeptDao().alle().size)
    }

    @Test
    fun kaputteDateiZaehltAlsFehler() = runTest {
        val gut = datei("gut.md", rezept("Gut", "https://x/gut"))
        val ohneZutaten = datei("leer.md", "# Nur Titel\n## Zubereitung\nNichts.\n")
        val riesig = datei("riesig.md", "x".repeat(1_100_000))
        val e = dienst.dateien(listOf(ohneZutaten, gut, riesig))
        assertEquals(1, e.importiert.size)
        assertEquals(listOf("leer.md", "riesig.md"), e.fehler)
    }

    @Test
    fun tagImport() = runTest {
        val id = dienst.dateien(listOf(datei("c.md", rezept("Tag", "https://x/tag")))).importiert.single()
        assertEquals("Import", db.rezeptDao().nachId(id)?.tags)
    }
}

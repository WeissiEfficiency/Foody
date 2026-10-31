package de.foody.core.data.rezepte

import android.net.Uri
import androidx.room.Room
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.SyncZustandEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.heute.bestand
import de.foody.domain.IngredientCatalog
import de.foody.domain.ZutatZeile
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class RezeptSchreibenTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var ablage: FotoAblage
    private lateinit var repo: RezeptRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries().build()
        ablage = FotoAblage(RuntimeEnvironment.getApplication())
        repo = RezeptRepository(db, ablage, EmptyCoroutineContext) { 1_000L }
    }

    @After
    fun tearDown() = db.close()

    private fun entwurf(vararg zeilen: String, id: String? = null, name: String = "Bowl") = RezeptEntwurf(
        id = id, name = name, portionen = 2, vorbereitung = 10, kochzeit = null, tags = "schnell",
        zeilen = zeilen.map { ZutatZeile.lesen(it)!! }, schritte = listOf("Kochen", "Anrichten"), notizen = null,
        quelle = null, mahlzeiten = null, gaenge = null, bild = null,
    )

    private suspend fun ok(g: Gespeichert) = assertIs<Gespeichert.Ok>(g).id

    @Test
    fun anlegenMitNeuerZutat() = runTest {
        val id = ok(repo.speichern(entwurf("200 g Quinoa")))
        val zutat = assertNotNull(db.zutatDao().nachNameOhneGross("Quinoa"))
        assertEquals(IngredientCatalog.guessCategory("Quinoa"), zutat.category)
        val zeile = db.rezeptZutatDao().nachRezept(id).single()
        assertEquals(zutat.id, zeile.ingredientId)
        assertEquals(listOf("Kochen", "Anrichten"), db.arbeitsschrittDao().nachRezept(id).map { it.text })
        assertEquals(1_000L, db.rezeptDao().nachId(id)?.createdAt)
    }

    @Test
    fun vorhandeneZutatOhneGrossKlein() = runTest {
        db.bestand()
        val id = ok(repo.speichern(entwurf("200 g reis")))
        assertEquals("z-reis", db.rezeptZutatDao().nachRezept(id).single().ingredientId)
    }

    @Test
    fun ohneMengeAlsHinweis() = runTest {
        val id = ok(repo.speichern(entwurf("1 Prise Salz")))
        val z = db.rezeptZutatDao().nachRezept(id).single()
        assertEquals(0, java.math.BigDecimal.ZERO.compareTo(z.amount))
        assertEquals("1 Prise", z.preparationNote)
    }

    @Test
    fun aendernErsetztZeilenBehaeltFavorit() = runTest {
        db.bestand()
        repo.favorit("r1", true)
        val e = assertNotNull(repo.entwurf("r1"))
        ok(repo.speichern(e.copy(zeilen = listOf(ZutatZeile.lesen("300 g Reis")!!, ZutatZeile.lesen("1 Prise Salz")!!))))
        assertEquals(2, db.rezeptZutatDao().nachRezept("r1").size)
        val r = assertNotNull(db.rezeptDao().nachId("r1"))
        assertTrue(r.favorite)
        assertEquals(3, r.version, "Favorit und Ändern zählen je eine Version")
    }

    @Test
    fun duplizierenOhneQuelleUndFavorit() = runTest {
        db.bestand()
        db.rezeptDao().upsert(assertNotNull(db.rezeptDao().nachId("r1")).copy(sourceUrl = "https://quelle", favorite = true))
        val kopie = assertNotNull(repo.duplizieren("r1"))
        assertNotEquals("r1", kopie)
        val k = assertNotNull(db.rezeptDao().nachId(kopie))
        assertEquals("Reispfanne (Kopie)", k.name)
        assertNull(k.sourceUrl)
        assertFalse(k.favorite)
        assertEquals(1, db.rezeptDao().nachQuelle("https://quelle").size)
        assertTrue(db.rezeptZutatDao().nachRezept(kopie).none { it.id == "l1" })
    }

    @Test
    fun archivierenUndWiederherstellen() = runTest {
        db.bestand()
        repo.archivieren("r1", true)
        assertEquals(1_000L, db.rezeptDao().nachId("r1")?.archivedAt)
        repo.archivieren("r1", false)
        assertNull(db.rezeptDao().nachId("r1")?.archivedAt)
    }

    @Test
    fun geloeschtWaehrendBearbeitung() = runTest {
        db.bestand()
        val e = assertNotNull(repo.entwurf("r1"))
        db.rezeptDao().loeschen("r1")
        assertEquals(Gespeichert.Geloescht, repo.speichern(e))
        assertTrue(db.rezeptDao().alle().isEmpty())
    }

    @Test
    fun beiAktivemSyncOutbox() = runTest {
        db.syncDao().upsertZustand(SyncZustandEntity(active = true, serverUrl = "https://foody.example"))
        val id = ok(repo.speichern(entwurf("200 g Quinoa")))
        assertTrue(db.syncDao().outbox().any { it.type == "recipe" && it.recordId == id })
    }

    @Test
    fun fotoErsetzenRaeumtAuf() = runTest {
        db.bestand()
        // Direkt im Fotoordner anlegen: speichern() dekodiert, das kann Robolectric unter Windows nicht
        val ordner = File(RuntimeEnvironment.getApplication().filesDir, "recipe_images").apply { mkdirs() }
        val alt = Uri.fromFile(File(ordner, "alt.jpg").apply { writeBytes(ByteArray(10)) }).toString()
        val neu = Uri.fromFile(File(ordner, "neu.jpg").apply { writeBytes(ByteArray(10)) }).toString()
        repo.fotoSetzen("r1", alt)
        repo.fotoSetzen("r1", neu)
        assertNull(ablage.datei(alt)?.takeIf { it.exists() }, "altes Foto ist weg")
        assertTrue(ablage.datei(neu)!!.exists())
        assertEquals(neu, db.rezeptDao().nachId("r1")?.imageUri)
    }

    @Test
    fun entwurfRundweg() = runTest {
        // Der Katalog vereinheitlicht Namen („Zwiebeln“ → „Zwiebel“): nach dem ersten Speichern bleibt alles stabil
        val id = ok(repo.speichern(entwurf("200 g Quinoa", "1 Prise Salz", "2 große Zwiebeln")))
        val einmal = assertNotNull(repo.entwurf(id))
        ok(repo.speichern(einmal))
        assertEquals(einmal.zeilen, assertNotNull(repo.entwurf(id)).zeilen)
        assertEquals(listOf("Quinoa", "Salz"), einmal.zeilen.take(2).map { it.name })
    }
}

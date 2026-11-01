package de.foody.core.data.woche

import androidx.room.Room
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.SyncZustandEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.heute.bestand
import de.foody.core.data.rezepte.RezeptRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class WocheSchreibenTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var repo: WocheRepository
    private val tag = LocalDate.of(2026, 10, 26)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries().build()
        val rezepte = RezeptRepository(db, FotoAblage(RuntimeEnvironment.getApplication()), EmptyCoroutineContext) { 1L }
        repo = WocheRepository(db, rezepte, { 5_000L }, EmptyCoroutineContext)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun eintrag(id: String) = assertNotNull(db.planEintragDao().nachId(id))

    @Test
    fun hinzufuegenMitPortionen() = runTest {
        db.bestand()
        val id = assertNotNull(repo.hinzufuegen(tag, "ABENDESSEN", "r1", 3))
        val e = eintrag(id)
        assertEquals(tag, e.date)
        assertEquals("ABENDESSEN", e.slotType)
        assertEquals(3, e.servings)
        assertEquals(5_000L, e.createdAt)
    }

    @Test
    fun hinzufuegenOhnePortionenNimmtDieDesRezepts() = runTest {
        db.bestand()
        val id = assertNotNull(repo.hinzufuegen(tag, "ABENDESSEN", "r1", null))
        assertEquals(2, eintrag(id).servings)
    }

    @Test
    fun portionenBegrenzt() = runTest {
        db.bestand()
        val id = assertNotNull(repo.hinzufuegen(tag, "ABENDESSEN", "r1", 2))
        repo.portionen(id, 0)
        assertEquals(1, eintrag(id).servings)
        repo.portionen(id, 120)
        assertEquals(99, eintrag(id).servings)
    }

    @Test
    fun verschieben() = runTest {
        db.bestand()
        val id = assertNotNull(repo.hinzufuegen(tag, "ABENDESSEN", "r1", 2))
        repo.verschieben(id, tag.plusDays(2))
        assertEquals(tag.plusDays(2), eintrag(id).date)
    }

    @Test
    fun umbenennenEigenerName() = runTest {
        db.bestand()
        val id = assertNotNull(repo.hinzufuegen(tag, "ABENDESSEN", "r1", 2))
        repo.umbenennen(id, "  Brotzeit am See ")
        assertEquals("Brotzeit am See", eintrag(id).slotType)
    }

    @Test
    fun bekannterNameWirdMahlzeit() = runTest {
        db.bestand()
        val id = assertNotNull(repo.hinzufuegen(tag, "abendessen ", "r1", 2))
        assertEquals("ABENDESSEN", eintrag(id).slotType)
        repo.umbenennen(id, "Frühstück")
        assertEquals("FRUEHSTUECK", eintrag(id).slotType)
    }

    @Test
    fun umbenennenLeerAbgelehnt() = runTest {
        db.bestand()
        val id = assertNotNull(repo.hinzufuegen(tag, "ABENDESSEN", "r1", 2))
        assertFailsWith<IllegalArgumentException> { repo.umbenennen(id, "   ") }
        assertFailsWith<IllegalArgumentException> { repo.umbenennen(id, "x".repeat(31)) }
        assertEquals("ABENDESSEN", eintrag(id).slotType)
    }

    @Test
    fun entfernenUndWiederherstellenMitDerselbenId() = runTest {
        db.bestand()
        val id = assertNotNull(repo.hinzufuegen(tag, "ABENDESSEN", "r1", 2))
        val weg = assertNotNull(repo.entfernen(id))
        assertNull(db.planEintragDao().nachId(id))
        repo.wiederherstellen(weg)
        assertEquals(id, eintrag(id).id)
    }

    @Test
    fun wiederherstellenOhneRezeptStill() = runTest {
        // Rückgängig, nachdem ein Sync das Rezept gelöscht hat: nichts anlegen, nicht abstürzen
        db.bestand()
        val id = assertNotNull(repo.hinzufuegen(tag, "ABENDESSEN", "r1", 2))
        val weg = assertNotNull(repo.entfernen(id))
        db.rezeptDao().loeschen("r1")
        repo.wiederherstellen(weg)
        assertNull(db.planEintragDao().nachId(id))
    }

    @Test
    fun hinzufuegenOhneRezeptStill() = runTest {
        assertNull(repo.hinzufuegen(tag, "ABENDESSEN", "gibt-es-nicht", 2))
        assertTrue(db.planEintragDao().alle().isEmpty())
    }

    @Test
    fun gekochtAnUndAus() = runTest {
        db.bestand()
        val id = assertNotNull(repo.hinzufuegen(tag, "ABENDESSEN", "r1", 2))
        repo.gekocht(id, true)
        assertEquals(5_000L, eintrag(id).cookedAt)
        repo.gekocht(id, false)
        assertNull(eintrag(id).cookedAt)
    }

    @Test
    fun aenderungenInDerOutbox() = runTest {
        db.syncDao().upsertZustand(SyncZustandEntity(active = true, serverUrl = "https://foody.example"))
        db.bestand()
        val id = assertNotNull(repo.hinzufuegen(tag, "ABENDESSEN", "r1", 2))
        db.syncDao().outboxLeeren()
        repo.portionen(id, 3)
        assertTrue(db.syncDao().outbox().any { it.type == "meal_slot" && it.recordId == id })
    }

    @Test
    fun updatedAtGesetzt() = runTest {
        db.bestand()
        val id = assertNotNull(repo.hinzufuegen(tag, "ABENDESSEN", "r1", 2))
        repo.verschieben(id, tag.plusDays(1))
        assertEquals(5_000L, eintrag(id).updatedAt)
    }
}

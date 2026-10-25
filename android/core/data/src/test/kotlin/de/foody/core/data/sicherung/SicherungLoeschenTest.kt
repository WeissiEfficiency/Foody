package de.foody.core.data.sicherung

import android.content.Context
import androidx.room.Room
import de.foody.core.data.Einstellungsschluessel
import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufsartikelQuelleEntity
import de.foody.core.data.db.EinkaufslisteEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.PlanEintragEntity
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.SyncFotoLokalEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.fotos.FotoIndex
import de.foody.core.data.startdaten.Startdaten
import de.foody.core.data.startdaten.StartdatenLader
import de.foody.core.data.sync.SyncSpeicher
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** „Alle Daten löschen“: Fachtabellen leer, eigene Fotos weg, Startkatalog neu, Einstellungen bleiben, bei Sync gesperrt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class SicherungLoeschenTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)
    private lateinit var db: FoodyDatenbank
    private lateinit var ablage: FotoAblage
    private lateinit var sync: SyncSpeicher
    private lateinit var sicherung: Sicherung
    private lateinit var foto: String
    private val ordner get() = File(context.filesDir, Einstellungsschluessel.FOTO_ORDNER)

    @Before
    fun setUp() = runTest {
        ordner.deleteRecursively()
        prefs.edit().clear().putInt(Einstellungsschluessel.TAGESZIEL, 2000).putInt("seedVersion", Startdaten.VERSION).commit()
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatenbank::class.java).addCallback(FoodyDatenbank.CALLBACK)
            .allowMainThreadQueries().build()
        ablage = FotoAblage(context)
        sync = SyncSpeicher(db, ablage, FotoIndex(db.syncDao(), ablage))
        sicherung = Sicherung(context, db, ablage, sync, StartdatenLader(db, context))
        foto = ablage.ablegen(byteArrayOf(1, 2, 3))
        db.syncDao().upsertFotoLokal(SyncFotoLokalEntity(foto, "abc", 3, 1))
        bestand()
    }

    @After
    fun tearDown() {
        db.close()
        ordner.deleteRecursively()
    }

    private suspend fun bestand() {
        db.zutatDao().upsert(ZutatEntity(id = "eigen", canonicalName = "Meine Würzmischung", createdAt = 1, updatedAt = 1))
        db.rezeptDao().upsert(
            RezeptEntity(
                id = "r1", name = "Reispfanne", defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = foto,
                notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
            ),
        )
        db.rezeptZutatDao().upsert(RezeptZutatEntity("z1", "r1", "eigen", BigDecimal("10"), MeasureUnit.GRAM, 0, null, false))
        db.planEintragDao().upsert(PlanEintragEntity("p1", LocalDate.of(2026, 10, 28), "ABENDESSEN", "r1", 2, createdAt = 1, updatedAt = 1))
        db.einkaufslisteDao().upsert(EinkaufslisteEntity(id = "l1", name = "Einkauf", createdAt = 1, updatedAt = 1))
        db.einkaufsartikelDao().upsert(EinkaufsartikelEntity(id = "a1", listId = "l1", name = "Reis", sortOrder = 0))
        db.einkaufsartikelQuelleDao().upsert(
            EinkaufsartikelQuelleEntity("q1", "a1", "p1", "z1", "Reispfanne", LocalDate.of(2026, 10, 28), BigDecimal("10"), MeasureUnit.GRAM),
        )
    }

    private fun anzahl(tabelle: String) = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tabelle").use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun allesLeerAusserStartkatalog() = runTest {
        assertEquals(LoeschErgebnis.Fertig, sicherung.allesLoeschen())
        for (t in listOf("recipe", "recipe_ingredient", "instruction_step", "meal_slot", "shopping_list", "shopping_item", "shopping_item_source", "tagebuch_eintrag")) {
            assertEquals(0, anzahl(t), t)
        }
        assertNull(db.zutatDao().nachId("eigen"))
        assertNotNull(db.zutatDao().nachId("seed-reis"))
    }

    @Test
    fun eigeneFotosEntfernt() = runTest {
        val datei = assertNotNull(ablage.datei(foto))
        assertTrue(datei.exists())
        sicherung.allesLoeschen()
        assertFalse(datei.exists())
    }

    @Test
    fun fotoindexBereinigt() = runTest {
        assertNotNull(db.syncDao().fotoLokal(foto))
        sicherung.allesLoeschen()
        assertNull(db.syncDao().fotoLokal(foto))
    }

    @Test
    fun prefsBleiben() = runTest {
        sicherung.allesLoeschen()
        assertEquals(2000, prefs.getInt(Einstellungsschluessel.TAGESZIEL, 0))
    }

    @Test
    fun syncAktivNichtsGeaendert() = runTest {
        sync.aktivieren("https://foody.example", "konto", allesHochladen = false)
        assertEquals(LoeschErgebnis.SyncAktiv, sicherung.allesLoeschen())
        assertNotNull(db.rezeptDao().nachId("r1"))
        assertTrue(assertNotNull(ablage.datei(foto)).exists())
    }

    @Test
    fun startdatenVersionWiederGesetzt() = runTest {
        sicherung.allesLoeschen()
        assertEquals(Startdaten.VERSION, prefs.getInt("seedVersion", 0))
    }

    @Test
    fun abbruchNachDemCommitRaeumtTrotzdemAuf() = runBlocking<Unit> {
        // Seite verlassen, während das Löschen läuft: Der Abbruch kommt mitten in der Transaktion (beim ersten DELETE).
        // Die Transaktion wird festgeschrieben; Fotos und Startkatalog müssen trotzdem folgen – sonst bleiben Fotos
        // liegen und der Katalog bis zum nächsten Start leer.
        val auftrag = AtomicReference<Job?>(null)
        db.close()
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatenbank::class.java).addCallback(FoodyDatenbank.CALLBACK)
            .allowMainThreadQueries()
            .setQueryCallback({ sql, _ -> if (sql.startsWith("DELETE FROM tagebuch_eintrag")) auftrag.get()?.cancel() }) { it.run() }
            .build()
        sync = SyncSpeicher(db, ablage, FotoIndex(db.syncDao(), ablage))
        sicherung = Sicherung(context, db, ablage, sync, StartdatenLader(db, context))
        db.syncDao().upsertFotoLokal(SyncFotoLokalEntity(foto, "abc", 3, 1))
        bestand()
        val datei = assertNotNull(ablage.datei(foto))

        val job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) { sicherung.allesLoeschen() }
        auftrag.set(job)
        job.start()
        job.join()

        assertTrue(job.isCancelled)
        assertNull(db.rezeptDao().nachId("r1"))
        assertFalse(datei.exists())
        assertNotNull(db.zutatDao().nachId("seed-reis"))
    }
}

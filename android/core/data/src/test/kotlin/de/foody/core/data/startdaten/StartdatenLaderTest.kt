package de.foody.core.data.startdaten

import android.content.Context
import androidx.room.Room
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.SyncZustandEntity
import de.foody.core.data.db.ZutatEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class StartdatenLaderTest {
    private lateinit var db: FoodyDatenbank
    private val kontext: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = kontext.getSharedPreferences("foody", Context.MODE_PRIVATE)
    private val lader get() = StartdatenLader(db, kontext)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(kontext, FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK)
            .allowMainThreadQueries()
            .build()
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun leereDatenbankBekommtStartzutaten() = runTest {
        val n = lader.ladenWennNoetig()
        assertEquals(Startdaten.zutaten(0).size, n)
        assertEquals(Startdaten.zutaten(0).size, db.zutatDao().alle().size)
        assertEquals(Startdaten.VERSION, prefs.getInt("seedVersion", 0))
    }

    @Test
    fun zweiterAufrufTutNichts() = runTest {
        lader.ladenWennNoetig()
        assertEquals(0, lader.ladenWennNoetig())
    }

    @Test
    fun vEinsMitVersionAchtTutNichts() = runTest {
        prefs.edit().putInt("seedVersion", 8).commit()
        assertEquals(0, lader.ladenWennNoetig())
        assertTrue(db.zutatDao().alle().isEmpty())
    }

    @Test
    fun altesSeededGiltAlsEins() = runTest {
        prefs.edit().putBoolean("seeded", true).commit()
        assertTrue(lader.ladenWennNoetig() > 0)
        assertEquals(Startdaten.VERSION, prefs.getInt("seedVersion", 0))
    }

    @Test
    fun eigeneGleichnamigeZutatWirdNurErgaenzt() = runTest {
        db.zutatDao().upsert(ZutatEntity(id = "eigen-butter", canonicalName = "Butter", createdAt = 1, updatedAt = 1))
        lader.ladenWennNoetig()
        val butter = assertNotNull(db.zutatDao().nachId("eigen-butter"))
        assertEquals(BigDecimal("3050"), butter.energyKj)
        assertEquals(null, db.zutatDao().nachId("seed-butter"))
    }

    @Test
    fun geaenderteStartzutatBleibt() = runTest {
        val reis = Startdaten.zutaten(1).first { it.id == "seed-reis" }
        db.zutatDao().upsert(reis.copy(energyKj = BigDecimal("1234"), nutrientSource = "eigene"))
        lader.ladenWennNoetig()
        assertEquals(BigDecimal("1234"), db.zutatDao().nachId("seed-reis")?.energyKj)
    }

    @Test
    fun beiAktivemSyncLandenStartdatenInDerOutbox() = runTest {
        db.syncDao().upsertZustand(SyncZustandEntity(active = true, serverUrl = "https://foody.example"))
        lader.ladenWennNoetig()
        val vorgemerkt = db.syncDao().outbox().map { it.recordId }.toSet()
        assertTrue("seed-reis" in vorgemerkt && "seed-butter" in vorgemerkt)
    }
}

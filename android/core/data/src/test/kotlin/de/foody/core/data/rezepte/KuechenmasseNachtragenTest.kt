package de.foody.core.data.rezepte

import androidx.room.Room
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.SyncZustandEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.heute.bestand
import de.foody.domain.MeasureUnit
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
class KuechenmasseNachtragenTest {
    private lateinit var db: FoodyDatenbank

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun alteZeilen() {
        db.bestand()
        db.zutatDao().upsert(ZutatEntity(id = "z-salz", canonicalName = "Salz", createdAt = 1, updatedAt = 1))
        db.rezeptZutatDao().upsert(RezeptZutatEntity("l-prise", "r1", "z-salz", BigDecimal.ZERO, MeasureUnit.PIECE, 5, "Für die Soße · 1 Prise(n)", false))
        db.rezeptZutatDao().upsert(RezeptZutatEntity("l-msp", "r1", "z-salz", BigDecimal("2"), MeasureUnit.PIECE, 6, "Msp.", false))
        db.rezeptZutatDao().upsert(RezeptZutatEntity("l-etwas", "r1", "z-salz", BigDecimal.ZERO, MeasureUnit.PIECE, 7, "etwas", false))
    }

    @Test
    fun prisenUndMesserspitzenWerdenGramm() = runTest {
        alteZeilen()
        assertEquals(2, KuechenmasseNachtragen(db).nachtragen())
        val prise = assertNotNull(db.rezeptZutatDao().nachId("l-prise"))
        assertEquals(0, BigDecimal("0.5").compareTo(prise.amount))
        assertEquals(MeasureUnit.GRAM, prise.unit)
        assertEquals("Für die Soße · 1 Prise(n)", prise.preparationNote)
        val msp = assertNotNull(db.rezeptZutatDao().nachId("l-msp"))
        assertEquals(0, BigDecimal("1").compareTo(msp.amount))
        assertEquals("2 Msp.", msp.preparationNote)
        // „etwas“ bleibt nach Bedarf
        assertEquals(0, BigDecimal.ZERO.compareTo(db.rezeptZutatDao().nachId("l-etwas")?.amount))
    }

    @Test
    fun zweiterLaufTutNichts() = runTest {
        alteZeilen()
        KuechenmasseNachtragen(db).nachtragen()
        assertEquals(0, KuechenmasseNachtragen(db).nachtragen())
    }

    @Test
    fun beiAktivemSyncWirdDasRezeptVorgemerkt() = runTest {
        alteZeilen()
        db.syncDao().upsertZustand(SyncZustandEntity(active = true, serverUrl = "https://foody.example"))
        assertTrue("r1" !in db.syncDao().outbox().map { it.recordId })
        KuechenmasseNachtragen(db).nachtragen()
        assertTrue("r1" in db.syncDao().outbox().map { it.recordId })
    }
}

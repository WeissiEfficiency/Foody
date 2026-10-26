package de.foody.core.data.zutaten

import android.content.Context
import androidx.room.Room
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.domain.IngredientCatalog
import de.foody.domain.MeasureUnit
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lesen des Zutatenkatalogs: kcal, Verwendungen, eigen, Sortierung, Rezeptnamen, Synonyme. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class ZutatenRepositoryLesenTest {
    private val kontext: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: FoodyDatenbank
    private lateinit var repo: ZutatenRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(kontext, FoodyDatenbank::class.java).addCallback(FoodyDatenbank.CALLBACK)
            .allowMainThreadQueries().build()
        repo = ZutatenRepository(db) { 10L }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun zutat(id: String, name: String, kj: String? = null) =
        db.zutatDao().upsert(ZutatEntity(id = id, canonicalName = name, energyKj = kj?.let(::BigDecimal), createdAt = 1, updatedAt = 1))

    private suspend fun rezept(id: String, name: String, vararg zutaten: String) {
        db.rezeptDao().upsert(
            RezeptEntity(
                id = id, name = name, defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = null,
                notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
            ),
        )
        zutaten.forEachIndexed { i, z ->
            db.rezeptZutatDao().upsert(RezeptZutatEntity("$id-$i", id, z, BigDecimal.ONE, MeasureUnit.GRAM, i, null, false))
        }
    }

    @Test
    fun listeMitKcalUndRezepten() = runTest {
        zutat("seed-reis", "Reis", "1502")
        zutat("eigen-1", "Würze")
        rezept("r1", "Reispfanne", "seed-reis", "seed-reis")
        rezept("r2", "Risotto", "seed-reis")
        val liste = repo.liste().first().associateBy { it.id }
        assertEquals(359, liste.getValue("seed-reis").kcal)
        assertEquals(2, liste.getValue("seed-reis").rezepte)
        assertNull(liste.getValue("eigen-1").kcal)
        assertEquals(0, liste.getValue("eigen-1").rezepte)
    }

    @Test
    fun eigenNachId() = runTest {
        zutat("seed-reis", "Reis")
        zutat("eigen-1", "Würze")
        val liste = repo.liste().first().associateBy { it.id }
        assertEquals(false, liste.getValue("seed-reis").eigen)
        assertEquals(true, liste.getValue("eigen-1").eigen)
    }

    @Test
    fun sortiertDeutsch() = runTest {
        zutat("a", "Zucker")
        zutat("b", "äpfel")
        zutat("c", "Birne")
        assertEquals(listOf("äpfel", "Birne", "Zucker"), repo.liste().first().map { it.name })
    }

    @Test
    fun anzahlZaehlt() = runTest {
        zutat("a", "Zucker")
        zutat("b", "Birne")
        assertEquals(2, repo.anzahl().first())
    }

    @Test
    fun verwendetInSortiert() = runTest {
        zutat("z", "Reis")
        rezept("r1", "Reispfanne", "z")
        rezept("r2", "curry", "z", "z")
        assertEquals(listOf("curry", "Reispfanne"), repo.verwendetIn("z").first())
    }

    @Test
    fun synonymeOhneEigenenNamen() {
        val name = IngredientCatalog.canonicalName("Zwiebeln")
        val s = repo.synonyme(name)
        assertTrue(s.none { it.equals(name, ignoreCase = true) })
        assertEquals(IngredientCatalog.synonymsOf(name).filterNot { it.equals(name, ignoreCase = true) }, s)
    }
}

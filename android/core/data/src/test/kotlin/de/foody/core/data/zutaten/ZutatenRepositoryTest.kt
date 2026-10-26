package de.foody.core.data.zutaten

import android.content.Context
import androidx.room.Room
import de.foody.api.protocol.RecordType
import de.foody.core.data.db.EinkaufsartikelEntity
import de.foody.core.data.db.EinkaufslisteEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.SyncZustandEntity
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.domain.IngredientCatalog
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import de.foody.domain.NutritionResult
import de.foody.domain.TagebuchArt
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Schreiben im Zutatenkatalog: anlegen, speichern (nur geänderte Felder), löschen, zusammenführen, vereinheitlichen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class ZutatenRepositoryTest {
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

    private suspend fun zutat(id: String, name: String, aendern: (ZutatEntity) -> ZutatEntity = { it }): ZutatEntity {
        val z = aendern(ZutatEntity(id = id, canonicalName = name, createdAt = 1, updatedAt = 1))
        db.zutatDao().upsert(z)
        return z
    }

    private suspend fun rezept(id: String, zutatId: String) {
        db.rezeptDao().upsert(
            RezeptEntity(
                id = id, name = "Rezept $id", defaultServings = 2, prepMinutes = null, cookMinutes = null, imageUri = null,
                notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
            ),
        )
        db.rezeptZutatDao().upsert(RezeptZutatEntity("$id-l", id, zutatId, BigDecimal.ONE, MeasureUnit.GRAM, 0, null, false))
    }

    private suspend fun gespeichert(id: String) = assertNotNull(db.zutatDao().nachId(id))

    private suspend fun syncAn() = db.syncDao().upsertZustand(SyncZustandEntity(active = true, serverUrl = "https://foody.example"))

    // --- anlegen ---

    @Test
    fun anlegenRaetKategorie() = runTest {
        val e = assertIs<ZutatErgebnis.Gespeichert>(repo.anlegen("  Paprika "))
        val z = gespeichert(e.id)
        assertEquals("Paprika", z.canonicalName)
        assertEquals(IngredientCatalog.guessCategory("Paprika"), z.category)
        assertFalse(z.id.startsWith("seed-"))
    }

    @Test
    fun anlegenNameVergeben() = runTest {
        zutat("seed-reis", "Reis")
        assertEquals(ZutatErgebnis.NameVergeben("seed-reis", "Reis"), repo.anlegen("reis"))
        assertEquals(1, db.zutatDao().alle().size)
    }

    // --- speichern ---

    @Test
    fun speichernNurGeaenderteFelder() = runTest {
        val z = zutat("seed-reis", "Reis") { it.copy(nutrientBasis = NutrientBasis.PER_100_G, energyKj = BigDecimal("1502")) }
        val vorher = ZutatEntwurf.aus(z)
        assertEquals(ZutatErgebnis.Gespeichert("seed-reis"), repo.speichern("seed-reis", vorher, vorher.copy(dichte = "0,85")))
        val neu = gespeichert("seed-reis")
        assertEquals(BigDecimal("0.85"), neu.densityGPerMl)
        assertEquals(BigDecimal("1502"), neu.energyKj)
        assertEquals(10L, neu.updatedAt)
    }

    @Test
    fun syncStandBleibt() = runTest {
        val z = zutat("z", "Reis")
        val vorher = ZutatEntwurf.aus(z)
        db.zutatDao().upsert(z.copy(pieceWeightG = BigDecimal("50"))) // kam per Sync, während der Editor offen war
        repo.speichern("z", vorher, vorher.copy(dichte = "1"))
        assertEquals(BigDecimal("50"), gespeichert("z").pieceWeightG)
    }

    @Test
    fun kcalGeaendertWirdKj() = runTest {
        val vorher = ZutatEntwurf.aus(zutat("z", "Würze"))
        repo.speichern("z", vorher, vorher.copy(kcal = "100"))
        val z = gespeichert("z")
        assertEquals(0, z.energyKj?.compareTo(BigDecimal("418.4")))
        assertEquals(NutrientBasis.PER_100_G, z.nutrientBasis)
        assertEquals("Eigene Angabe", z.nutrientSource)
    }

    @Test
    fun quelleVomNutzerBleibt() = runTest {
        val vorher = ZutatEntwurf.aus(zutat("z", "Würze"))
        repo.speichern("z", vorher, vorher.copy(kcal = "100", quelle = "Packung"))
        assertEquals("Packung", gespeichert("z").nutrientSource)
    }

    @Test
    fun alleNaehrwerteLeer() = runTest {
        val z = zutat("z", "Würze") {
            it.copy(nutrientBasis = NutrientBasis.PER_100_G, energyKj = BigDecimal("100"), protein = BigDecimal("1"), nutrientSource = "BLS")
        }
        val vorher = ZutatEntwurf.aus(z)
        repo.speichern("z", vorher, vorher.copy(kcal = "", eiweiss = ""))
        val neu = gespeichert("z")
        assertNull(neu.energyKj)
        assertNull(neu.protein)
        assertNull(neu.nutrientBasis)
        assertNull(neu.nutrientSource)
    }

    @Test
    fun eigenenNamenUmschreiben() = runTest {
        val vorher = ZutatEntwurf.aus(zutat("z", "reis"))
        assertEquals(ZutatErgebnis.Gespeichert("z"), repo.speichern("z", vorher, vorher.copy(name = "Reis")))
        assertEquals("Reis", gespeichert("z").canonicalName)
    }

    @Test
    fun nameVergebenAuchUmlaute() = runTest {
        zutat("a", "Äpfel")
        val vorher = ZutatEntwurf.aus(zutat("b", "Birne"))
        assertEquals(ZutatErgebnis.NameVergeben("a", "Äpfel"), repo.speichern("b", vorher, vorher.copy(name = "äpfel")))
        assertEquals("Birne", gespeichert("b").canonicalName)
    }

    @Test
    fun strichcodeVergeben() = runTest {
        zutat("a", "Saft") { it.copy(barcode = "4001234567890") }
        val vorher = ZutatEntwurf.aus(zutat("b", "Milch"))
        assertEquals(ZutatErgebnis.StrichcodeVergeben("a", "Saft"), repo.speichern("b", vorher, vorher.copy(strichcode = "4001234567890")))
        assertNull(gespeichert("b").barcode)
    }

    @Test
    fun ungueltigSpeichertNichts() = runTest {
        val vorher = ZutatEntwurf.aus(zutat("z", "Würze"))
        val e = assertIs<ZutatErgebnis.Ungueltig>(repo.speichern("z", vorher, vorher.copy(kcal = "5000", dichte = "1")))
        assertEquals(setOf(ZutatFeld.KCAL), e.fehler.keys)
        assertNull(gespeichert("z").densityGPerMl)
    }

    @Test
    fun zeileWegNichtGefunden() = runTest {
        val vorher = ZutatEntwurf.aus(ZutatEntity(id = "weg", canonicalName = "Weg", createdAt = 1, updatedAt = 1))
        assertEquals(ZutatErgebnis.NichtGefunden, repo.speichern("weg", vorher, vorher.copy(dichte = "1")))
    }

    // --- löschen ---

    @Test
    fun loeschenUnbenutzt() = runTest {
        zutat("z", "Würze")
        assertEquals(ZutatErgebnis.Geloescht, repo.loeschen("z"))
        assertNull(db.zutatDao().nachId("z"))
    }

    @Test
    fun loeschenBenutzt() = runTest {
        zutat("z", "Würze")
        rezept("r1", "z")
        rezept("r2", "z")
        assertEquals(ZutatErgebnis.InVerwendung(2), repo.loeschen("z"))
        gespeichert("z")
    }

    // --- zusammenführen ---

    @Test
    fun zusammenfuehrenHaengtAllesUm() = runTest {
        syncAn()
        zutat("alt", "Zwiebeln") { it.copy(densityGPerMl = BigDecimal("0.9")) }
        zutat("neu", "Zwiebel")
        rezept("r1", "alt")
        db.einkaufslisteDao().upsert(EinkaufslisteEntity("l1", "Woche", createdAt = 1, updatedAt = 1))
        db.einkaufsartikelDao().upsert(EinkaufsartikelEntity(id = "a1", listId = "l1", ingredientId = "alt", name = "Zwiebeln", sortOrder = 0))
        db.tagebuchDao().upsert(
            TagebuchEintragEntity(
                "t1", LocalDate.of(2026, 10, 26), "ABENDESSEN", TagebuchArt.ZUTAT, "Zwiebeln", zutatId = "alt",
                createdAt = 1, updatedAt = 1,
            ),
        )
        db.syncDao().outbox().forEach { db.syncDao().loeschenOutbox(it.type, it.recordId) }

        assertEquals(ZutatErgebnis.Zusammengefuehrt("neu", "Zwiebel"), repo.zusammenfuehren("alt", "neu"))

        assertEquals("neu", db.rezeptZutatDao().nachRezept("r1").single().ingredientId)
        assertEquals("neu", db.einkaufsartikelDao().nachId("a1")?.ingredientId)
        assertEquals("neu", db.tagebuchDao().nachId("t1")?.zutatId)
        assertEquals(BigDecimal("0.9"), gespeichert("neu").densityGPerMl)
        assertNull(db.zutatDao().nachId("alt"))
        val outbox = db.syncDao().outbox().associate { (it.type to it.recordId) to it.deleted }
        assertEquals(false, outbox[RecordType.RECIPE.wire to "r1"])
        assertEquals(true, outbox[RecordType.INGREDIENT.wire to "alt"])
    }

    @Test
    fun zusammenfuehrenMitEntwurf() = runTest {
        val vorher = ZutatEntwurf.aus(zutat("alt", "Zwiebeln"))
        zutat("neu", "Zwiebel")
        repo.zusammenfuehren("alt", "neu", vorher to vorher.copy(kcal = "200", name = "Zwiebel"))
        assertEquals(0, gespeichert("neu").energyKj?.compareTo(NutritionResult.kcalToKj(BigDecimal(200))))
        assertNull(db.zutatDao().nachId("alt"))
    }

    // --- vereinheitlichen ---

    @Test
    fun vereinheitlichenWieVorschau() = runTest {
        val c = IngredientCatalog.canonicalName("Zwiebeln")
        zutat("a", "Zwiebeln") { it.copy(category = "Obst & Gemüse") }
        zutat("b", "zwiebeln") { it.copy(category = "Obst & Gemüse") }
        rezept("r1", "b")
        val vorschau = repo.vereinheitlichenVorschau()
        assertEquals(2, vorschau.size)
        assertEquals(vorschau.size, repo.vereinheitlichen())
        assertEquals(listOf("a" to c), db.zutatDao().alle().map { it.id to it.canonicalName })
        assertEquals("a", db.rezeptZutatDao().nachRezept("r1").single().ingredientId)
        assertTrue(repo.vereinheitlichenVorschau().isEmpty())
    }

    @Test
    fun zusammenfuehrenGleicheOderFehlendeIds() = runTest {
        zutat("a", "Zwiebel")
        assertEquals(ZutatErgebnis.NichtGefunden, repo.zusammenfuehren("a", "a"))
        assertEquals(ZutatErgebnis.NichtGefunden, repo.zusammenfuehren("a", "fehlt"))
        assertEquals(ZutatErgebnis.NichtGefunden, repo.zusammenfuehren("fehlt", "a"))
        assertTrue(db.zutatDao().nachId("a") != null)
    }
}

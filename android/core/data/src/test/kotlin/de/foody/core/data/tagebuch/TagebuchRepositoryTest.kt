package de.foody.core.data.tagebuch

import androidx.room.Room
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.SyncZustandEntity
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.heute.bestand
import de.foody.core.data.heute.plan
import de.foody.core.data.zutaten.Packungswerte
import de.foody.domain.IngredientCatalog
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.Naehrwerte
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import de.foody.domain.NutritionResult
import de.foody.domain.TagebuchArt
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
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class TagebuchRepositoryTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var repo: TagebuchRepository
    private val heute = LocalDate.of(2026, 10, 24)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FoodyDatenbank::class.java)
            .addCallback(FoodyDatenbank.CALLBACK).allowMainThreadQueries().build()
        repo = TagebuchRepository(db) { 1_000L }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun kj(id: String) = db.tagebuchDao().nachId(id)?.energieKj

    @Test
    fun ausRezeptHaeltWerteFest() = runTest {
        db.bestand()
        val id = assertNotNull(repo.ausRezept(heute, Mahlzeit.ABENDESSEN, "r1", BigDecimal.ONE))
        val vorher = kj(id)
        assertEquals(0, BigDecimal("3000").compareTo(vorher))
        db.rezeptZutatDao().upsert(RezeptZutatEntity("l1", "r1", "z-reis", BigDecimal("800"), MeasureUnit.GRAM, 0, null, false))
        assertEquals(vorher, kj(id))
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals(TagebuchArt.REZEPT, e.art)
        assertEquals("Reispfanne", e.name)
        assertEquals(1_000L, e.createdAt)
    }

    @Test
    fun ausZutatOhneNaehrwerteUnvollstaendig() = runTest {
        db.zutatDao().upsert(ZutatEntity(id = "z-x", canonicalName = "Unbekannt", createdAt = 1, updatedAt = 1))
        val id = assertNotNull(repo.ausZutat(heute, Mahlzeit.SNACK, "z-x", BigDecimal("50"), MeasureUnit.GRAM))
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals(TagebuchArt.ZUTAT, e.art)
        assertNull(e.energieKj)
        assertFalse(e.vollstaendig)
    }

    @Test
    fun ausPlanEinePortionUndNurEinmal() = runTest {
        db.bestand()
        db.planEintragDao().upsert(plan("p1", heute, "Abendessen", "r1", portionen = 4))
        val id = assertNotNull(repo.ausPlan("p1"))
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals("p1", e.planEintragId)
        assertEquals("r1", e.rezeptId)
        assertEquals(0, BigDecimal.ONE.compareTo(e.portionen))
        assertEquals(Mahlzeit.ABENDESSEN.name, e.mahlzeit)
        assertEquals(heute, e.datum)
        assertNull(repo.ausPlan("p1"), "zweites Übernehmen legt nichts an")
        assertEquals(1, db.tagebuchDao().alle().size)
    }

    @Test
    fun ausPlanOhnePlanEintragGibtNull() = runTest {
        assertNull(repo.ausPlan("fehlt"))
    }

    @Test
    fun freiMitKcal() = runTest {
        val id = repo.frei(heute, Mahlzeit.SNACK, "Apfel", 52, null, null, null)
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals(TagebuchArt.FREI, e.art)
        assertEquals(52, de.foody.domain.Naehrwerte(e.energieKj, null, null, null, true).kcal)
        assertTrue(e.vollstaendig)
    }

    @Test
    fun aendernSkaliert() = runTest {
        db.bestand()
        val id = assertNotNull(repo.ausRezept(heute, Mahlzeit.ABENDESSEN, "r1", BigDecimal.ONE))
        repo.aendern(id, BigDecimal(2), Mahlzeit.MITTAGESSEN, heute.plusDays(1), null)
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals(0, BigDecimal("6000").compareTo(e.energieKj))
        assertEquals(0, BigDecimal(2).compareTo(e.portionen))
        assertEquals(Mahlzeit.MITTAGESSEN.name, e.mahlzeit)
        assertEquals(heute.plusDays(1), e.datum)
    }

    @Test
    fun loeschenUndWiederherstellen() = runTest {
        val id = repo.frei(heute, Mahlzeit.SNACK, "Apfel", 52, null, null, null)
        val weg = assertNotNull(repo.loeschen(id))
        assertNull(db.tagebuchDao().nachId(id))
        repo.wiederherstellen(weg)
        assertEquals(weg, db.tagebuchDao().nachId(id))
        assertEquals(listOf(id), repo.amTag(heute).first().map { it.id })
    }

    @Test
    fun beiAktivemSyncOutbox() = runTest {
        db.syncDao().upsertZustand(SyncZustandEntity(active = true, serverUrl = "https://foody.example"))
        val id = repo.frei(heute, Mahlzeit.SNACK, "Apfel", 52, null, null, null)
        assertTrue(db.syncDao().outbox().any { it.type == "tagebuch_eintrag" && it.recordId == id })
    }

    @Test
    fun geloeschtesRezeptBehaeltEintrag() = runTest {
        db.bestand()
        val id = assertNotNull(repo.ausRezept(heute, Mahlzeit.ABENDESSEN, "r1", BigDecimal.ONE))
        db.rezeptDao().loeschen("r1")
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals("Reispfanne", e.name)
        assertEquals(0, BigDecimal("3000").compareTo(e.energieKj))
    }

    // --- aus Packung (Phase 6e3) ---

    private fun packung(
        werte: Map<Nutrient, BigDecimal>,
        basis: NutrientBasis? = NutrientBasis.PER_100_G,
        strichcode: String? = null,
    ) = Packungswerte(name = null, basis = basis, werte = werte, strichcode = strichcode, quelle = "Open Food Facts")

    private fun kjAus(kcal: Int) = NutritionResult.kcalToKj(BigDecimal(kcal))

    private fun kcal(e: TagebuchEintragEntity) = Naehrwerte(e.energieKj, null, null, null, true).kcal

    @Test
    fun packungOhneHaekchenFrei() = runTest {
        val zutaten = db.zutatDao().alle().size
        val p = packung(mapOf(Nutrient.ENERGY_KJ to kjAus(400), Nutrient.PROTEIN_G to BigDecimal("10")))
        val id = assertNotNull(repo.ausPackung(heute, Mahlzeit.SNACK, " Müsliriegel ", BigDecimal("150"), MeasureUnit.GRAM, p, alsZutat = false))
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals(TagebuchArt.FREI, e.art)
        assertEquals("Müsliriegel", e.name)
        assertEquals(600, kcal(e))
        assertEquals(0, BigDecimal("15").compareTo(e.eiweiss))
        assertNull(e.zutatId)
        assertEquals(zutaten, db.zutatDao().alle().size)
    }

    @Test
    fun packungOhneBerechenbareWerteNull() = runTest {
        val p = packung(mapOf(Nutrient.ENERGY_KJ to kjAus(400)))
        assertNull(repo.ausPackung(heute, Mahlzeit.SNACK, "Saft", BigDecimal("200"), MeasureUnit.MILLILITER, p, alsZutat = false))
        assertNull(repo.ausPackung(heute, Mahlzeit.SNACK, "Saft", BigDecimal("200"), MeasureUnit.MILLILITER, p, alsZutat = true))
        assertTrue(db.tagebuchDao().alle().isEmpty())
        assertNull(db.zutatDao().alle().firstOrNull { it.canonicalName == "Saft" })
    }

    @Test
    fun packungNeueZutat() = runTest {
        val p = packung(mapOf(Nutrient.ENERGY_KJ to BigDecimal("1500"), Nutrient.FAT_G to BigDecimal("7")), strichcode = "4001234567890")
        val id = assertNotNull(
            repo.ausPackung(heute, Mahlzeit.FRUEHSTUECK, "Haferflocken Extra", BigDecimal("50"), MeasureUnit.GRAM, p, alsZutat = true),
        )
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals(TagebuchArt.ZUTAT, e.art)
        val z = assertNotNull(db.zutatDao().nachId(assertNotNull(e.zutatId)))
        assertEquals(IngredientCatalog.canonicalName("Haferflocken Extra"), z.canonicalName)
        assertEquals(IngredientCatalog.guessCategory(z.canonicalName), z.category)
        assertEquals(BigDecimal("1500"), z.energyKj)
        assertEquals(BigDecimal("7"), z.fat)
        assertEquals(NutrientBasis.PER_100_G, z.nutrientBasis)
        assertEquals("Open Food Facts", z.nutrientSource)
        assertEquals("4001234567890", z.barcode)
        assertEquals(z.canonicalName, e.name)
        assertEquals(0, BigDecimal("50").compareTo(e.menge))
        assertEquals(MeasureUnit.GRAM, e.einheit)
        assertEquals(0, BigDecimal("750").compareTo(e.energieKj))
        assertEquals(Mahlzeit.FRUEHSTUECK.name, e.mahlzeit)
    }

    @Test
    fun packungAktualisiertVorhandene() = runTest {
        val k = IngredientCatalog.canonicalName("Mehl")
        db.zutatDao().upsert(
            ZutatEntity(
                id = "w", canonicalName = k, nutrientBasis = NutrientBasis.PER_100_G, energyKj = BigDecimal("1400"),
                fat = BigDecimal("1"), fiber = BigDecimal("3"), nutrientSource = "BLS", createdAt = 1, updatedAt = 1,
            ),
        )
        val anzahl = db.zutatDao().alle().size
        val p = packung(mapOf(Nutrient.ENERGY_KJ to BigDecimal("1450"), Nutrient.FAT_G to BigDecimal("1.2")))
        val id = assertNotNull(repo.ausPackung(heute, Mahlzeit.ABENDESSEN, "Mehl", BigDecimal("100"), MeasureUnit.GRAM, p, alsZutat = true))
        assertEquals(anzahl, db.zutatDao().alle().size)
        val z = assertNotNull(db.zutatDao().nachId("w"))
        assertEquals(BigDecimal("1450"), z.energyKj)
        assertEquals(BigDecimal("1.2"), z.fat)
        assertEquals(BigDecimal("3"), z.fiber)
        assertEquals("Open Food Facts", z.nutrientSource)
        assertEquals(1_000L, z.updatedAt)
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals("w", e.zutatId)
        assertEquals(k, e.name)
    }

    @Test
    fun packungStrichcodeNurWennFrei() = runTest {
        db.zutatDao().upsert(ZutatEntity(id = "a", canonicalName = "Saft", barcode = "4001234567890", createdAt = 1, updatedAt = 1))
        db.zutatDao().upsert(ZutatEntity(id = "b", canonicalName = "Quark", barcode = "40000000", createdAt = 1, updatedAt = 1))
        val p = packung(mapOf(Nutrient.ENERGY_KJ to BigDecimal("300")), strichcode = "4001234567890")
        val neu = assertNotNull(repo.ausPackung(heute, Mahlzeit.SNACK, "Joghurtdrink", BigDecimal("100"), MeasureUnit.GRAM, p, alsZutat = true))
        assertNull(db.zutatDao().nachId(assertNotNull(db.tagebuchDao().nachId(neu)?.zutatId))?.barcode)
        repo.ausPackung(heute, Mahlzeit.SNACK, "Quark", BigDecimal("100"), MeasureUnit.GRAM, p.copy(strichcode = "4009999999999"), alsZutat = true)
        assertEquals("40000000", db.zutatDao().nachId("b")?.barcode)
        assertEquals("4001234567890", db.zutatDao().nachId("a")?.barcode)
    }

    @Test
    fun packungBasisMl() = runTest {
        val p = packung(mapOf(Nutrient.ENERGY_KJ to BigDecimal("200")), basis = NutrientBasis.PER_100_ML)
        val frei = assertNotNull(repo.ausPackung(heute, Mahlzeit.SNACK, "Saft", BigDecimal("250"), MeasureUnit.MILLILITER, p, alsZutat = false))
        assertEquals(0, BigDecimal("500").compareTo(db.tagebuchDao().nachId(frei)?.energieKj))
        val id = assertNotNull(repo.ausPackung(heute, Mahlzeit.SNACK, "Saft", BigDecimal("250"), MeasureUnit.MILLILITER, p, alsZutat = true))
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals(MeasureUnit.MILLILITER, e.einheit)
        assertEquals(0, BigDecimal("500").compareTo(e.energieKj))
        assertEquals(NutrientBasis.PER_100_ML, db.zutatDao().nachId(assertNotNull(e.zutatId))?.nutrientBasis)
    }

    @Test
    fun packungGenaueKj() = runTest {
        val p = packung(mapOf(Nutrient.ENERGY_KJ to BigDecimal("1502.3")))
        val id = assertNotNull(repo.ausPackung(heute, Mahlzeit.SNACK, "Dinkelpops", BigDecimal("100"), MeasureUnit.GRAM, p, alsZutat = true))
        val e = assertNotNull(db.tagebuchDao().nachId(id))
        assertEquals(BigDecimal("1502.3"), db.zutatDao().nachId(assertNotNull(e.zutatId))?.energyKj)
        assertEquals(0, BigDecimal("1502.3").compareTo(e.energieKj))
    }
}

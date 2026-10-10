package de.foody.app

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.GoalPreferences
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.db.IngredientEntity
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.repo.PlanRepository
import de.foody.app.data.repo.RecipeDraft
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.data.repo.TagebuchRepository
import de.foody.app.ui.tagebuch.TagebuchUiState
import de.foody.app.ui.tagebuch.TagebuchViewModel
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.NutrientBasis
import de.foody.domain.TagebuchArt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tagebuch: Vorschläge aus dem Plan übernehmen, Einträge aus Rezept, Zutat und frei; Werte bleiben festgehalten. */
@RunWith(AndroidJUnit4::class)
class TagebuchViewModelTest {
    private lateinit var db: FoodyDatabase
    private lateinit var recipes: RecipeRepository
    private lateinit var plan: PlanRepository
    private lateinit var vm: TagebuchViewModel
    private lateinit var curryId: String
    private val collector = CoroutineScope(Dispatchers.Main)
    private val today = LocalDate.now()

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatabase::class.java).build()
        recipes = RecipeRepository(db.recipeDao())
        // 1464 kJ je 100 g; Curry: 200 g für 2 Portionen → 1464 kJ ≈ 350 kcal je Portion
        db.ingredientDao().upsert(
            IngredientEntity(id = "reis", canonicalName = "Reis", nutrientBasis = NutrientBasis.PER_100_G,
                energyKj = BigDecimal("1464"), createdAt = 0, updatedAt = 0),
        )
        curryId = recipes.save(
            RecipeDraft(id = null, name = "Curry", defaultServings = 2,
                ingredients = listOf(RecipeDraft.Line("reis", BigDecimal("200"), MeasureUnit.GRAM, null, false))),
        )
        plan = PlanRepository(db, db.mealPlanDao(), db.recipeDao(), db.pantryDao(), db.ingredientDao())
        vm = TagebuchViewModel(
            TagebuchRepository(db, db.tagebuchDao()), plan, recipes, IngredientRepository(db, db.ingredientDao(), context),
            GoalPreferences(context), SavedStateHandle(), ohneScan(),
        )
        collector.launch { vm.state.collect {} }
        Unit
    }

    @After fun tearDown() {
        collector.cancel()
        vm.aufraeumen()
        db.close()
    }

    private fun await(condition: (TagebuchUiState) -> Boolean) = runBlocking {
        withTimeout(5_000) { vm.state.first { !it.loading && condition(it) } }
    }

    private fun eintraege(s: TagebuchUiState) = s.eintraege.values.flatten()

    @Test fun gegessenUebernimmtVorschlagEinmal() = runBlocking {
        plan.add(today, Mahlzeit.ABENDESSEN.name, curryId, 4)
        val slot = await { it.vorschlaege[Mahlzeit.ABENDESSEN].orEmpty().size == 1 }.vorschlaege.getValue(Mahlzeit.ABENDESSEN).single().first
        vm.gegessen(slot)
        vm.gegessen(slot)
        val s = await { eintraege(it).isNotEmpty() && it.vorschlaege[Mahlzeit.ABENDESSEN].isNullOrEmpty() }
        kotlinx.coroutines.delay(300) // zweiter Tipp hätte jetzt geschrieben
        val alle = db.tagebuchDao().getAll()
        assertEquals(1, alle.size)
        assertEquals(slot.id, alle.single().planEintragId)
        assertEquals(TagebuchArt.REZEPT, alle.single().art)
        assertEquals(350, s.bilanz.tag.kcal)
    }

    @Test fun festgehaltenNachRezeptAenderung() = runBlocking {
        vm.rezeptEintragen(Mahlzeit.MITTAGESSEN, curryId, BigDecimal.ONE)
        await { it.bilanz.tag.kcal == 350 }
        recipes.save(recipes.draftOf(curryId)!!.copy(defaultServings = 4))
        recipes.delete(curryId)
        assertEquals(350, await { eintraege(it).size == 1 }.bilanz.tag.kcal)
    }

    @Test fun zutatOhneStueckgewicht() = runBlocking {
        assertFalse(vm.zutatEintragen(Mahlzeit.SNACK, "reis", BigDecimal("2"), MeasureUnit.PIECE))
        assertTrue(db.tagebuchDao().getAll().isEmpty())
        assertTrue(vm.zutatEintragen(Mahlzeit.SNACK, "reis", BigDecimal("100"), MeasureUnit.GRAM))
        val s = await { eintraege(it).size == 1 }
        assertEquals(350, s.bilanz.jeMahlzeit.getValue(Mahlzeit.SNACK).kcal)
        assertEquals("Reis", eintraege(s).single().name)
    }

    @Test fun freiEintrag() = runBlocking {
        vm.freiEintragen(Mahlzeit.SNACK, "Apfel", 80, null, null, null)
        assertEquals(80, await { eintraege(it).size == 1 }.bilanz.jeMahlzeit.getValue(Mahlzeit.SNACK).kcal)
        vm.loeschen(eintraege(vm.state.value).single().id)
        await { eintraege(it).isEmpty() }
        Unit
    }

    @Test fun wocheUeberJahresgrenze() {
        vm.zeigeTag(LocalDate.of(2026, 1, 2))
        val s = await { it.tag == LocalDate.of(2026, 1, 2) }
        assertEquals((0L..6L).map { LocalDate.of(2025, 12, 27).plusDays(it) }, s.woche.map { it.first })
    }

    @Test fun bearbeitenSkaliertFestgehalteneWerte() = runBlocking {
        vm.rezeptEintragen(Mahlzeit.MITTAGESSEN, curryId, BigDecimal.ONE)
        val e = eintraege(await { it.bilanz.tag.kcal == 350 }).single()
        // Rezept danach ändern: 4 statt 2 Portionen → live wären es nur noch 175 kcal je Portion
        recipes.save(recipes.draftOf(curryId)!!.copy(defaultServings = 4))
        vm.bearbeiten(e.copy(mahlzeit = Mahlzeit.ABENDESSEN.name))
        assertEquals(350, await { it.eintraege[Mahlzeit.ABENDESSEN]?.size == 1 }.bilanz.tag.kcal, "nur Mahlzeit geändert")
        vm.bearbeiten(db.tagebuchDao().get(e.id)!!.copy(portionen = BigDecimal("2")))
        assertEquals(700, await { it.bilanz.tag.kcal != 350 }.bilanz.tag.kcal, "doppelte Portionen = doppelte festgehaltene Werte")
    }

    @Test fun verschobenerPlanEintragBleibtUebernommen() = runBlocking {
        plan.add(today, Mahlzeit.ABENDESSEN.name, curryId, 2)
        val slot = await { it.vorschlaege[Mahlzeit.ABENDESSEN].orEmpty().size == 1 }.vorschlaege.getValue(Mahlzeit.ABENDESSEN).single().first
        vm.gegessen(slot)
        await { eintraege(it).size == 1 }
        // Nach dem Essen 8 Tage später eingeplant: außerhalb der Wochenleiste des neuen Tages
        plan.update(slot.copy(date = today.plusDays(8)))
        vm.zeigeTag(today.plusDays(8))
        await { it.tag == today.plusDays(8) }
        kotlinx.coroutines.delay(500)
        assertTrue(vm.state.value.vorschlaege.isEmpty(), "schon gegessen – kein Vorschlag, dessen „Gegessen“ nichts täte")
    }

    private val skyr = de.foody.app.scan.Packung(
        "Skyr", NutrientBasis.PER_100_G,
        mapOf(de.foody.domain.Nutrient.ENERGY_KJ to BigDecimal(260), de.foody.domain.Nutrient.PROTEIN_G to BigDecimal(11)),
        "4001234567890", de.foody.app.scan.OpenFoodFacts.QUELLE,
    )

    @Test fun packungAlsNeueZutat() = runBlocking {
        assertTrue(vm.packungEintragen(Mahlzeit.SNACK, "Skyr", BigDecimal(150), MeasureUnit.GRAM, skyr, alsZutat = true))
        val zutat = db.ingredientDao().findByNameExact("Skyr")!!
        assertEquals(0, BigDecimal(260).compareTo(zutat.energyKj))
        assertEquals("4001234567890", zutat.barcode)
        assertEquals(de.foody.app.scan.OpenFoodFacts.QUELLE, zutat.nutrientSource)
        val e = db.tagebuchDao().getAll().single()
        assertEquals(TagebuchArt.ZUTAT, e.art)
        assertEquals(zutat.id, e.zutatId)
        // 260 kJ je 100 g × 1,5 = 390 kJ ≈ 93 kcal
        assertEquals(93, await { eintraege(it).size == 1 }.bilanz.tag.kcal)
    }

    @Test fun packungOhneHaekchenIstFrei() = runBlocking {
        assertTrue(vm.packungEintragen(Mahlzeit.SNACK, "Skyr", BigDecimal(150), MeasureUnit.GRAM, skyr, alsZutat = false))
        assertEquals(null, db.ingredientDao().findByNameExact("Skyr"))
        val e = db.tagebuchDao().getAll().single()
        assertEquals(TagebuchArt.FREI, e.art)
        assertEquals(93, await { eintraege(it).size == 1 }.bilanz.tag.kcal)
    }

    @Test fun vorhandeneZutatOhneHaekchenBleibt() = runBlocking {
        val anders = skyr.copy(name = "Reis", werte = mapOf(de.foody.domain.Nutrient.ENERGY_KJ to BigDecimal(1500)))
        assertTrue(vm.packungEintragen(Mahlzeit.MITTAGESSEN, "Reis", BigDecimal(100), MeasureUnit.GRAM, anders, alsZutat = false))
        assertEquals(0, BigDecimal("1464").compareTo(db.ingredientDao().get("reis")!!.energyKj), "Katalogwert unverändert")
        // Der Eintrag nutzt die gescannten Werte: 1500 kJ ≈ 359 kcal
        assertEquals(359, await { eintraege(it).size == 1 }.bilanz.tag.kcal)
    }

    @Test fun vorhandeneZutatMitHaekchenWirdAktualisiert() = runBlocking {
        val anders = skyr.copy(name = "Reis", werte = mapOf(de.foody.domain.Nutrient.ENERGY_KJ to BigDecimal(1500)))
        assertTrue(vm.packungEintragen(Mahlzeit.MITTAGESSEN, "Reis", BigDecimal(100), MeasureUnit.GRAM, anders, alsZutat = true))
        val reis = db.ingredientDao().get("reis")!!
        assertEquals(0, BigDecimal(1500).compareTo(reis.energyKj))
        assertEquals("4001234567890", reis.barcode)
        assertEquals("reis", db.tagebuchDao().getAll().single().zutatId)
    }
}

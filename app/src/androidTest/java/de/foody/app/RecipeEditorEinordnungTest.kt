package de.foody.app

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.app.data.db.FoodyDatabase
import de.foody.app.data.repo.IngredientRepository
import de.foody.app.data.RecipePhotoStore
import de.foody.app.data.repo.RecipeRepository
import de.foody.app.ui.recipes.RecipeEditorViewModel
import de.foody.domain.Gang
import de.foody.domain.Mahlzeit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Einordnung im Editor: Vermutung vorausgewählt, Antippen legt fest, Zurücksetzen vermutet wieder. */
@RunWith(AndroidJUnit4::class)
class RecipeEditorEinordnungTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: FoodyDatabase
    private lateinit var recipes: RecipeRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FoodyDatabase::class.java).build()
        recipes = RecipeRepository(db.recipeDao())
    }

    private val editoren = mutableListOf<RecipeEditorViewModel>()

    @After fun tearDown() {
        editoren.forEach { it.aufraeumen() }
        db.close()
    }

    private fun editor(id: String? = null) = RecipeEditorViewModel(
        recipes, IngredientRepository(db, db.ingredientDao(), context), RecipePhotoStore(context, db.recipeDao()),
        SavedStateHandle(if (id == null) emptyMap() else mapOf("id" to id)),
    ).also { editoren += it }

    private suspend fun RecipeEditorViewModel.loaded() = apply { withTimeout(5_000) { state.first { it.loaded } } }

    private suspend fun RecipeEditorViewModel.saveAndWait() {
        val done = CompletableDeferred<Unit>()
        save { done.complete(Unit) }
        withTimeout(5_000) { done.await() }
    }

    private suspend fun gespeichert(name: String) = db.recipeDao().observeAll().first().single { it.name == name }

    @Test fun vermutungWirdAngezeigt() = runBlocking {
        val vm = editor().loaded()
        vm.set { it.copy(name = "Tiramisu") }
        assertEquals(setOf(Gang.NACHSPEISE), vm.state.value.einordnung.gaenge)
        assertTrue(vm.state.value.einordnung.gaengeVermutet)
    }

    @Test fun antippenUebernimmtVermutungPlusAenderung() = runBlocking {
        val vm = editor().loaded()
        vm.set { it.copy(name = "Tiramisu") }
        vm.toggleMahlzeit(Mahlzeit.ABENDESSEN)
        assertEquals(setOf(Mahlzeit.SNACK, Mahlzeit.ABENDESSEN), vm.state.value.mahlzeiten)
        vm.saveAndWait()
        val r = gespeichert("Tiramisu")
        assertEquals("ABENDESSEN,SNACK", r.mahlzeiten)
        assertNull(r.gaenge, "Gang nicht angefasst → bleibt vermutet")
    }

    @Test fun zuruecksetzenVermutetWieder() = runBlocking {
        val vm = editor().loaded()
        vm.set { it.copy(name = "Curry") }
        vm.toggleGang(Gang.BROTZEIT)
        vm.saveAndWait()
        val id = gespeichert("Curry").id
        assertEquals("HAUPTSPEISE,BROTZEIT", gespeichert("Curry").gaenge)

        val again = editor(id).loaded()
        assertEquals(setOf(Gang.HAUPTSPEISE, Gang.BROTZEIT), again.state.value.gaenge)
        again.gaengeZuruecksetzen()
        assertNull(again.state.value.gaenge)
        again.saveAndWait()
        assertNull(gespeichert("Curry").gaenge)
    }

    @Test fun alleAbgewaehltIstBewusstLeer() = runBlocking {
        val vm = editor().loaded()
        vm.set { it.copy(name = "Tiramisu") }
        vm.toggleMahlzeit(Mahlzeit.SNACK)
        assertEquals(emptySet(), vm.state.value.mahlzeiten)
        vm.saveAndWait()
        assertEquals("", gespeichert("Tiramisu").mahlzeiten)
    }
}

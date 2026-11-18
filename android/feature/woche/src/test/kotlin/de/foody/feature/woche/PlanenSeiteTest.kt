package de.foody.feature.woche

import android.content.Context
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.Einstellungsschluessel
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Planen in den Stilen Q–V wie in den Entwürfen: eine eigene Seite „Woche planen“ mit den Reitern „Selbst wählen“ und
 * „Vorschlagen lassen“ statt Mahlzeitwahl, Rezeptwahl und Füllen-Blatt; Milchglas behält die Blätter.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "h1600dp")
class PlanenSeiteTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private lateinit var u: WocheTestUmgebung
    private lateinit var vm: WocheViewModel

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        db = testDatenbank()
        u = WocheTestUmgebung(db)
        runBlocking {
            db.bestand()
            db.hauptspeisen()
        }
    }

    @After
    fun tearDown() {
        u.viewModels.schliessen()
        db.close()
    }

    private fun zeigen(stil: FoodyStil, variante: StilVariante = StilVariante.IMMERSIV) {
        vm = u.vm()
        compose.setContent {
            FoodyTheme(stil = stil, variante = variante, unschaerfe = false) { WocheRoute({ _, _ -> }, {}, vm = vm) }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(fuellenKnopf).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun seiteDa() = compose.onAllNodes(hasContentDescription("Woche planen") and isHeading()).fetchSemanticsNodes().isNotEmpty()

    private fun reiter(name: String) = compose.onNode(hasContentDescription(name) and isSelectable())

    /** Szenen beschreiben den Knopf („Leere Tage füllen“), die normale Ansicht zeigt ihn als Text. */
    private val fuellenKnopf = hasContentDescription("Leere Tage füllen", substring = true) or hasText("Leere Tage füllen")

    private fun fuellenTippen() = compose.onAllNodes(fuellenKnopf).onFirst().performScrollTo().performClick()

    private fun eintraege() = runBlocking { db.planEintragDao().alle() }

    /** Über „Leere Tage füllen“ öffnet die Seite mit den Vorschlägen; „Selbst wählen“ plant Gemüsecurry am Freitag mittags. */
    private fun selbst(stil: FoodyStil, variante: StilVariante = StilVariante.IMMERSIV) {
        zeigen(stil, variante)
        fuellenTippen()
        compose.waitUntil(5_000) { seiteDa() }
        reiter("Vorschlagen lassen").assertIsSelected()
        compose.warteAufText("Neu mischen")
        reiter("Selbst wählen").performClick()
        reiter("Selbst wählen").assertIsSelected()
        compose.onNode(hasContentDescription("Freitag, 30.10.") and isSelectable()).performScrollTo().performClick()
        compose.onNodeWithText("Mittagessen").performScrollTo().performClick()
        compose.onNodeWithText("Gemüsecurry").performScrollTo().performClick()
        compose.onNodeWithTag(PLANEN_EINPLANEN).performClick()
        compose.waitUntil(5_000) { eintraege().isNotEmpty() }
        val e = eintraege().single()
        assertEquals(MONTAG.plusDays(4), e.date)
        assertEquals("MITTAGESSEN", e.slotType)
        assertEquals("h1", e.recipeId)
        assertEquals(2, e.servings)
        compose.waitUntil(5_000) { !seiteDa() }
        assertNull(vm.fuellen.value)
    }

    @Test fun sage() = selbst(FoodyStil.SAGE)

    @Test fun anime() = selbst(FoodyStil.ANIME)

    @Test fun edo() = selbst(FoodyStil.EDO)

    @Test fun bayrisch() = selbst(FoodyStil.BAYRISCH)

    @Test fun grauen() = selbst(FoodyStil.GRAUEN)

    @Test fun kreuzer() = selbst(FoodyStil.KREUZER)

    @Test fun ruhig() = selbst(FoodyStil.EDO, StilVariante.RUHIG)

    /**
     * „+ Mahlzeit“ am gewählten Tag öffnet „Selbst wählen“ mit diesem Tag und Abendessen; Portionen ± wirken. Seit alle
     * Stile auch in Ruhig ihre Szene zeigen, geht das über den Knopf der Szene (hier Kreuzer in Ruhig).
     */
    @Test
    fun plusMitPortionen() {
        runBlocking { db.planen("p1", MONTAG, "MITTAGESSEN") }
        zeigen(FoodyStil.KREUZER, StilVariante.RUHIG)
        // Chips und Liste wählen beide den Tag; der Chip kommt zuerst
        compose.onAllNodes(hasContentDescription("Montag, 26.10.", substring = true) and isSelectable()).onFirst().performClick()
        compose.onNodeWithContentDescription("Mahlzeit hinzufügen").performScrollTo().performClick()
        compose.waitUntil(5_000) { seiteDa() }
        reiter("Selbst wählen").assertIsSelected()
        compose.onNode(hasContentDescription("Montag, 26.10.") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Pilzrisotto").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Mehr Portionen").performClick()
        compose.onNodeWithTag(PLANEN_EINPLANEN).performClick()
        compose.waitUntil(5_000) { eintraege().size == 2 }
        val e = eintraege().single { it.recipeId == "h3" }
        assertEquals(MONTAG, e.date)
        assertEquals("ABENDESSEN", e.slotType)
        assertEquals(3, e.servings)
    }

    /** Zurück schließt ohne zu planen; „Vorschlagen lassen“ übernimmt die Vorschläge und schließt die Seite. */
    @Test
    fun vorschlaegeUndZurueck() {
        zeigen(FoodyStil.KREUZER)
        fuellenTippen()
        compose.waitUntil(5_000) { seiteDa() }
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.waitUntil(5_000) { !seiteDa() }
        assertNull(vm.fuellen.value)
        assertTrue(eintraege().isEmpty())
        fuellenTippen()
        compose.waitUntil(5_000) { vm.fuellen.value?.vorschlag?.isNotEmpty() == true }
        compose.onNodeWithText("Route übernehmen", substring = true).performClick()
        compose.waitUntil(5_000) { !seiteDa() }
        assertTrue(eintraege().isNotEmpty())
    }

    /** Milchglas hat keinen Entwurf: „Leere Tage füllen“ bleibt ein Blatt, keine Seite. */
    @Test
    fun milchglasBlatt() {
        zeigen(FoodyStil.MILCHGLAS)
        fuellenTippen()
        compose.warteAufText("Neu mischen")
        assertTrue(!seiteDa())
    }

    @Test
    fun startAlsText() {
        val s = PlanenStart(PlanenReiter.SELBST, MONTAG, "Essen | bei Mia")
        assertEquals(s, PlanenStart.ausText(s.alsText()))
    }
}

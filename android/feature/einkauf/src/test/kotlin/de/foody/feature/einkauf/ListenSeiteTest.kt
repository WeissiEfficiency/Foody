package de.foody.feature.einkauf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Listen in den Stilen Q–V wie in den Entwürfen: eine eigene Seite „Einkaufslisten“ mit den Reitern „Neue Liste ·
 * Abgleich · Frühere“ statt drei Blättern, im Kopf „Aktuelle Liste löschen“; Milchglas behält die Blätter.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "h1600dp")
class ListenSeiteTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private lateinit var u: EinkaufTestUmgebung
    private val geoeffnet = mutableListOf<String>()

    @Before
    fun setUp() {
        db = testDatenbank()
        u = EinkaufTestUmgebung(db)
        runBlocking { db.einkaufBestand() }
    }

    @After
    fun tearDown() {
        u.viewModels.schliessen()
        db.close()
    }

    private fun zeigen(stil: FoodyStil, variante: StilVariante = StilVariante.IMMERSIV): EinkaufViewModel {
        val vm = u.vm()
        compose.setContent {
            FoodyTheme(stil = stil, variante = variante, unschaerfe = false) { EinkaufRoute(null, onListe = { geoeffnet += it }, onAktuelle = {}, vm = vm) }
        }
        return vm
    }

    /** Liste „26.10.–1.11.“ aus dem Curry am Mittwoch (mit Zeitraum, also abgleichbar). */
    private fun planListe(): String = runBlocking {
        db.planen("s1", MITTWOCH, "c")
        u.repo.ausPlanAnlegen(MONTAG, MONTAG.plusDays(6), emptySet())!!
    }

    private fun seiteDa() = compose.onAllNodes(hasContentDescription("Einkaufslisten") and isHeading()).fetchSemanticsNodes().isNotEmpty()

    private fun reiter(name: String) = compose.onNode(hasContentDescription(name) and isSelectable())

    /** Szenen (Q–V) haben den Knopf „Listen“, Milchglas das Menü ⋮ mit „Frühere Listen“. */
    private fun listenOeffnen() {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("Einkaufslisten") or hasContentDescription("Weitere Aktionen")).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodes(hasContentDescription("Einkaufslisten")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodes(hasContentDescription("Einkaufslisten")).onFirst().performClick()
        } else {
            compose.onNodeWithContentDescription("Weitere Aktionen").performClick()
            compose.onNodeWithText("Frühere Listen").performClick()
        }
    }

    /**
     * „Listen“ öffnet die Seite auf „Neue Liste“; die Reiter wechseln zu „Abgleich“ und „Frühere“, der Papierkorb für die
     * aktuelle Liste steht auf jedem im Kopf; Zurück schließt.
     */
    private fun reiterWechseln(stil: FoodyStil, variante: StilVariante = StilVariante.IMMERSIV) {
        planListe()
        val vm = zeigen(stil, variante)
        listenOeffnen()
        compose.waitUntil(5_000) { seiteDa() }
        reiter("Neue Liste").assertIsSelected()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Zwiebeln übernehmen")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Aktuelle Liste löschen").assertExists()
        reiter("Abgleich").performClick()
        reiter("Abgleich").assertIsSelected()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Die Liste passt zum", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Aktuelle Liste löschen").assertExists()
        reiter("Frühere Listen").performClick()
        reiter("Frühere Listen").assertIsSelected()
        compose.onNodeWithContentDescription("Aktuelle Liste löschen").assertExists()
        compose.onNodeWithContentDescription("Zurück").performClick()
        compose.waitUntil(5_000) { !seiteDa() }
        assertEquals(null, vm.blatt.value)
    }

    @Test fun sage() = reiterWechseln(FoodyStil.SAGE)

    @Test fun anime() = reiterWechseln(FoodyStil.ANIME)

    @Test fun edo() = reiterWechseln(FoodyStil.EDO)

    @Test fun bayrisch() = reiterWechseln(FoodyStil.BAYRISCH)

    @Test fun grauen() = reiterWechseln(FoodyStil.GRAUEN)

    @Test fun kreuzer() = reiterWechseln(FoodyStil.KREUZER)

    @Test fun ruhig() = reiterWechseln(FoodyStil.BAYRISCH, StilVariante.RUHIG)

    /** „Neue Liste“ legt die Liste an und schließt die Seite. */
    @Test
    fun neueListeAnlegen() {
        runBlocking { db.planen("s1", MITTWOCH, "c") }
        zeigen(FoodyStil.KREUZER)
        listenOeffnen()
        compose.waitUntil(5_000) { seiteDa() }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Liste anlegen (2)")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Liste anlegen (2)").performClick()
        compose.waitUntil(5_000) { runBlocking { db.einkaufsartikelDao().alle() }.size == 2 }
        compose.waitUntil(5_000) { !seiteDa() }
    }

    /** Eine Liste ohne Zeitraum lässt sich nicht abgleichen; der Reiter sagt warum. „Frühere“ öffnet eine Liste. */
    @Test
    fun ohneZeitraumUndOeffnen() {
        runBlocking {
            db.einkaufslisteDao().upsert(liste("alt", 1, name = "12.10.–18.10."))
            db.einkaufslisteDao().upsert(liste("neu", 2))
        }
        zeigen(FoodyStil.EDO)
        listenOeffnen()
        compose.waitUntil(5_000) { seiteDa() }
        reiter("Abgleich").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("nicht aus dem Plan", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        reiter("Frühere Listen").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("12.10.–18.10.")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("12.10.–18.10.").performClick()
        assertEquals(listOf("alt"), geoeffnet)
    }

    /** Der Papierkorb „Aktuelle Liste löschen“ steht nur mit einer aktuellen Liste im Kopf und ruft `onLoeschen`. */
    @Test
    fun loeschenNurMitListe() {
        var mitListe by mutableStateOf(false)
        var geloescht = 0
        compose.setContent {
            FoodyTheme(stil = FoodyStil.ANIME, variante = StilVariante.IMMERSIV, unschaerfe = false) {
                ListenSeite(Blatt.Frueher, mitZeitraum = true, onReiter = {}, onZurueck = {}, onLoeschen = if (mitListe) ({ geloescht++ }) else null) {}
            }
        }
        compose.waitUntil(5_000) { seiteDa() }
        compose.onNodeWithContentDescription("Aktuelle Liste löschen").assertDoesNotExist()
        mitListe = true
        compose.onNodeWithContentDescription("Aktuelle Liste löschen").performClick()
        assertEquals(1, geloescht)
    }

    /** Leerer Einkauf (keine Liste): „Listen“ öffnet die Seite ohne „Aktuelle Liste löschen“. */
    @Test
    fun ohneListeKeinLoeschen() {
        zeigen(FoodyStil.ANIME)
        listenOeffnen()
        compose.waitUntil(5_000) { seiteDa() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Aktuelle Liste löschen").assertDoesNotExist()
    }

    /** Milchglas hat keinen Entwurf: „Frühere Listen“ bleibt ein Blatt, keine Seite. */
    @Test
    fun milchglasBlatt() {
        planListe()
        zeigen(FoodyStil.MILCHGLAS)
        listenOeffnen()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Frühere Listen")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(!seiteDa())
    }
}

package de.foody.feature.heute.tagebuch

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.erkennung.ErkennungStatus
import de.foody.core.erkennung.ErkennungTexte
import de.foody.core.erkennung.FotoGedaechtnis
import de.foody.core.erkennung.MahlzeitErkenner
import de.foody.core.testing.ViewModelHalter
import de.foody.domain.FotoPortion
import de.foody.domain.FotoVorschlag
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.MitSchrift200
import de.foody.feature.heute.pruefeNichtsAbgeschnitten
import de.foody.feature.heute.testDatenbank
import de.foody.feature.heute.testErkennung
import de.foody.feature.heute.testGedaechtnis
import de.foody.feature.heute.testScan
import de.foody.feature.heute.warteAufText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals

/**
 * Blatt „Eintragen“ → „Frei“ mit „Foto erkennen“: Knopf nur mit Modell, Fortschritt mit „Abbrechen“, Meldung, Vorschlag
 * in den Feldern. Kamera und Galerie laufen im Test nicht; die Erkennung startet direkt über den Ablauf.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "h1600dp")
class EintragenBlattFotoTest {
    private val halter = ViewModelHalter()

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank

    /** Antwort des Fake-Erkenners; bis dahin läuft die Erkennung. */
    private val antwort = CompletableDeferred<ErkennungStatus>()
    private val erkenner = MahlzeitErkenner { antwort.await() }

    @Before
    fun setUp() {
        db = testDatenbank()
    }

    @After
    fun tearDown() {
        halter.schliessen()
        db.close()
    }

    private fun zeigen(modell: Boolean = true, schrift200: Boolean = false, gedaechtnis: FotoGedaechtnis = testGedaechtnis()): EintragenViewModel {
        val vm = halter.behalten(
            EintragenViewModel(SavedStateHandle(), HeuteRepository(db, EmptyCoroutineContext), TagebuchRepository(db) { 1L }, db, testScan(), erkenner, testErkennung(modell), gedaechtnis),
        )
        val auftrag = BlattAuftrag(Mahlzeit.MITTAGESSEN, LocalDate.of(2026, 10, 24))
        compose.setContent {
            FoodyTheme {
                if (schrift200) MitSchrift200 { EintragenBlatt(auftrag, {}, vm) } else EintragenBlatt(auftrag, {}, vm)
            }
        }
        compose.warteAufText("Frei")
        compose.onNodeWithText("Frei").performClick()
        compose.warteAufText("Packung scannen")
        return vm
    }

    @Test
    fun ohneModellKeinKnopf() {
        zeigen(modell = false)
        assertEquals(0, compose.onAllNodesWithText(ErkennungTexte.KNOPF).fetchSemanticsNodes().size)
    }

    @Test
    fun menueMitKameraUndGalerie() {
        zeigen()
        compose.onNodeWithText(ErkennungTexte.KNOPF).performScrollTo().performClick()
        compose.onNodeWithText(ErkennungTexte.KAMERA).assertExists()
        compose.onNodeWithText(ErkennungTexte.GALERIE).assertExists()
    }

    @Test
    fun fortschrittMitAbbrechen() {
        val vm = zeigen()
        compose.runOnIdle { vm.fotoLauf.erkennen("content://foto") }
        compose.warteAufText(ErkennungTexte.LAEUFT)
        compose.onNode(hasText(ErkennungTexte.KNOPF) and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText(ErkennungTexte.ABBRECHEN).performClick()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithText(ErkennungTexte.LAEUFT).fetchSemanticsNodes().size)
    }

    @Test
    fun meldungOhneVorschlag() {
        val vm = zeigen()
        compose.runOnIdle { vm.fotoLauf.erkennen("content://foto") }
        antwort.complete(ErkennungStatus.Unlesbar)
        compose.warteAufText(ErkennungTexte.UNLESBAR)
    }

    @Test
    fun vorschlagInDenFeldern() {
        val vm = zeigen()
        compose.runOnIdle { vm.fotoLauf.erkennen("content://foto") }
        antwort.complete(ErkennungStatus.Erkannt(FotoVorschlag("Leberkässemmel", 480, BigDecimal("18.5"), null, null), 20_000))
        compose.warteAufText(ErkennungTexte.BITTE_PRUEFEN)
        compose.onNode(hasSetTextAction() and hasText("Leberkässemmel")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("480")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("18,5")).assertExists()

        // Alles von Hand geändert: kein Hinweis mehr
        compose.onNode(hasSetTextAction() and hasText("Leberkässemmel")).performTextReplacement("Semmel")
        compose.onNode(hasSetTextAction() and hasText("480")).performTextReplacement("300")
        compose.onNode(hasSetTextAction() and hasText("18,5")).performTextReplacement("10")
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithText(ErkennungTexte.BITTE_PRUEFEN).fetchSemanticsNodes().size)
    }

    @Test
    fun schrift200() {
        val vm = zeigen(schrift200 = true)
        compose.runOnIdle { vm.fotoLauf.erkennen("content://foto") }
        compose.warteAufText(ErkennungTexte.LAEUFT)
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test
    fun portionsChipsRechnenUm() {
        val vm = zeigen()
        compose.runOnIdle { vm.fotoLauf.erkennen("content://foto") }
        antwort.complete(ErkennungStatus.Erkannt(FotoVorschlag("Leberkässemmel", 480, BigDecimal("18.5"), null, null), 20_000))
        compose.warteAufText(ErkennungTexte.BITTE_PRUEFEN)
        compose.onNode(hasText("groß") and hasClickAction()).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("672")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("25,9")).assertExists()
    }

    @Test
    fun wieBeimLetztenMal() {
        val gedaechtnis = testGedaechtnis()
        val gespeichert = FotoVorschlag("Leberkässemmel mit Senf", 520, null, null, null)
        runBlocking { gedaechtnis.merken(FotoVorschlag("Leberkässemmel", 480, null, null, null), false, FotoPortion.NORMAL, gespeichert) }
        val vm = zeigen(gedaechtnis = gedaechtnis)
        compose.runOnIdle { vm.fotoLauf.erkennen("content://foto") }
        antwort.complete(ErkennungStatus.Erkannt(FotoVorschlag("Leberkässemmel", 480, null, null, null), 20_000))
        compose.warteAufText(ErkennungTexte.WIE_LETZTES_MAL)
        compose.onNode(hasSetTextAction() and hasText("Leberkässemmel mit Senf")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("520")).assertExists()
    }
}

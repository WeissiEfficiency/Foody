package de.foody.feature.heute.tagebuch

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.Einstellungsschluessel
import de.foody.core.data.TageszielSpeicher
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.heute.HeuteRepository
import de.foody.core.data.tagebuch.TagebuchRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.testing.ViewModelHalter
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.MitSchrift200
import de.foody.feature.heute.bestand
import de.foody.feature.heute.pruefeNichtsAbgeschnitten
import de.foody.feature.heute.testDatenbank
import de.foody.feature.heute.testScan
import de.foody.feature.heute.warteAufText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class TagebuchTagScreenTest {
    private val halter = ViewModelHalter()

    @get:Rule
    val compose = createComposeRule()

    private val heute = LocalDate.of(2026, 10, 24)
    private lateinit var db: FoodyDatenbank
    private lateinit var tagebuch: TagebuchRepository
    private var aktuell = heute

    @Before
    fun setUp() {
        db = testDatenbank()
        tagebuch = TagebuchRepository(db) { 1L }
    }

    @After
    fun tearDown() {
        halter.schliessen()
        db.close()
    }

    private fun zeigen(schrift200: Boolean = false) {
        val kontext = RuntimeEnvironment.getApplication()
        val vm = halter.behalten(TagebuchTagViewModel(tagebuch, TageszielSpeicher(kontext)))
        val eintragen = halter.behalten(EintragenViewModel(SavedStateHandle(), HeuteRepository(db, EmptyCoroutineContext), tagebuch, db, testScan()))
        compose.setContent {
            FoodyTheme {
                var datum by remember { mutableStateOf(heute) }
                val inhalt = @androidx.compose.runtime.Composable {
                    TagebuchTagRoute(datum, onDatum = { datum = it; aktuell = it }, vm = vm, eintragenVm = eintragen)
                }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
    }

    @Test
    fun blaetternVorUndZurueck() {
        zeigen()
        compose.warteAufText("SAMSTAG, 24. OKTOBER 2026")
        compose.onNodeWithContentDescription("Vorheriger Tag").performClick()
        compose.warteAufText("FREITAG, 23. OKTOBER 2026")
        compose.onNodeWithContentDescription("Nächster Tag").performClick()
        compose.onNodeWithContentDescription("Nächster Tag").performClick()
        compose.warteAufText("SONNTAG, 25. OKTOBER 2026")
        assertEquals(heute.plusDays(1), aktuell)
    }

    @Test
    fun bilanzUndBalken() {
        RuntimeEnvironment.getApplication().getSharedPreferences(Einstellungsschluessel.PREFS, Context.MODE_PRIVATE)
            .edit().putInt(Einstellungsschluessel.TAGESZIEL, 2000).commit()
        runBlocking { tagebuch.frei(heute, Mahlzeit.SNACK, "Apfel", 500, null, null, null) }
        zeigen()
        compose.warteAufText("von 2.000 kcal")
        compose.onNodeWithText("Apfel").assertExists()
    }

    // Ohne Breitenangabe: Unter Robolectric läuft ein Textfeld in einem Dialog mit `w…dp`-Qualifier endlos (Testumgebung,
    // nicht die App – Prüfung auf dem Emulator in der Sichtprüfung)
    @Test
    @Config(qualifiers = "h891dp")
    fun eintragAendernSkaliert() {
        runBlocking { db.bestand(); tagebuch.ausRezept(heute, Mahlzeit.ABENDESSEN, "r1", BigDecimal.ONE) }
        zeigen()
        compose.warteAufText("717 kcal")
        compose.onNodeWithContentDescription("Reispfanne ändern").performScrollTo().performClick()
        compose.warteAufText("Portionen")
        compose.onNode(hasSetTextAction() and hasText("1")).performTextReplacement("2")
        compose.onNodeWithText("Speichern").performClick()
        compose.warteAufText("1.434 kcal")
    }

    @Test
    fun loeschenMitRueckgaengig() {
        runBlocking { tagebuch.frei(heute, Mahlzeit.SNACK, "Apfel", 52, null, null, null) }
        zeigen()
        compose.warteAufText("Apfel")
        compose.onNodeWithContentDescription("Apfel löschen").performClick()
        compose.warteAufText("Eintrag gelöscht")
        compose.waitUntil(5_000) { runBlocking { db.tagebuchDao().alle().isEmpty() } }
        compose.onNodeWithText("Rückgängig").performClick()
        compose.warteAufText("Apfel")
        assertEquals(1, runBlocking { db.tagebuchDao().alle().size })
    }

    @Test
    fun geloeschtesRezeptEintragBleibtSichtbar() {
        runBlocking {
            db.bestand()
            tagebuch.ausRezept(heute, Mahlzeit.ABENDESSEN, "r1", BigDecimal.ONE)
            db.rezeptDao().loeschen("r1")
        }
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.warteAufText("717 kcal")
    }

    @Test
    fun schrift200OhneAbschneiden() {
        runBlocking {
            db.bestand()
            tagebuch.ausRezept(heute, Mahlzeit.ABENDESSEN, "r1", BigDecimal.ONE)
            tagebuch.frei(heute, Mahlzeit.FRUEHSTUECK, "Haferflocken mit Apfel und Zimt", 380, null, null, null)
        }
        zeigen(schrift200 = true)
        compose.warteAufText("Reispfanne")
        compose.pruefeNichtsAbgeschnitten()
    }
}

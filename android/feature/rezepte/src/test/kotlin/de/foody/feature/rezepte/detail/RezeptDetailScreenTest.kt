package de.foody.feature.rezepte.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.db.ArbeitsschrittEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.RezeptEntity
import de.foody.core.data.db.RezeptZutatEntity
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.domain.MeasureUnit
import de.foody.feature.rezepte.MitSchrift200
import de.foody.feature.rezepte.bestand
import de.foody.feature.rezepte.einkauf
import de.foody.feature.rezepte.pruefeNichtsAbgeschnitten
import de.foody.feature.rezepte.testDatenbank
import de.foody.feature.rezepte.warteAufText
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
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h1600dp")
class RezeptDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: FoodyDatenbank
    private val gekocht = mutableListOf<Pair<String, Int>>()
    private val bearbeitet = mutableListOf<String>()
    private val dupliziert = mutableListOf<String>()
    private var zurueck = 0
    private var einkauf = 0

    @Before
    fun setUp() {
        db = testDatenbank()
        runBlocking {
            db.bestand()
            db.arbeitsschrittDao().upsert(ArbeitsschrittEntity("s1", "r1", 0, "Reis kochen."))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun zeigen(id: String = "r1", schrift200: Boolean = false) {
        val vm = RezeptDetailViewModel(RezeptRepository(db, FotoAblage(RuntimeEnvironment.getApplication())) { 1_000L }, db.einkauf())
        compose.setContent {
            FoodyTheme {
                val inhalt = @androidx.compose.runtime.Composable {
                    RezeptDetailRoute(
                        id, onKochen = { r, p -> gekocht += r to p }, onBearbeiten = { bearbeitet += it },
                        onDupliziert = { dupliziert += it }, onZurueck = { zurueck++ }, onEinkauf = { einkauf++ }, vm = vm,
                    )
                }
                if (schrift200) MitSchrift200(inhalt) else inhalt()
            }
        }
    }

    @Test
    fun zeigtZutatenSchritteUndKcal() {
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithText("400 g").assertExists()
        compose.onNodeWithText("Reis kochen.", substring = true).assertExists()
        compose.warteAufText("717 kcal je Portion")
    }

    @Test
    fun portionenSkalieren() {
        zeigen()
        compose.warteAufText("400 g")
        compose.onNodeWithContentDescription("Mehr Portionen").performClick()
        compose.warteAufText("600 g")
        compose.warteAufText("3 Portionen")
        compose.warteAufText("717 kcal je Portion")
    }

    @Test
    fun naehrwerteMitVollstaendigkeit() {
        runBlocking {
            db.zutatDao().upsert(ZutatEntity(id = "z-x", canonicalName = "Unbekannt", createdAt = 1, updatedAt = 1))
            db.rezeptZutatDao().upsert(RezeptZutatEntity("l2", "r1", "z-x", BigDecimal("50"), MeasureUnit.GRAM, 1, null, false))
        }
        zeigen()
        compose.warteAufText("Für 1 von 2 Zutaten fehlen Werte")
    }

    @Test
    fun energieUndBrennwertGetrennt() {
        zeigen()
        // Eigene Zeilen, damit auf dem Handy nichts mitten in der Zahl umbricht
        compose.warteAufText("717 kcal")
        compose.onNodeWithText("3.000 kJ").assertExists()
        compose.onNodeWithText("6.000 kJ").assertExists()
    }

    @Test
    fun kochenMitPortionen() {
        zeigen()
        compose.warteAufText("400 g")
        compose.onNodeWithContentDescription("Mehr Portionen").performClick()
        compose.onNodeWithText("Kochen").performClick()
        assertEquals(listOf("r1" to 3), gekocht)
    }

    @Test
    fun archivierenMitRueckgaengig() {
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithContentDescription("Archivieren").performClick()
        compose.warteAufText("Rezept archiviert")
        compose.waitUntil(5_000) { runBlocking { db.rezeptDao().nachId("r1")?.archivedAt != null } }
        compose.onNodeWithText("Rückgängig").performClick()
        compose.waitUntil(5_000) { runBlocking { db.rezeptDao().nachId("r1")?.archivedAt == null } }
    }

    @Test
    fun rueckgaengigTrifftArchiviertesRezept() {
        // Tablet: nach dem Archivieren rechts ein anderes Rezept öffnen, dann „Rückgängig“
        runBlocking {
            db.rezeptDao().upsert(
                RezeptEntity(
                    id = "r2", name = "Linsensuppe", defaultServings = 2, prepMinutes = null, cookMinutes = null,
                    imageUri = null, notes = null, tags = "", archivedAt = null, createdAt = 1, updatedAt = 1,
                ),
            )
        }
        val vm = RezeptDetailViewModel(RezeptRepository(db, FotoAblage(RuntimeEnvironment.getApplication())) { 1_000L }, db.einkauf())
        var id by mutableStateOf("r1")
        compose.setContent { FoodyTheme { RezeptDetailRoute(id, { _, _ -> }, {}, {}, {}, vm = vm) } }
        compose.warteAufText("Reispfanne")
        compose.onNodeWithContentDescription("Archivieren").performClick()
        compose.warteAufText("Rezept archiviert")
        compose.waitUntil(5_000) { runBlocking { db.rezeptDao().nachId("r1")?.archivedAt != null } }
        val r2Vorher = runBlocking { db.rezeptDao().nachId("r2") }
        id = "r2"
        compose.warteAufText("Linsensuppe")
        compose.onNodeWithText("Rückgängig").performClick()
        compose.waitUntil(5_000) { runBlocking { db.rezeptDao().nachId("r1")?.archivedAt == null } }
        assertEquals(r2Vorher, runBlocking { db.rezeptDao().nachId("r2") })
    }

    @Test
    fun archiviertZeigtWiederherstellen() {
        runBlocking { db.rezeptDao().archivSetzen("r1", 5, 5) }
        zeigen()
        compose.warteAufText("Dieses Rezept ist archiviert.")
        compose.onNodeWithText("Wiederherstellen").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { db.rezeptDao().nachId("r1")?.archivedAt == null } }
        assertNull(runBlocking { db.rezeptDao().nachId("r1")?.archivedAt })
    }

    @Test
    fun nichtGefunden() {
        zeigen(id = "gibt-es-nicht")
        compose.warteAufText("Rezept nicht gefunden")
        compose.onNodeWithText("Zurück").performClick()
        assertEquals(1, zurueck)
    }

    @Test
    fun duplizierenOeffnetKopie() {
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithContentDescription("Duplizieren").performClick()
        compose.waitUntil(5_000) { dupliziert.isNotEmpty() }
        assertNotEquals("r1", dupliziert.single())
        assertEquals(2, runBlocking { db.rezeptDao().alle().size })
    }

    @Test
    fun bearbeitenUndFavorit() {
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithContentDescription("Bearbeiten").performClick()
        assertEquals(listOf("r1"), bearbeitet)
        compose.onNodeWithContentDescription("Als Favorit merken").performClick()
        compose.waitUntil(5_000) { runBlocking { db.rezeptDao().nachId("r1")?.favorite == true } }
    }

    @Test
    fun schrift200OhneAbschneiden() {
        zeigen(schrift200 = true)
        compose.warteAufText("Reispfanne")
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test
    fun aufEinkaufslisteMitAnsehen() {
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithContentDescription("Auf die Einkaufsliste").performClick()
        compose.warteAufText("Hinzufügen")
        compose.onNodeWithContentDescription("Eine Portion mehr").performClick()
        compose.warteAufText("3 Portionen")
        compose.onNodeWithText("Hinzufügen").performClick()
        compose.warteAufText("1 Artikel auf der Einkaufsliste")
        val reis = runBlocking { db.einkaufsartikelDao().alle() }.single()
        assertEquals(0, BigDecimal("600").compareTo(reis.amount))
        compose.onNodeWithText("Ansehen").performClick()
        compose.waitUntil(5_000) { einkauf == 1 }
    }

    @Test
    fun nichtsEinzukaufen() {
        runBlocking {
            db.zutatDao().upsert(ZutatEntity(id = "z-wasser", canonicalName = "Wasser", createdAt = 1, updatedAt = 1))
            db.rezeptZutatDao().loeschenFuerRezept("r1")
            db.rezeptZutatDao().upsert(RezeptZutatEntity("l9", "r1", "z-wasser", BigDecimal("500"), MeasureUnit.MILLILITER, 0, null, false))
        }
        zeigen()
        compose.warteAufText("Reispfanne")
        compose.onNodeWithContentDescription("Auf die Einkaufsliste").performClick()
        compose.warteAufText("Hinzufügen")
        compose.onNodeWithText("Hinzufügen").performClick()
        compose.warteAufText("Nichts einzukaufen")
        assertTrue(runBlocking { db.einkaufslisteDao().alle() }.isEmpty())
    }
}

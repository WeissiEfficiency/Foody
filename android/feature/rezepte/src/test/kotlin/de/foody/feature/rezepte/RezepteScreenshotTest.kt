package de.foody.feature.rezepte

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import de.foody.core.data.db.ArbeitsschrittEntity
import de.foody.core.data.db.FoodyDatenbank
import de.foody.core.data.db.ZutatEntity
import de.foody.core.data.fotos.FotoAblage
import de.foody.core.data.rezepte.RezeptEntwurf
import de.foody.core.data.rezepte.RezeptRepository
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import de.foody.domain.NutrientBasis
import de.foody.domain.ZutatZeile
import de.foody.feature.rezepte.detail.RezeptDetailRoute
import de.foody.feature.rezepte.detail.RezeptDetailViewModel
import de.foody.feature.rezepte.editor.EditorRoute
import de.foody.feature.rezepte.editor.EditorViewModel
import de.foody.feature.rezepte.liste.RezeptListeRoute
import de.foody.feature.rezepte.liste.RezeptListeViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal

/**
 * Referenzbilder von Liste, Detail und Editor mit erfundenen Rezepten: hell/dunkel × Handy/Tablet. Robolectric rendert
 * keine Unschärfe und (unter Windows) keine Fotos – die Karten zeigen den Platzhalter.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class)
class RezepteScreenshotTest {
    private lateinit var db: FoodyDatenbank
    private lateinit var repo: RezeptRepository

    @Before
    fun setUp() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        db = testDatenbank()
        repo = RezeptRepository(db, FotoAblage(RuntimeEnvironment.getApplication())) { 1_000L }
        runBlocking { beispiele() }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun zutat(id: String, name: String, kj: String, eiweiss: String) = db.zutatDao().upsert(
        ZutatEntity(
            id = id, canonicalName = name, nutrientBasis = NutrientBasis.PER_100_G, energyKj = BigDecimal(kj),
            protein = BigDecimal(eiweiss), carbs = BigDecimal("10"), fat = BigDecimal("3"), createdAt = 1, updatedAt = 1,
        ),
    )

    private suspend fun rezept(name: String, tags: String, zeilen: List<String>, schritte: List<String>) = repo.speichern(
        RezeptEntwurf(
            id = null, name = name, portionen = 2, vorbereitung = 15, kochzeit = 25, tags = tags,
            zeilen = zeilen.map { ZutatZeile.lesen(it)!! }, schritte = schritte, notizen = null, quelle = null,
            mahlzeiten = null, gaenge = null, bild = null,
        ),
    )

    private suspend fun beispiele() {
        db.bestand()
        db.arbeitsschrittDao().upsert(ArbeitsschrittEntity("s1", "r1", 0, "Reis in Salzwasser garen."))
        db.arbeitsschrittDao().upsert(ArbeitsschrittEntity("s2", "r1", 1, "Mit dem Gemüse in der Pfanne anbraten."))
        repo.favorit("r1", true)
        zutat("z-linsen", "Rote Linsen", "1400", "24")
        zutat("z-paprika", "Paprika", "120", "1")
        zutat("z-feta", "Feta", "1100", "17")
        rezept("Linsencurry mit Kokosmilch", "vegan, schnell", listOf("200 g Rote Linsen", "1 Paprika"), listOf("Linsen kochen."))
        rezept("Ofengemüse mit Feta und Kräuterquark", "vegetarisch", listOf("2 Paprika", "200 g Feta"), listOf("Backen."))
    }

    @Test
    @Config(qualifiers = "w411dp-h1600dp")
    fun handy() = alle("handy")

    @Test
    @Config(qualifiers = "w1280dp-h1200dp")
    fun tablet() = alle("tablet")

    private val vergleich = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f))

    private fun alle(geraet: String) {
        for (dunkel in listOf(false, true)) {
            val modus = if (dunkel) "dunkel" else "hell"
            bild("liste_${modus}_$geraet", dunkel) {
                RezeptListeRoute({}, {}, {}, vm = remember { RezeptListeViewModel(repo) })
            }
            bild("detail_${modus}_$geraet", dunkel) {
                RezeptDetailRoute("r1", { _, _ -> }, {}, {}, {}, vm = remember { RezeptDetailViewModel(repo, db.einkauf()) })
            }
            bild("editor_${modus}_$geraet", dunkel) {
                val ablage = FotoAblage(RuntimeEnvironment.getApplication())
                EditorRoute("r1", {}, {}, vm = remember { EditorViewModel(repo, db, ablage) })
            }
        }
    }

    private fun bild(name: String, dunkel: Boolean, inhalt: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = vergleich) {
            FoodyTheme(dunkel = dunkel, unschaerfe = false) { FoodyHintergrund { inhalt() } }
        }
    }
}

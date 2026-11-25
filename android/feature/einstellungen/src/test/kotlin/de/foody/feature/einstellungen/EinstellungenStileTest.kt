package de.foody.feature.einstellungen

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.size
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.data.TAGESZIEL_MAX
import de.foody.core.data.TAGESZIEL_MIN
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * Einstellungen der Stile R–V in Immersiv wie in den Entwürfen: Überschriften mit Stilwort (TalkBack liest das
 * normale Wort), Schalter-Zeilen, Tagesziel mit − und + und Feld, Varianten-Leiste und Farbmuster als Optionsfelder,
 * Abgleich öffnet „Server verbinden“. Die Kacheln der Stilwahl behalten
 * beim Wechsel des Stils ihre Größe und stehen in jedem Stil in denselben Spalten und Reihen, auch auf einem schmalen
 * Handy mit großer Schrift und auf dem Tablet.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "h1600dp")
class EinstellungenStileTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var u: EinstellungenTestUmgebung
    private var server = 0

    /** Stil und Variante des Themes; [wechseln] stellt sie um, ohne den Inhalt neu zu setzen. */
    private var themaStil by mutableStateOf(FoodyStil.MILCHGLAS)
    private var themaVariante by mutableStateOf(StilVariante.IMMERSIV)

    @Before
    fun setUp() {
        u = EinstellungenTestUmgebung()
    }

    @After
    fun tearDown() = u.schliessen()

    private val keineDateien: @Composable ((Uri) -> Unit, (Uri) -> Unit) -> Dateiwahl = { _, _ -> remember { Dateiwahl({}, {}) } }

    private fun zeigen(stil: FoodyStil, schrift: Float = 1f, variante: StilVariante = StilVariante.IMMERSIV): EinstellungenViewModel {
        u.darstellung.stilSetzen(stil.name)
        u.darstellung.stilVarianteSetzen(variante.name)
        u.ziel.setzen(2000)
        u.verbinden()
        val vm = u.vm()
        themaStil = stil
        themaVariante = variante
        compose.setContent {
            FoodyTheme(stil = themaStil, variante = themaVariante, unschaerfe = false) {
                MitSchrift(schrift) { EinstellungenRoute(onServer = { server++ }, onZutaten = {}, onZurueck = {}, vm = vm, dateiwahl = keineDateien) }
            }
        }
        // Milchglas und die Sage zeigen die Überschrift nicht als Stil-Zeile; die Zeile der Produktsuche haben alle
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Online-Produktsuche"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        return vm
    }

    private fun ziel() = runBlocking { u.ziel.tagesziel().first() }

    private fun bedienen(stil: FoodyStil) {
        val vm = zeigen(stil)
        // Abgleich: Marke und Zeile, Tipp öffnet „Server verbinden“
        val verbunden = if (stil == FoodyStil.KREUZER) "VERBUNDEN · HTTP://10.0.2.2:8081" else "Verbunden mit http://10.0.2.2:8081"
        compose.onNodeWithText(verbunden).performScrollTo().performClick()
        assertEquals(1, server)
        // Variante und Farbe als Optionsfelder
        compose.onNode(isSelectable() and hasContentDescription(stil.akzente[1].anzeigename)).performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.zustand.value.akzent == stil.akzente[1].name }
        compose.onNode(isSelectable() and hasContentDescription(stil.akzente[1].anzeigename)).assertIsSelected()
        compose.onNodeWithText(if (stil == FoodyStil.KREUZER) "RUHIG" else "Ruhig").performScrollTo().performClick()
        compose.waitUntil(5_000) { vm.zustand.value.variante == StilVariante.RUHIG }
        // Tagesziel: − und + in Schritten von 50, das Feld nimmt eine eigene Zahl
        compose.onNodeWithContentDescription("Ziel erhöhen").performScrollTo().performClick()
        compose.waitUntil(5_000) { ziel() == 2050 }
        compose.onNodeWithContentDescription("Ziel senken").performClick().performClick()
        compose.waitUntil(5_000) { ziel() == 1950 }
        val feld = compose.onNode(hasSetTextAction() and hasContentDescription("Tagesziel in kcal"))
        feld.performTextReplacement("2400")
        feld.performImeAction()
        compose.waitUntil(5_000) { ziel() == 2400 }
        // Schalter-Zeile
        val schalter = compose.onNodeWithText("Online-Produktsuche").performScrollTo()
        schalter.assertIsOn()
        schalter.performClick()
        compose.waitUntil(5_000) { !u.scan.onlineSuche.value }
        schalter.assertIsOff()
    }

    @Test fun anime() = bedienen(FoodyStil.ANIME)

    @Test fun edo() = bedienen(FoodyStil.EDO)

    @Test fun bayrisch() = bedienen(FoodyStil.BAYRISCH)

    @Test fun grauen() = bedienen(FoodyStil.GRAUEN)

    @Test fun kreuzer() = bedienen(FoodyStil.KREUZER)

    /** Wie in den Entwürfen steht der Abgleich nach Darstellung, Tagesziel und Daten; nur Milchglas behält ihn oben. */
    private fun abgleichUnten(stil: FoodyStil, variante: StilVariante = StilVariante.IMMERSIV, unten: Boolean = true) {
        zeigen(stil, variante = variante)
        val kreuzer = stil == FoodyStil.KREUZER && variante == StilVariante.IMMERSIV
        val verbunden = if (kreuzer) "VERBUNDEN · HTTP://10.0.2.2:8081" else "Verbunden mit http://10.0.2.2:8081"
        // Lage ohne Zuschnitt: unten liegt der Abgleich außerhalb des Bildschirms
        val abgleich = compose.onNodeWithText(verbunden, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
        val daten = compose.onNodeWithText("Online-Produktsuche", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
        assertEquals(unten, abgleich > daten, "$stil $variante: Abgleich bei $abgleich, Online-Produktsuche bei $daten")
    }

    @Test fun sageAbgleichUnten() = abgleichUnten(FoodyStil.SAGE)

    @Test fun animeAbgleichUnten() = abgleichUnten(FoodyStil.ANIME)

    @Test fun edoAbgleichUnten() = abgleichUnten(FoodyStil.EDO)

    @Test fun bayrischAbgleichUnten() = abgleichUnten(FoodyStil.BAYRISCH)

    @Test fun grauenAbgleichUnten() = abgleichUnten(FoodyStil.GRAUEN)

    @Test fun kreuzerAbgleichUnten() = abgleichUnten(FoodyStil.KREUZER)

    @Test fun ruhigAbgleichUnten() = abgleichUnten(FoodyStil.EDO, StilVariante.RUHIG)

    @Test fun milchglasAbgleichOben() = abgleichUnten(FoodyStil.MILCHGLAS, unten = false)

    /**
     * Sage auf dem Handy des Nutzers (361 dp, Schrift 90 %): Die vier Farben stehen in einer Reihe; vorher rutschte die
     * vierte allein in eine neue Zeile und das Layout sprang.
     */
    @Test
    @Config(qualifiers = "w361dp-h1600dp")
    fun sageFarbenInEinerReihe() {
        zeigen(FoodyStil.SAGE, schrift = 0.9f)
        val oben = FoodyStil.SAGE.akzente.map {
            compose.onNode(isSelectable() and hasText(it.anzeigename)).fetchSemanticsNode().positionInRoot.y
        }
        assertEquals(1, oben.distinct().size, "Farben in Reihen: $oben")
    }

    /** Wie ein Tipp auf die Kachel (und auf die [variante]): Speicher und Theme zeigen danach [stil]. */
    private fun wechseln(stil: FoodyStil, variante: StilVariante = themaVariante) {
        u.darstellung.stilSetzen(stil.name)
        u.darstellung.stilVarianteSetzen(variante.name)
        themaStil = stil
        themaVariante = variante
        compose.waitForIdle()
    }

    /** Kachel der Stilwahl mit dem Namen [name]. */
    private fun kachel(name: String) = compose.onNode(hasText(name) and isSelectable())

    private fun groesse(name: String) = kachel(name).performScrollTo().getUnclippedBoundsInRoot().size

    /** Lage der Kachel „Edo“ (die vierte) gegenüber „Milchglas“ (der ersten): zeigt, wie die Reihen aufgeteilt sind. */
    private fun lageEdo(): DpOffset {
        val milchglas = kachel("Milchglas").performScrollTo().getUnclippedBoundsInRoot()
        val edo = kachel("Edo").getUnclippedBoundsInRoot()
        return DpOffset(edo.left - milchglas.left, edo.top - milchglas.top)
    }

    /**
     * Bayrisch zeigt die Wahl in einer Karte mit Innenabstand, Grauen nicht: Die Kacheln bleiben trotzdem gleich groß und
     * die Reihen gleich aufgeteilt. Breite eines üblichen Handys: Dort passten in Grauen vier Kacheln in die Reihe, in
     * Bayrisch nur drei.
     */
    @Test
    @Config(qualifiers = "w411dp-h1600dp")
    fun kachelnBehaltenIhreGroesse() {
        zeigen(FoodyStil.BAYRISCH)
        val bayrisch = groesse("Milchglas")
        val lageBayrisch = lageEdo()
        wechseln(FoodyStil.GRAUEN)
        val grauen = groesse("Milchglas")
        assertEquals(bayrisch.width, grauen.width, "Breite")
        assertEquals(bayrisch.height, grauen.height, "Höhe")
        // „Edo“ steht in beiden Stilen gleich weit von „Milchglas“ entfernt, also an derselben Stelle der Reihen
        assertEquals(lageBayrisch, lageEdo(), "Lage von Edo gegenüber Milchglas")
    }

    /**
     * Bei 130 % Schrift sind alle sieben Kacheln gleich groß, auch die mit dem zweizeiligen „Kosmisches Grauen“. Breite
     * eines üblichen Handys: Dort stehen drei Kacheln in einer Reihe, und der lange Name bricht um.
     */
    @Test
    @Config(qualifiers = "w411dp-h1600dp")
    fun kachelnBeiGrosserSchrift() {
        zeigen(FoodyStil.BAYRISCH, schrift = 1.3f)
        val groessen = FoodyStil.entries.associate { it.anzeigename to groesse(it.anzeigename) }
        assertEquals(1, groessen.values.toSet().size, "Größen: $groessen")
        compose.onNodeWithText(FoodyStil.GRAUEN.anzeigename).performScrollTo().assertIsDisplayed()
    }

    /** Platz einer Kachel der Stilwahl: Abstand nach links zu „Milchglas“, Breite und Reihe (0 = erste). */
    private data class Platz(val links: Dp, val breite: Dp, val reihe: Int)

    /**
     * Platz jeder Kachel; prüft dabei, dass alle sieben gleich groß sind (auch die letzte einer Reihe hat volle Breite).
     * Die Höhe steht nicht im Platz: Sie folgt der Schrift des Stils, nicht der Breite.
     */
    private fun raster(stil: FoodyStil, variante: StilVariante): List<Platz> {
        val erste = kachel("Milchglas").performScrollTo().getUnclippedBoundsInRoot()
        val grenzen = FoodyStil.entries.map { kachel(it.anzeigename).getUnclippedBoundsInRoot() }
        assertEquals(1, grenzen.map { it.size }.toSet().size, "$stil $variante Größen: $grenzen")
        val reihen = grenzen.map { it.top }.distinct().sorted()
        return grenzen.map { Platz(it.left - erste.left, it.width, reihen.indexOf(it.top)) }
    }

    /**
     * Die Wahl liegt je Stil in einer anderen Karte: Milchglas, Sage und Ruhig mit 16 dp Innenabstand, Anime und
     * Bayrisch mit 14 dp, Edo, Grauen und Kreuzer ohne Karte. In jedem Stil und jeder Variante stehen die Kacheln
     * trotzdem in denselben Spalten und Reihen und sind gleich breit. Gibt das Raster zurück.
     */
    private fun rasterUeberallGleich(schrift: Float): List<Platz> {
        zeigen(FoodyStil.MILCHGLAS, schrift)
        val bezug = raster(FoodyStil.MILCHGLAS, StilVariante.IMMERSIV)
        val abweichend = FoodyStil.entries.flatMap { stil -> StilVariante.entries.map { stil to it } }.mapNotNull { (stil, variante) ->
            wechseln(stil, variante)
            raster(stil, variante).takeIf { it != bezug }?.let { "$stil $variante: $it" }
        }
        assertEquals(emptyList<String>(), abweichend, "Bezug Milchglas: $bezug")
        return bezug
    }

    /** Schmales Handy mit 130 % Schrift: Ohne Karte passten drei Kacheln in die Reihe, in der Glaskarte nur zwei. */
    @Test
    @Config(qualifiers = "w360dp-h1600dp")
    fun kachelnSchmalMitGrosserSchrift() {
        rasterUeberallGleich(1.3f)
    }

    /**
     * Tablet: Die Kacheln nutzen die Breite der Karte. In 656 dp (720 dp Lesebreite ohne Seitenrand und Innenabstand der
     * Glaskarte) passen sechs Kacheln zu 88 dp mit 8 dp Abstand in die erste Reihe.
     */
    @Test
    @Config(qualifiers = "w1280dp-h1600dp")
    fun kachelnAufDemTablet() {
        val raster = rasterUeberallGleich(1f)
        assertEquals(6, raster.count { it.reihe == 0 }, "Kacheln in der ersten Reihe: $raster")
    }

    @Test
    fun schritte() {
        assertEquals(2050, zielSchritt("2000", 1))
        assertEquals(1950, zielSchritt("", -1))
        assertEquals(TAGESZIEL_MIN, zielSchritt("520", -1))
        assertEquals(TAGESZIEL_MAX, zielSchritt("10000", 1))
    }
}

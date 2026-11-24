package de.foody.feature.heute.stil

import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.domain.Mahlzeit
import de.foody.feature.heute.EintragKurz
import de.foody.feature.heute.HeuteAktionen
import de.foody.feature.heute.HeuteScreen
import de.foody.feature.heute.MahlzeitAbschnitt
import de.foody.feature.heute.MitSchrift
import de.foody.feature.heute.beispielZustand
import de.foody.feature.heute.pruefeNichtsAbgeschnitten
import de.foody.feature.heute.tagebuch.SofortStart
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** „Heute“ im Bayrischen Stil (Immersiv): der Maibaum statt normaler Ansicht, mit denselben Aktionen. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h1600dp")
class HeuteBayrischTest {
    @get:Rule
    val compose = createComposeRule()

    private val ausgeloest = mutableListOf<String>()
    private val aktionen = HeuteAktionen(
        onKochen = { id, p -> ausgeloest += "kochen $id $p" },
        onRezept = { ausgeloest += "rezept $it" },
        onEinkauf = { ausgeloest += "einkauf $it" },
        onAlleEintraege = { ausgeloest += "alle" },
        onEinstellungen = { ausgeloest += "einstellungen" },
        onEintragen = { ausgeloest += "eintragen ${it?.name}" },
        onSofort = { ausgeloest += "sofort $it" },
        onGegessen = { ausgeloest += "gegessen $it" },
        onNichtGegessen = { ausgeloest += "nicht ${it.planEintragId}" },
    )

    /** Mit „Foto“ (Modell da); ein Test schaltet es ab. */
    private var zustand by mutableStateOf(beispielZustand())

    @Before
    fun animationenAus() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun zeigen(variante: StilVariante = StilVariante.IMMERSIV, schrift: Float = 1f) {
        compose.setContent {
            FoodyTheme(stil = FoodyStil.BAYRISCH, variante = variante, unschaerfe = false) {
                MitSchrift(schrift) { HeuteScreen(zustand, aktionen) }
            }
        }
    }

    @Test
    fun immersivZeigtMaibaum() {
        zeigen()
        compose.onNodeWithText("Heid").assertExists()
        compose.onNodeWithText("Griaß di!").assertExists()
        compose.onNodeWithContentDescription("Heute").assertExists()
        compose.onNodeWithContentDescription("Maibaum: 860 von 2.000 kcal", substring = true).assertExists()
        compose.onNodeWithText("Heid auf d'Nacht").assertExists()
    }

    @Test
    fun ruhigZeigtSzeneSchlicht() {
        // Wie die übrigen Entwürfe T-* in Ruhig: derselbe Maibaum ohne Landschaft, „Heid“ in Alegreya statt Fraktur
        zeigen(StilVariante.RUHIG)
        compose.onNodeWithText("Heid").assertExists()
        compose.onNodeWithContentDescription("Maibaum: 860 von 2.000 kcal", substring = true).assertExists()
        compose.onNodeWithText("Heid auf d'Nacht").assertExists()
    }

    @Test
    fun alleWege() {
        zeigen()
        compose.onNodeWithContentDescription("Kochen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ofengemüse mit Feta und Kräuterquark, Rezept öffnen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Tagebuch, 860 von 2.000 kcal").performScrollTo().performClick()
        compose.onNodeWithText("+ Brotzeit eitragn").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Einkauf, 9 offen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        assertEquals(
            listOf("kochen r1 2", "rezept r1", "alle", "eintragen ${Mahlzeit.SNACK.name}", "einkauf l1", "einstellungen"),
            ausgeloest,
        )
    }

    /** Zwei runde Symbolknöpfe starten Eintragen gleich mit Scanner bzw. Kamera; „Foto“ nur mit Modell auf dem Gerät. */
    @Test
    fun scannenUndFotoGetrennt() {
        zeigen()
        compose.onNodeWithContentDescription("Scannen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Foto").performScrollTo().performClick()
        assertEquals(listOf("sofort ${SofortStart.SCANNEN}", "sofort ${SofortStart.FOTO}"), ausgeloest)
        zustand = zustand.copy(fotoMoeglich = false)
        compose.onNodeWithContentDescription("Scannen").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Foto").assertDoesNotExist()
    }

    /** Schmales Handy mit 130 % Schrift: Die Knöpfe (und alles andere) passen. */
    @Test
    @Config(qualifiers = "w360dp-h1600dp")
    fun szenenKnoepfeBei130() {
        zeigen(schrift = 1.3f)
        compose.waitForIdle()
        compose.pruefeNichtsAbgeschnitten()
    }

    @Test
    fun texte() {
        assertEquals("Passt scho – no 1.140 frei", bilanzSatz(860, 2000))
        assertEquals("Obacht – 300 drüber", bilanzSatz(2300, 2000))
        assertEquals("860 kcal heid", bilanzSatz(860, null))
        assertEquals("Passt scho – no ≤ 1.140 frei", bilanzSatz(860, 2000, vollstaendig = false))
        assertEquals("Obacht – ≥ 300 drüber", bilanzSatz(2300, 2000, vollstaendig = false))
        assertEquals("≥ 860 kcal heid", bilanzSatz(860, null, vollstaendig = false))
        assertEquals("Heid auf d'Nacht", essenKopf("ABENDESSEN", morgen = false))
        assertEquals("Morgn in da Friah", essenKopf("FRUEHSTUECK", morgen = true))
        val t = beispielZustand().tagebuch
        assertEquals("Haferflocken mit Apfel · 380", schildText(t.mahlzeiten[0]))
        assertEquals("no nix", schildText(MahlzeitAbschnitt(Mahlzeit.SNACK, emptyList(), emptyList())))
        assertEquals("Ofengemüse mit Feta und Kräuterquark · 540", schildText(t.mahlzeiten[3]))
        assertEquals(
            "860 von 2.000 kcal. Frühstück: Haferflocken mit Apfel, 380 kcal. Mittagessen: Linsensuppe, 480 kcal. " +
                "Snack: noch nichts. Abendessen: Ofengemüse mit Feta und Kräuterquark, geplant",
            maibaumBeschreibung(t),
        )
    }

    // Regel 24: Fehlen einem Eintrag die Energiewerte, ist die Summe nur eine Untergrenze („≥“)
    private val unvollstaendig = MahlzeitAbschnitt(
        Mahlzeit.FRUEHSTUECK,
        listOf(EintragKurz("e1", "Brot", 300, vollstaendig = true), EintragKurz("e2", "Kaffee", null, vollstaendig = false)),
        emptyList(),
    )

    @Test
    fun unvollstaendigZeigtMindestens() {
        assertEquals("Brot, Kaffee · ≥ 300", schildText(unvollstaendig))
        val t = beispielZustand().tagebuch
        val satz = maibaumBeschreibung(t.copy(mahlzeiten = listOf(unvollstaendig)))
        assertEquals("860 von 2.000 kcal. Frühstück: Brot, Kaffee, ≥ 300 kcal", satz)
    }
}

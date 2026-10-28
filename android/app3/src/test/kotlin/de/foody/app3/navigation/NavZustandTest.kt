package de.foody.app3.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** R-W6: Der Zustand der Bildschirme übersteht den Wechsel zwischen Leiste, Rail und Kochen-Vollbild. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp", application = android.app.Application::class)
class NavZustandTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController
    private var breite by mutableStateOf(411.dp)

    /** „Heute“ mit einem Zähler in `rememberSaveable`, sonst die Platzhalter. */
    private val bildschirme = Bildschirme.platzhalter.let { p ->
        Bildschirme(
            heute = {
                var n by rememberSaveable { mutableStateOf(0) }
                Button(onClick = { n++ }) { Text("Gezählt: $n") }
            },
            tagebuch = p.tagebuch, einstellungen = p.einstellungen, serverVerbinden = p.serverVerbinden,
            zutaten = p.zutaten, zutat = p.zutat, rezepte = p.rezepte, rezeptDetail = p.rezeptDetail,
            editor = p.editor, kochen = p.kochen, woche = p.woche, einkauf = p.einkauf,
        )
    }

    private fun starten() {
        compose.setContent {
            nav = rememberNavController()
            FoodyTheme { Box(Modifier.width(breite).fillMaxHeight()) { Hauptgeruest(nav, bildschirme) } }
        }
        compose.onNodeWithText("Gezählt: 0").performClick()
        compose.onNodeWithText("Gezählt: 1").assertExists()
    }

    @Test
    fun ueberstehtBreitenwechsel() {
        starten()
        compose.onNodeWithTag(Hauptgeruest.LEISTE).assertExists()
        breite = 900.dp
        compose.onNodeWithTag(Hauptgeruest.RAIL).assertExists()
        compose.onNodeWithText("Gezählt: 1").assertExists()
    }

    @Test
    fun ueberstehtKochenVollbild() {
        starten()
        compose.runOnIdle { nav.navigate(Kochen("r1")) }
        compose.onNodeWithTag(Hauptgeruest.LEISTE).assertDoesNotExist()
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithText("Gezählt: 1").assertExists()
    }
}

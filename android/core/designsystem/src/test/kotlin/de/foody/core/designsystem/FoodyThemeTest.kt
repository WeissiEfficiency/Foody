package de.foody.core.designsystem

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.token.FigtreeFamilie
import de.foody.core.designsystem.token.FoodyFarben
import de.foody.core.designsystem.token.FoodyTypografie
import de.foody.core.designsystem.token.FrauncesFamilie
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class FoodyThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun themeLiefertHelleFarben() {
        var farben: FoodyFarben? = null
        var primaer: Any? = null
        compose.setContent {
            FoodyTheme(dunkel = false) {
                farben = FoodyTheme.farben
                primaer = MaterialTheme.colorScheme.primary
            }
        }
        assertEquals(FoodyFarben.Hell, farben)
        assertEquals(FoodyFarben.Hell.primaer, primaer)
    }

    @Test
    fun themeLiefertDunkleFarben() {
        var farben: FoodyFarben? = null
        var dunkel = false
        compose.setContent {
            FoodyTheme(dunkel = true) {
                farben = FoodyTheme.farben
                dunkel = FoodyTheme.dunkel
            }
        }
        assertEquals(FoodyFarben.Dunkel, farben)
        assertTrue(dunkel)
    }

    @Test
    fun ueberschriftenInFraunces() {
        assertEquals(FrauncesFamilie, FoodyTypografie.titleLarge.fontFamily)
        assertEquals(FrauncesFamilie, FoodyTypografie.displayLarge.fontFamily)
        assertEquals(FigtreeFamilie, FoodyTypografie.titleMedium.fontFamily)
        assertEquals(FigtreeFamilie, FoodyTypografie.bodyLarge.fontFamily)
    }

    @Test
    fun unschaerfeAusBeiAnimationenAus() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var unschaerfe = true
        compose.setContent { FoodyTheme { unschaerfe = FoodyTheme.unschaerfe } }
        assertFalse(unschaerfe)
    }

    @Test
    fun schriftenLadenOhneFehler() {
        compose.setContent {
            FoodyTheme { Text("Senf-Krusten-Braten", style = FoodyTypografie.headlineLarge) }
        }
        compose.onNodeWithText("Senf-Krusten-Braten").assertExists()
    }
}

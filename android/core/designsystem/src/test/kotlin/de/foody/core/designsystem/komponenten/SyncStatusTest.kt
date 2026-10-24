package de.foody.core.designsystem.komponenten

import android.provider.Settings
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.foody.core.designsystem.FoodyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
class SyncStatusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun textJeZustand() {
        compose.setContent {
            FoodyTheme {
                androidx.compose.foundation.layout.Column {
                    SyncStatus(SyncAnzeige.Synchron)
                    SyncStatus(SyncAnzeige.Wartet)
                    SyncStatus(SyncAnzeige.Fehler("Anmeldung abgelaufen"))
                }
            }
        }
        compose.onNodeWithText("Synchron").assertExists()
        compose.onNodeWithText("Wartet auf Netz").assertExists()
        compose.onNodeWithText("Anmeldung abgelaufen").assertExists()
    }

    @Test
    fun drehtNurWennLaeuftUndAnimationenErlaubt() {
        assertTrue(symbolDreht(SyncAnzeige.Laeuft, animationen = true))
        assertFalse(symbolDreht(SyncAnzeige.Laeuft, animationen = false))
        assertFalse(symbolDreht(SyncAnzeige.Synchron, animationen = true))
    }

    @Test
    fun laeuftOhneAnimationenRendert() {
        val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.setContent { FoodyTheme { SyncStatus(SyncAnzeige.Laeuft) } }
        compose.onNodeWithText("Läuft …").assertExists()
    }
}

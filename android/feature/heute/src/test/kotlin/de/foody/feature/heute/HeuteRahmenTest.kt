package de.foody.feature.heute

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Der Rahmen der Heute-Szenen endet über der schwebenden Leiste, auch wenn die Systemleiste hoch ist: Die Leiste sitzt
 * über der Systemleiste. Ohne deren Höhe lagen „Rezept ansehen“ und „Scannen · Foto“ in der Sage bei
 * Drei-Tasten-Navigation halb unter der Leiste (Gerätetest 2026-11-16).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h800dp-mdpi")
class HeuteRahmenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun endeLiegtUeberLeisteUndSystemleiste() {
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        compose.setContent {
            HeuteRahmen(statusLeiste = false) { m -> Column(m) { Box(Modifier.fillMaxWidth().height(2000.dp).testTag("inhalt")) } }
        }
        // Drei-Tasten-Navigation: 48 dp (mdpi, also 48 px)
        compose.runOnUiThread {
            val einsaetze = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                .build()
            ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, einsaetze)
        }
        compose.waitForIdle()
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        compose.waitForIdle()

        val luecke = compose.onRoot().getBoundsInRoot().bottom - compose.onNodeWithTag("inhalt").getBoundsInRoot().bottom
        assertEquals(PLATZ_FUER_LEISTE + 48.dp, luecke)
    }
}

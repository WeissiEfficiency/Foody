package de.foody.app3

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.foody.app3.navigation.Hauptgeruest
import de.foody.app3.navigation.deepLinkOeffnen
import de.foody.core.data.DarstellungSpeicher
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import de.foody.feature.kochen.timer.KochTimerDienst
import de.foody.feature.kochen.timer.KochTimerSpeicher
import javax.inject.Inject

/**
 * Einzige Activity der V3. `singleTask`: Ein Deep Link bei laufender App kommt über `onNewIntent` und wird an die
 * Navigation weitergereicht; beim Kaltstart liest die Navigation den Start-Intent selbst.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var kochTimer: KochTimerSpeicher
    @Inject lateinit var darstellung: DarstellungSpeicher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val nav = rememberNavController()
            DisposableEffect(nav) {
                val weiter = Consumer<Intent> { nav.deepLinkOeffnen(it) }
                addOnNewIntentListener(weiter)
                onDispose { removeOnNewIntentListener(weiter) }
            }
            // Startwert sofort aus den Prefs: kein Aufblitzen der falschen Darstellung beim Start
            val d by remember { darstellung.darstellung() }.collectAsStateWithLifecycle(darstellung.aktuell())
            val dunkel = istDunkel(d)
            SystemleistenPassend(dunkel)
            FoodyTheme(dunkel = dunkel) { FoodyHintergrund { Hauptgeruest(nav) } }
        }
    }

    override fun onStart() {
        super.onStart()
        // Laufende Kochtimer nach einem Neustart wieder aufnehmen – nur im Vordergrund darf der Dienst starten
        KochTimerDienst.wennNoetigStarten(this, kochTimer)
    }
}

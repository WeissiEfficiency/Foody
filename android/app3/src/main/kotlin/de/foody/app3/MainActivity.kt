package de.foody.app3

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.core.util.Consumer
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.foody.app3.navigation.Hauptgeruest
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val nav = rememberNavController()
            DisposableEffect(nav) {
                val weiter = Consumer<Intent> { nav.handleDeepLink(it) }
                addOnNewIntentListener(weiter)
                onDispose { removeOnNewIntentListener(weiter) }
            }
            FoodyTheme { FoodyHintergrund { Hauptgeruest(nav) } }
        }
    }

    override fun onStart() {
        super.onStart()
        // Laufende Kochtimer nach einem Neustart wieder aufnehmen – nur im Vordergrund darf der Dienst starten
        KochTimerDienst.wennNoetigStarten(this, kochTimer)
    }
}

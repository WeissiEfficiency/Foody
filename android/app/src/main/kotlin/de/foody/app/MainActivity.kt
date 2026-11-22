package de.foody.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.foody.app.navigation.Hauptgeruest
import de.foody.app.navigation.LinkEingang
import de.foody.app.navigation.widgetStart
import de.foody.app.navigation.widgetStartOeffnen
import de.foody.app.widget.alleAktualisieren
import de.foody.core.data.DarstellungSpeicher
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.token.Farbwelt
import de.foody.feature.kochen.timer.KochTimerDienst
import de.foody.feature.kochen.timer.KochTimerSpeicher
import javax.inject.Inject

/**
 * Einzige Activity der V3. `singleTask`: Ein Deep Link bei laufender App kommt über `onNewIntent` und wird über
 * [LinkEingang] an die Navigation weitergereicht; beim Kaltstart liest die Navigation den Start-Intent selbst (außer
 * den Links der Widgets, die gibt die Activity weiter).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var kochTimer: KochTimerSpeicher

    @Inject lateinit var darstellung: DarstellungSpeicher

    /** Links über `onNewIntent`; auch die, die nach einem Prozessende vor dem ersten Aufbau kommen. */
    private val links = LinkEingang()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Kaltstart von einem Widget (nicht nach Drehen/Prozessende: Eintragen liegt dann schon im Stapel; ein neuer Tipp
        // auf eine im Hintergrund beendete App kommt über `onNewIntent`, noch vor dem ersten Aufbau)
        val widgetBeimStart = if (savedInstanceState == null) intent.widgetStart() else null
        addOnNewIntentListener(links::ankommen)
        setContent {
            val nav = rememberNavController()
            // „KI-Foto“ und „Scannen“: „Heute“ (bzw. die Seite von vorher) bleibt unsichtbar, bis Eintragen obenauf liegt
            var verdeckt by remember { mutableStateOf(widgetBeimStart?.sofort != null || links.sofortWartet()) }
            LaunchedEffect(nav) {
                widgetBeimStart?.let(nav::widgetStartOeffnen)
                links.oeffnen(nav)
                verdeckt = false
                links.laufendOeffnen(nav)
            }
            // Startwert sofort aus den Prefs: kein Aufblitzen der falschen Darstellung beim Start
            val d by remember { darstellung.darstellung() }.collectAsStateWithLifecycle(remember { darstellung.aktuell() })
            val farbwelt by remember { darstellung.farbwelt() }.collectAsStateWithLifecycle(remember { darstellung.farbweltAktuell() })
            val wahl by remember { darstellung.stil() }.collectAsStateWithLifecycle(remember { darstellung.stilAktuell() })
            val stil = FoodyStil.ausName(wahl.stil)
            // Knopf-Widgets tragen Farbe und Wort des Stils: nach jedem Wechsel neu zeichnen
            LaunchedEffect(wahl) { alleAktualisieren(applicationContext) }
            val variante = StilVariante.ausName(wahl.variante)
            val modusDunkel = istDunkel(d)
            // Stile haben einen festen Grund; die Systemleisten folgen ihm
            SystemleistenPassend(stil.dunkel(variante, modusDunkel))
            FoodyTheme(dunkel = modusDunkel, farbwelt = Farbwelt.ausName(farbwelt), stil = stil, variante = variante, akzent = wahl.akzent) {
                FoodyHintergrund {
                    Box(Modifier.graphicsLayer { alpha = if (verdeckt) 0f else 1f }) { Hauptgeruest(nav) }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Laufende Kochtimer nach einem Neustart wieder aufnehmen – nur im Vordergrund darf der Dienst starten
        KochTimerDienst.wennNoetigStarten(this, kochTimer)
    }
}

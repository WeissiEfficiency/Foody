package de.foody.app.navigation

import android.content.Intent
import androidx.navigation.NavHostController
import kotlinx.coroutines.channels.Channel

/**
 * Links bei laufender App (`onNewIntent`), gepuffert, bis die Navigation sie übernimmt. Nach einem Prozessende im
 * Hintergrund baut `singleTask` die Activity neu auf und liefert einen Widget-Link noch vor dem ersten Aufbau der
 * Navigation – ohne Puffer ginge der Tipp verloren. Jeder Link wird genau einmal geöffnet, der Reihe nach.
 * Nur auf dem Hauptthread benutzen (`onNewIntent` und die Effekte der Komposition).
 */
class LinkEingang {
    private val wartend = ArrayDeque<Intent>()

    /** Meldet [laufendOeffnen], dass etwas angekommen ist. */
    private val angekommen = Channel<Unit>(Channel.CONFLATED)

    fun ankommen(intent: Intent) {
        wartend.addLast(intent)
        angekommen.trySend(Unit)
    }

    /** Wartet ein Widget-Link, der gleich Kamera oder Scanner öffnet? Dann bleibt „Heute“ bis dahin verdeckt. */
    fun sofortWartet(): Boolean = wartend.any { it.widgetStart()?.sofort != null }

    /** Öffnet die wartenden Links der Reihe nach in [nav] ([deepLinkOeffnen]). */
    fun oeffnen(nav: NavHostController) {
        while (true) nav.deepLinkOeffnen(wartend.removeFirstOrNull() ?: return)
    }

    /** Wie [oeffnen], danach jeden Link, sobald er ankommt; läuft, bis die Navigation geht (Abbruch des Effekts). */
    suspend fun laufendOeffnen(nav: NavHostController): Nothing {
        while (true) {
            oeffnen(nav)
            angekommen.receive()
        }
    }
}

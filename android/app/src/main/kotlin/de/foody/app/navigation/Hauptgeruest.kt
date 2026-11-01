package de.foody.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.foody.core.designsystem.komponenten.FoodyNavigationsRail
import de.foody.core.designsystem.komponenten.FoodyNavigationsleiste
import de.foody.core.designsystem.komponenten.NavZiel
import de.foody.core.designsystem.token.FoodyBreiten

/** Ein Tab mit seiner Route. */
private class Tab(val route: Any, val ziel: NavZiel)

private val tabs = listOf(
    Tab(Heute, NavZiel("Heute", Icons.Outlined.LightMode)),
    Tab(Rezepte, NavZiel("Rezepte", Icons.Outlined.Restaurant)),
    Tab(Woche, NavZiel("Woche", Icons.Outlined.CalendarMonth)),
    Tab(Einkauf, NavZiel("Einkauf", Icons.Outlined.ShoppingBag)),
)

/**
 * Index des Tabs, zu dem der Stapel gehört. Ein Tab-Wechsel nimmt immer bis zur Wurzel „Heute“ zurück, also liegt
 * höchstens ein anderer Tab im Stapel; sonst gehört alles (auch Details aus „Heute“) zu „Heute“.
 */
private fun aktiverTab(nav: NavHostController): Int =
    (1 until tabs.size).firstOrNull { i -> runCatching { nav.getBackStackEntry(tabs[i].route) }.isSuccess } ?: 0

/**
 * Rahmen der App: vier Tabs Heute · Rezepte · Woche · Einkauf. Handy: schwebende [FoodyNavigationsleiste] unten über
 * dem Inhalt; ab 840 dp Breite [FoodyNavigationsRail] links neben dem Inhalt.
 */
@Composable
fun Hauptgeruest(
    navController: NavHostController = rememberNavController(),
    bildschirme: Bildschirme = Bildschirme.echt,
) {
    val eintrag by navController.currentBackStackEntryAsState()
    val aktiv = remember(eintrag) { aktiverTab(navController) }
    // Der Kochmodus ist ein Vollbild ohne Navigation
    val vollbild = eintrag?.destination?.hasRoute<Kochen>() == true
    val waehlen: (Int) -> Unit = { i ->
        when {
            // Heute ist die Wurzel; der aktive Tab erneut: zurück an seinen Anfang
            i == 0 -> navController.popBackStack<Heute>(inclusive = false)
            i == aktiv -> navController.popBackStack(tabs[i].route, inclusive = false)
            else -> navController.navigate(tabs[i].route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val ziele = tabs.map { it.ziel }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val breit = !vollbild && maxWidth >= FoodyBreiten.zweispaltig
        // Ein NavHost an fester Stelle: Vollbild, Drehen oder Fenstergröße tauschen nur die Navigation daneben aus,
        // der Zustand der Bildschirme (`rememberSaveable`) bleibt.
        Row(Modifier.fillMaxSize()) {
            if (breit) {
                Box(Modifier.fillMaxHeight().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                    FoodyNavigationsRail(ziele, aktiv, waehlen, Modifier.wrapContentHeight().testTag(Hauptgeruest.RAIL))
                }
            }
            FoodyNavHost(navController, bildschirme, Modifier.weight(1f))
        }
        if (!vollbild && !breit) {
            FoodyNavigationsleiste(
                ziele, aktiv, waehlen,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().testTag(Hauptgeruest.LEISTE),
            )
        }
    }
}

object Hauptgeruest {
    const val LEISTE = "navigationsleiste"
    const val RAIL = "navigationsrail"
}

package de.foody.app3.navigation

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.foody.core.designsystem.komponenten.FoodyNavigationsRail
import de.foody.core.designsystem.komponenten.FoodyNavigationsleiste
import de.foody.core.designsystem.komponenten.NavZiel
import kotlin.reflect.KClass

/** Ein Tab mit seiner Route und den Zielen, bei denen er als aktiv gilt (z. B. Rezept-Detail → „Rezepte“). */
private class Tab(val route: Any, val ziel: NavZiel, val auch: List<KClass<*>> = emptyList())

private val tabs = listOf(
    Tab(Heute, NavZiel("Heute", Icons.Outlined.LightMode), listOf(TagebuchTag::class, Einstellungen::class)),
    Tab(Rezepte, NavZiel("Rezepte", Icons.Outlined.Restaurant), listOf(RezeptDetail::class, Kochen::class)),
    Tab(Woche, NavZiel("Woche", Icons.Outlined.CalendarMonth)),
    Tab(Einkauf, NavZiel("Einkauf", Icons.Outlined.ShoppingBag), listOf(EinkaufListe::class)),
)

/**
 * Rahmen der App: vier Tabs Heute · Rezepte · Woche · Einkauf. Handy: schwebende [FoodyNavigationsleiste] unten über
 * dem Inhalt; ab 840 dp Breite [FoodyNavigationsRail] links neben dem Inhalt.
 */
@Composable
fun Hauptgeruest(navController: NavHostController = rememberNavController()) {
    val eintrag by navController.currentBackStackEntryAsState()
    val aktiv = tabs.indexOfFirst { tab -> eintrag?.destination?.gehoertZu(tab) == true }
    val waehlen: (Int) -> Unit = { i ->
        navController.navigate(tabs[i].route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val ziele = tabs.map { it.ziel }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= BREIT) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                    FoodyNavigationsRail(ziele, aktiv, waehlen, Modifier.wrapContentHeight().testTag(Hauptgeruest.RAIL))
                }
                FoodyNavHost(navController, Modifier.weight(1f))
            }
        } else {
            FoodyNavHost(navController, Modifier.fillMaxSize())
            FoodyNavigationsleiste(
                ziele, aktiv, waehlen,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().testTag(Hauptgeruest.LEISTE),
            )
        }
    }
}

private fun NavDestination.gehoertZu(tab: Tab): Boolean =
    hierarchy.any { d -> d.hasRoute(tab.route::class) || tab.auch.any { d.hasRoute(it) } }

object Hauptgeruest {
    const val LEISTE = "navigationsleiste"
    const val RAIL = "navigationsrail"
}

/** Ab dieser Breite steht die Navigation links (Material „expanded“). */
private val BREIT = 840.dp

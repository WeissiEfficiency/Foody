package de.foody.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.foody.core.designsystem.komponenten.FoodyNavigationsRail
import de.foody.core.designsystem.komponenten.FoodyNavigationsleiste
import de.foody.core.designsystem.komponenten.LEISTE_REGELHOEHE
import de.foody.core.designsystem.komponenten.LocalLeistenZuwachs
import de.foody.core.designsystem.komponenten.LocalMitLeiste
import de.foody.core.designsystem.komponenten.NavZiel
import de.foody.core.designsystem.stil.stilText
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.feature.heute.tagebuch.EintragenStart
import java.time.LocalDate

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

/** Stelle von „Eintragen“ in der Leiste: zwischen Heute und Rezepte. Ein Knopf, kein Tab. */
private const val EINTRAGEN = 1

/** Stelle in der Leiste → Tab; `null` für „Eintragen“. */
private fun tabAnStelle(stelle: Int): Int? = when {
    stelle < EINTRAGEN -> stelle
    stelle == EINTRAGEN -> null
    else -> stelle - 1
}

/** Tab → Stelle in der Leiste. */
private fun stelleVonTab(tab: Int): Int = if (tab < EINTRAGEN) tab else tab + 1

/**
 * Rahmen der App: vier Tabs Heute · Rezepte · Woche · Einkauf und dazwischen der Knopf „Eintragen“ (öffnet Eintragen
 * für heute, von jeder Seite aus). Handy: schwebende [FoodyNavigationsleiste] unten über dem Inhalt; ab 840 dp Breite
 * [FoodyNavigationsRail] links neben dem Inhalt.
 */
@Composable
fun Hauptgeruest(
    navController: NavHostController = rememberNavController(),
    bildschirme: Bildschirme = Bildschirme.echt,
) {
    val eintrag by navController.currentBackStackEntryAsState()
    val aktiv = remember(eintrag) { aktiverTab(navController) }
    // Der Kochmodus ist ein Vollbild ohne Navigation; Eintragen behält sie
    val vollbild = eintrag?.destination?.hasRoute<Kochen>() == true
    val inEintragen = eintrag?.destination?.hasRoute<Eintragen>() == true
    val tabWaehlen: (Int) -> Unit = { i ->
        // Ein Reiter schließt Eintragen, sonst läge es beim Zurückkehren noch im Stapel des Reiters
        if (navController.currentBackStackEntry?.destination?.hasRoute<Eintragen>() == true) navController.popBackStack()
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
    // „Eintragen“ legt die Seite über den Stapel des Tabs: Speichern und Zurück führen wieder dorthin
    val waehlen: (Int) -> Unit = { stelle ->
        tabAnStelle(stelle)?.let(tabWaehlen) ?: navController.eintragenOeffnen(EintragenStart(LocalDate.now()))
    }
    // Wörter je Stil in Immersiv (Bayrisch „Heid · Eitragn · Rezepte · Wochn · Eikaufn“,
    // Sternenkreuzer „Brücke · Erfassen · Archiv · Kurs · Fracht“)
    val woerter = listOf(
        stilText("Heute", bayrisch = "Heid", kreuzer = "Brücke"),
        stilText("Rezepte", kreuzer = "Archiv"),
        stilText("Woche", bayrisch = "Wochn", kreuzer = "Kurs"),
        stilText("Einkauf", bayrisch = "Eikaufn", kreuzer = "Fracht"),
    )
    val eintragen = stilText("Eintragen", bayrisch = "Eitragn", kreuzer = "Erfassen")
    val ziele = tabs.mapIndexed { i, t -> if (woerter[i] == t.ziel.text) t.ziel else t.ziel.copy(text = woerter[i], beschreibung = t.ziel.text) }
        .toMutableList()
        .apply {
            add(
                EINTRAGEN,
                NavZiel(eintragen, Icons.Outlined.AddCircleOutline, beschreibung = "Eintragen".takeIf { it != eintragen }, aktion = true),
            )
        }
    val aktivStelle = if (inEintragen) EINTRAGEN else stelleVonTab(aktiv)
    val dichte = LocalDensity.current
    var leiste by remember { mutableStateOf(LEISTE_REGELHOEHE) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val breit = !vollbild && maxWidth >= FoodyBreiten.zweispaltig
        val mitLeiste = !vollbild && !breit
        val zuwachs = if (mitLeiste) (leiste - LEISTE_REGELHOEHE).coerceAtLeast(0.dp) else 0.dp
        // Ein NavHost an fester Stelle: Vollbild, Drehen oder Fenstergröße tauschen nur die Navigation daneben aus,
        // der Zustand der Bildschirme (`rememberSaveable`) bleibt.
        Row(Modifier.fillMaxSize()) {
            if (breit) {
                // Mittig; reicht die Höhe nicht (Handy quer, große Schrift), lässt sich die Rail schieben, statt den
                // letzten Punkt zusammenzudrücken
                BoxWithConstraints(Modifier.fillMaxHeight().safeDrawingPadding()) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        FoodyNavigationsRail(ziele, aktivStelle, waehlen, Modifier.testTag(Hauptgeruest.RAIL))
                    }
                }
            }
            CompositionLocalProvider(LocalLeistenZuwachs provides zuwachs, LocalMitLeiste provides mitLeiste) {
                FoodyNavHost(navController, bildschirme, Modifier.weight(1f))
            }
        }
        if (mitLeiste) {
            FoodyNavigationsleiste(
                ziele, aktivStelle, waehlen,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .onSizeChanged { leiste = with(dichte) { it.height.toDp() } }
                    .testTag(Hauptgeruest.LEISTE),
            )
        }
    }
}

object Hauptgeruest {
    const val LEISTE = "navigationsleiste"
    const val RAIL = "navigationsrail"
}

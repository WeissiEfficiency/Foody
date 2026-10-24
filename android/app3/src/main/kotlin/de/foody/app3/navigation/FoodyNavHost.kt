package de.foody.app3.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import java.time.LocalDate

/** Schema der Deep Links (die v1 hatte keine). */
const val SCHEMA = "foody://"

/**
 * Alle Ziele der V3. Deep Links: `foody://rezept/{id}`, `foody://einkauf/{listeId}`,
 * `foody://kochen/{rezeptId}?portionen={n}`, `foody://tagebuch/{datum}`. Rezepte, Woche, Einkauf und Kochen sind bis
 * Phase 6 Platzhalter.
 */
@Composable
fun FoodyNavHost(nav: NavHostController, modifier: Modifier = Modifier) {
    NavHost(nav, startDestination = Heute, modifier = modifier) {
        composable<Heute> { Platzhalter("Heute") }
        composable<Rezepte> { Platzhalter("Rezepte") }
        composable<Woche> { Platzhalter("Woche") }
        composable<Einkauf> { Platzhalter("Einkauf") }
        composable<Einstellungen> { Platzhalter("Einstellungen") }
        composable<RezeptDetail>(deepLinks = listOf(navDeepLink<RezeptDetail>(basePath = "${SCHEMA}rezept"))) {
            Platzhalter("Rezept", "Rezept ${it.toRoute<RezeptDetail>().id}")
        }
        composable<Kochen>(deepLinks = listOf(navDeepLink<Kochen>(basePath = "${SCHEMA}kochen"))) {
            val ziel = it.toRoute<Kochen>()
            Platzhalter("Kochen", "Rezept ${ziel.rezeptId}" + ziel.portionen?.let { n -> " · $n ${if (n == 1) "Portion" else "Portionen"}" }.orEmpty())
        }
        composable<EinkaufListe>(deepLinks = listOf(navDeepLink<EinkaufListe>(basePath = "${SCHEMA}einkauf"))) {
            Platzhalter("Einkaufsliste", "Liste ${it.toRoute<EinkaufListe>().listeId}")
        }
        composable<TagebuchTag>(deepLinks = listOf(navDeepLink<TagebuchTag>(basePath = "${SCHEMA}tagebuch"))) {
            Platzhalter("Tagebuch", "Tagebuch ${tagebuchDatum(it.toRoute<TagebuchTag>().datum, LocalDate.now())}")
        }
    }
}

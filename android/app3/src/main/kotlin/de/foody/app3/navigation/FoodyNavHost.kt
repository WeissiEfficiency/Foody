package de.foody.app3.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import de.foody.feature.einstellungen.VerbindenRoute
import de.foody.feature.heute.HeuteRoute
import de.foody.feature.heute.tagebuch.TagebuchTagRoute
import java.time.LocalDate

/** Die Bildschirme mit ViewModel (Hilt); Navigationstests setzen [Bildschirme.platzhalter] ein. */
class Bildschirme(
    val heute: @Composable (NavHostController) -> Unit,
    val tagebuch: @Composable (LocalDate, NavHostController) -> Unit,
    val einstellungen: @Composable (NavHostController) -> Unit,
) {
    companion object {
        val echt = Bildschirme(
            heute = { nav ->
                HeuteRoute(
                    onKochen = { id, portionen -> nav.navigate(Kochen(id, portionen)) },
                    onRezept = { nav.navigate(RezeptDetail(it)) },
                    onEinkauf = { nav.navigate(EinkaufListe(it)) },
                    onAlleEintraege = { nav.navigate(TagebuchTag(LocalDate.now().toString())) },
                    onEinstellungen = { nav.navigate(Einstellungen) },
                )
            },
            tagebuch = { datum, nav ->
                // Blättern ersetzt den Tag, statt ihn auf den Stapel zu legen: „Zurück“ führt zu „Heute“
                TagebuchTagRoute(datum, onDatum = { neu ->
                    nav.navigate(TagebuchTag(neu.toString())) { popUpTo<TagebuchTag> { inclusive = true } }
                })
            },
            einstellungen = { nav -> VerbindenRoute(onZurueck = { nav.popBackStack() }) },
        )
        val platzhalter = Bildschirme(
            heute = { Platzhalter("Heute") },
            tagebuch = { datum, _ -> Platzhalter("Tagebuch", "Tagebuch $datum") },
            einstellungen = { Platzhalter("Einstellungen") },
        )
    }
}

/** Schema der Deep Links (die v1 hatte keine). */
const val SCHEMA = "foody://"

/**
 * Alle Ziele der V3. Deep Links: `foody://rezept/{id}`, `foody://einkauf/{listeId}`,
 * `foody://kochen/{rezeptId}?portionen={n}`, `foody://tagebuch/{datum}` (ungültiges Datum → heute). Rezepte, Woche,
 * Einkauf und Kochen sind bis Phase 6 Platzhalter.
 */
@Composable
fun FoodyNavHost(nav: NavHostController, bildschirme: Bildschirme, modifier: Modifier = Modifier) {
    NavHost(nav, startDestination = Heute, modifier = modifier) {
        composable<Heute> { bildschirme.heute(nav) }
        composable<Rezepte> { Platzhalter("Rezepte") }
        composable<Woche> { Platzhalter("Woche") }
        composable<Einkauf> { Platzhalter("Einkauf") }
        composable<Einstellungen> { bildschirme.einstellungen(nav) }
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
            bildschirme.tagebuch(tagebuchDatum(it.toRoute<TagebuchTag>().datum, LocalDate.now()), nav)
        }
    }
}

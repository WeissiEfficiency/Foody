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
import de.foody.feature.rezepte.RezepteBereich
import de.foody.feature.rezepte.detail.RezeptDetailRoute
import de.foody.feature.rezepte.editor.EditorRoute
import java.time.LocalDate

/** Die Bildschirme mit ViewModel (Hilt); Navigationstests setzen [Bildschirme.platzhalter] ein. */
class Bildschirme(
    val heute: @Composable (NavHostController) -> Unit,
    val tagebuch: @Composable (LocalDate, NavHostController) -> Unit,
    val einstellungen: @Composable (NavHostController) -> Unit,
    val rezepte: @Composable (NavHostController) -> Unit,
    val rezeptDetail: @Composable (String, NavHostController) -> Unit,
    /** Editor; ID `null` = neues Rezept. */
    val editor: @Composable (String?, NavHostController) -> Unit,
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
            rezepte = { nav ->
                RezepteBereich(
                    onOeffnen = { nav.navigate(RezeptDetail(it)) },
                    onNeu = { nav.navigate(RezeptNeu) },
                    onKochen = { id, portionen -> nav.navigate(Kochen(id, portionen)) },
                )
            },
            rezeptDetail = { id, nav ->
                RezeptDetailRoute(
                    id,
                    onKochen = { r, portionen -> nav.navigate(Kochen(r, portionen)) },
                    onBearbeiten = { nav.navigate(RezeptBearbeiten(it)) },
                    // Die Kopie ersetzt das Original im Stapel: „Zurück“ führt dorthin, woher man kam
                    onDupliziert = { kopie -> nav.navigate(RezeptDetail(kopie)) { popUpTo<RezeptDetail> { inclusive = true } } },
                    onZurueck = { nav.popBackStack() },
                )
            },
            editor = { id, nav ->
                EditorRoute(
                    id,
                    onFertig = { gespeichert ->
                        // Neu: das Rezept ersetzt den Editor; Bearbeiten: zurück zum Detail, das sich selbst aktualisiert
                        if (id == null) nav.navigate(RezeptDetail(gespeichert)) { popUpTo<RezeptNeu> { inclusive = true } } else nav.popBackStack()
                    },
                    onZurueck = { nav.popBackStack() },
                )
            },
        )
        val platzhalter = Bildschirme(
            heute = { Platzhalter("Heute") },
            tagebuch = { datum, _ -> Platzhalter("Tagebuch", "Tagebuch $datum") },
            einstellungen = { Platzhalter("Einstellungen") },
            rezepte = { Platzhalter("Rezepte", "Rezeptliste") },
            rezeptDetail = { id, _ -> Platzhalter("Rezept", "Rezept $id") },
            editor = { id, _ -> Platzhalter("Editor", if (id == null) "Neues Rezept" else "Rezept $id bearbeiten") },
        )
    }
}

/** Schema der Deep Links (die v1 hatte keine). */
const val SCHEMA = "foody://"

/**
 * Alle Ziele der V3. Deep Links: `foody://rezept/{id}`, `foody://einkauf/{listeId}`,
 * `foody://kochen/{rezeptId}?portionen={n}`, `foody://tagebuch/{datum}` (ungültiges Datum → heute). Woche, Einkauf
 * und Kochen sind bis 6b–6d Platzhalter.
 */
@Composable
fun FoodyNavHost(nav: NavHostController, bildschirme: Bildschirme, modifier: Modifier = Modifier) {
    NavHost(nav, startDestination = Heute, modifier = modifier) {
        composable<Heute> { bildschirme.heute(nav) }
        composable<Rezepte> { bildschirme.rezepte(nav) }
        composable<Woche> { Platzhalter("Woche") }
        composable<Einkauf> { Platzhalter("Einkauf") }
        composable<Einstellungen> { bildschirme.einstellungen(nav) }
        composable<RezeptDetail>(deepLinks = listOf(navDeepLink<RezeptDetail>(basePath = "${SCHEMA}rezept"))) {
            bildschirme.rezeptDetail(it.toRoute<RezeptDetail>().id, nav)
        }
        composable<RezeptNeu> { bildschirme.editor(null, nav) }
        composable<RezeptBearbeiten> { bildschirme.editor(it.toRoute<RezeptBearbeiten>().id, nav) }
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

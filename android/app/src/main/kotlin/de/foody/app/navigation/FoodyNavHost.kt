package de.foody.app.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navOptions
import androidx.navigation.toRoute
import de.foody.feature.einkauf.EinkaufRoute
import de.foody.feature.einstellungen.EinstellungenRoute
import de.foody.feature.einstellungen.VerbindenRoute
import de.foody.feature.heute.HeuteRoute
import de.foody.feature.kochen.KochenRoute
import de.foody.feature.heute.tagebuch.TagebuchTagRoute
import de.foody.feature.rezepte.RezepteBereich
import de.foody.feature.rezepte.detail.RezeptDetailRoute
import de.foody.feature.rezepte.editor.EditorRoute
import de.foody.feature.woche.WocheRoute
import de.foody.feature.zutaten.ZutatEditorRoute
import de.foody.feature.zutaten.ZutatenListeRoute
import java.time.LocalDate

/** Die Bildschirme mit ViewModel (Hilt); Navigationstests setzen [Bildschirme.platzhalter] ein. */
class Bildschirme(
    val heute: @Composable (NavHostController) -> Unit,
    val tagebuch: @Composable (LocalDate, NavHostController) -> Unit,
    val einstellungen: @Composable (NavHostController) -> Unit,
    val serverVerbinden: @Composable (NavHostController) -> Unit,
    val zutaten: @Composable (NavHostController) -> Unit,
    /** Zutateneditor mit der Zutat-ID. */
    val zutat: @Composable (String, NavHostController) -> Unit,
    val rezepte: @Composable (NavHostController) -> Unit,
    val rezeptDetail: @Composable (String, NavHostController) -> Unit,
    /** Editor; ID `null` = neues Rezept; dazu die Portionen, mit denen gekocht wurde. */
    val editor: @Composable (String?, Int?, NavHostController) -> Unit,
    /** Kochmodus: Rezept-ID, Portionen (`null` = die des Rezepts). */
    val kochen: @Composable (String, Int?, NavHostController) -> Unit,
    val woche: @Composable (NavHostController) -> Unit,
    /** Einkauf; Liste `null` = die aktuelle. */
    val einkauf: @Composable (String?, NavHostController) -> Unit,
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
            einstellungen = { nav ->
                EinstellungenRoute(
                    onServer = { nav.navigate(ServerVerbinden) }, onZutaten = { nav.navigate(Zutaten) }, onZurueck = { nav.popBackStack() },
                )
            },
            serverVerbinden = { nav -> VerbindenRoute(onZurueck = { nav.popBackStack() }) },
            zutaten = { nav -> ZutatenListeRoute(onZutat = { nav.navigate(Zutat(it)) }, onZurueck = { nav.popBackStack() }) },
            zutat = { id, nav -> ZutatEditorRoute(id, onFertig = { nav.popBackStack() }) },
            rezepte = { nav ->
                RezepteBereich(
                    onOeffnen = { nav.navigate(RezeptDetail(it)) },
                    onNeu = { nav.navigate(RezeptNeu) },
                    onKochen = { id, portionen -> nav.navigate(Kochen(id, portionen)) },
                    onEinkauf = { nav.zumEinkauf() },
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
                    onEinkauf = { nav.zumEinkauf() },
                )
            },
            editor = { id, gekochtMit, nav ->
                EditorRoute(
                    id,
                    onFertig = { gespeichert ->
                        // Neu: das Rezept ersetzt den Editor; Bearbeiten: zurück zum Detail, das sich selbst aktualisiert
                        if (id == null) nav.navigate(RezeptDetail(gespeichert)) { popUpTo<RezeptNeu> { inclusive = true } } else nav.popBackStack()
                    },
                    onZurueck = { nav.popBackStack() },
                    gekochtMit = gekochtMit,
                )
            },
            kochen = { id, portionen, nav ->
                KochenRoute(
                    id, portionen,
                    onRezeptAnpassen = { r, p -> nav.zuRezeptAnpassen(r, p) },
                    onSchliessen = { nav.popBackStack() },
                )
            },
            woche = { nav ->
                WocheRoute(
                    onKochen = { r, p -> nav.navigate(Kochen(r, p)) },
                    onRezept = { nav.navigate(RezeptDetail(it)) },
                )
            },
            einkauf = { listeId, nav ->
                EinkaufRoute(listeId, onListe = { nav.navigate(EinkaufListe(it)) }, onAktuelle = { nav.zumEinkauf() })
            },
        )
        val platzhalter = Bildschirme(
            heute = { Platzhalter("Heute") },
            tagebuch = { datum, _ -> Platzhalter("Tagebuch", "Tagebuch $datum") },
            einstellungen = { Platzhalter("Einstellungen") },
            serverVerbinden = { Platzhalter("Server verbinden") },
            zutaten = { Platzhalter("Zutatenkatalog") },
            zutat = { id, _ -> Platzhalter("Zutat", "Zutat $id") },
            rezepte = { Platzhalter("Rezepte", "Rezeptliste") },
            rezeptDetail = { id, _ -> Platzhalter("Rezept", "Rezept $id") },
            editor = { id, gekochtMit, _ ->
                Platzhalter("Editor", if (id == null) "Neues Rezept" else "Rezept $id bearbeiten" + gekochtMit?.let { " · gekocht mit $it" }.orEmpty())
            },
            kochen = { id, portionen, _ ->
                Platzhalter("Kochen", "Rezept $id" + portionen?.let { n -> " · $n ${if (n == 1) "Portion" else "Portionen"}" }.orEmpty())
            },
            woche = { Platzhalter("Woche", "Wochenplan") },
            einkauf = { listeId, _ -> Platzhalter("Einkauf", listeId?.let { "Liste $it" } ?: "Einkaufsliste") },
        )
    }
}

/** Schema der Deep Links (die v1 hatte keine). */
const val SCHEMA = "foody://"

/**
 * Alle Ziele der V3. Deep Links: `foody://rezept/{id}`, `foody://einkauf/{listeId}`,
 * `foody://kochen/{rezeptId}?portionen={n}`, `foody://tagebuch/{datum}` (ungültiges Datum → heute).
 */
@Composable
fun FoodyNavHost(nav: NavHostController, bildschirme: Bildschirme, modifier: Modifier = Modifier) {
    NavHost(nav, startDestination = Heute, modifier = modifier) {
        composable<Heute> { bildschirme.heute(nav) }
        composable<Rezepte> { bildschirme.rezepte(nav) }
        composable<Woche> { bildschirme.woche(nav) }
        composable<Einkauf> { bildschirme.einkauf(null, nav) }
        composable<Einstellungen> { bildschirme.einstellungen(nav) }
        composable<ServerVerbinden> { bildschirme.serverVerbinden(nav) }
        composable<Zutaten> { bildschirme.zutaten(nav) }
        composable<Zutat> { bildschirme.zutat(it.toRoute<Zutat>().id, nav) }
        composable<RezeptDetail>(deepLinks = listOf(navDeepLink<RezeptDetail>(basePath = "${SCHEMA}rezept"))) {
            bildschirme.rezeptDetail(it.toRoute<RezeptDetail>().id, nav)
        }
        composable<RezeptNeu> { bildschirme.editor(null, null, nav) }
        composable<RezeptBearbeiten> {
            val ziel = it.toRoute<RezeptBearbeiten>()
            bildschirme.editor(ziel.id, ziel.gekochtMit, nav)
        }
        composable<Kochen>(deepLinks = listOf(navDeepLink<Kochen>(basePath = "${SCHEMA}kochen"))) {
            val ziel = it.toRoute<Kochen>()
            bildschirme.kochen(ziel.rezeptId, ziel.portionen, nav)
        }
        composable<EinkaufListe>(deepLinks = listOf(navDeepLink<EinkaufListe>(basePath = "${SCHEMA}einkauf"))) {
            bildschirme.einkauf(it.toRoute<EinkaufListe>().listeId, nav)
        }
        composable<TagebuchTag>(deepLinks = listOf(navDeepLink<TagebuchTag>(basePath = "${SCHEMA}tagebuch"))) {
            bildschirme.tagebuch(tagebuchDatum(it.toRoute<TagebuchTag>().datum, LocalDate.now()), nav)
        }
    }
}

/**
 * Deep Link bei laufender App (`onNewIntent`): ins laufende Gerüst navigieren. [NavHostController.handleDeepLink] baut
 * bei `FLAG_ACTIVITY_NEW_TASK` (Tipp auf die Timer-Meldung) den Task neu auf und verwirft ungespeicherte Eingaben.
 * Derselbe Link zweimal legt das Ziel nicht doppelt an.
 */
fun NavHostController.deepLinkOeffnen(intent: Intent): Boolean {
    val uri = intent.data ?: return false
    if (!graph.hasDeepLink(uri)) return false
    navigate(uri, navOptions { launchSingleTop = true })
    return true
}

/** „Rezept anpassen“ nach dem Kochen: der Editor ersetzt den Kochmodus, „Zurück“ führt dorthin, woher man kam. */
fun NavHostController.zuRezeptAnpassen(rezeptId: String, portionen: Int) =
    navigate(RezeptBearbeiten(rezeptId, gekochtMit = portionen)) { popUpTo<Kochen> { inclusive = true } }

/**
 * Zur aktuellen Einkaufsliste: im Einkauf-Tab zurück an seinen Anfang (etwa von einer früheren Liste), sonst zum Tab
 * „Einkauf“, ohne einen gemerkten Stapel wiederherzustellen – der könnte noch eine frühere Liste zeigen. Heute bleibt
 * die Wurzel, „Zurück“ führt dorthin.
 */
fun NavHostController.zumEinkauf() {
    if (popBackStack<Einkauf>(inclusive = false)) return
    navigate(Einkauf) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

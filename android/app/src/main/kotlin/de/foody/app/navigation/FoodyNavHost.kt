package de.foody.app.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import de.foody.feature.heute.Schnellstart
import de.foody.feature.heute.tagebuch.TagebuchTagRoute
import de.foody.feature.kochen.KochenRoute
import de.foody.feature.rezepte.RezepteBereich
import de.foody.feature.rezepte.detail.RezeptDetailRoute
import de.foody.feature.rezepte.editor.EditorRoute
import de.foody.feature.woche.WocheRoute
import de.foody.feature.zutaten.ZutatEditorRoute
import de.foody.feature.zutaten.ZutatenListeRoute
import java.time.LocalDate
import java.util.UUID

/** Die Bildschirme mit ViewModel (Hilt); Navigationstests setzen eigene Platzhalter ein (`Platzhalter.kt` in den Tests). */
class Bildschirme(
    /** „Heute“; dazu die offene Anfrage eines Widgets (`null` = keine) und der Rückruf, wenn sie angenommen ist. */
    val heute: @Composable (NavHostController, Schnellstart?, () -> Unit) -> Unit,
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
            heute = { nav, schnellstart, onSchnellstartAngenommen ->
                HeuteRoute(
                    onKochen = { id, portionen -> nav.navigate(Kochen(id, portionen)) },
                    onRezept = { nav.navigate(RezeptDetail(it)) },
                    onEinkauf = { nav.navigate(EinkaufListe(it)) },
                    onAlleEintraege = { nav.navigate(TagebuchTag(LocalDate.now().toString())) },
                    onEinstellungen = { nav.navigate(Einstellungen) },
                    schnellstart = schnellstart,
                    onSchnellstartAngenommen = onSchnellstartAngenommen,
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
    }
}

/** Schema der Deep Links (die v1 hatte keine). */
const val SCHEMA = "foody://"

/** Tipp aufs Widget „Scannen“: „Heute“ mit dem Eintragen-Blatt und gleich dem Strichcode-Scan. */
const val SCAN_LINK = "${SCHEMA}scannen"

/** Tipp aufs Widget „KI-Foto“: „Heute“ mit dem Eintragen-Blatt und gleich der Kamera für die Fotoerkennung. */
const val FOTO_LINK = "${SCHEMA}foto"

/** Tipp aufs Widget „Schnell eintragen“ (oder dessen Knopf im Heute-Widget): „Heute“ mit dem Eintragen-Blatt. */
const val EINTRAGEN_LINK = "${SCHEMA}eintragen"

/**
 * Offene Widget-Anfrage im SavedStateHandle von „Heute“ ([Schnellstart.alsText]): übersteht Drehen und Prozessende,
 * bis „Heute“ sie annimmt.
 */
internal const val SCHNELLSTART = "schnellstart"

/** Der Link eines Widgets ([SCAN_LINK], [FOTO_LINK], [EINTRAGEN_LINK]) als Schnellstart; andere Links → `null`. */
fun Intent.schnellstart(): Schnellstart.Art? = when (data?.toString()) {
    SCAN_LINK -> Schnellstart.Art.SCANNEN
    FOTO_LINK -> Schnellstart.Art.FOTO
    EINTRAGEN_LINK -> Schnellstart.Art.EINTRAGEN
    else -> null
}

/**
 * Alle Ziele der V3. Deep Links: `foody://rezept/{id}`, `foody://einkauf/{listeId}`,
 * `foody://kochen/{rezeptId}?portionen={n}`, `foody://tagebuch/{datum}` (ungültiges Datum → heute), `foody://woche`; dazu
 * [SCAN_LINK] und [FOTO_LINK] von den Widgets, die [deepLinkOeffnen] selbst behandelt.
 */
@Composable
fun FoodyNavHost(nav: NavHostController, bildschirme: Bildschirme, modifier: Modifier = Modifier) {
    NavHost(nav, startDestination = Heute, modifier = modifier) {
        composable<Heute> { eintrag ->
            val anfrage by eintrag.savedStateHandle.getStateFlow<String?>(SCHNELLSTART, null).collectAsStateWithLifecycle()
            bildschirme.heute(nav, Schnellstart.ausText(anfrage)) { eintrag.savedStateHandle[SCHNELLSTART] = null }
        }
        composable<Rezepte> { bildschirme.rezepte(nav) }
        composable<Woche>(deepLinks = listOf(navDeepLink<Woche>(basePath = "${SCHEMA}woche"))) { bildschirme.woche(nav) }
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
 * Derselbe Link zweimal legt das Ziel nicht doppelt an. Die Widget-Links führen zu [schnellstartAnfragen].
 */
fun NavHostController.deepLinkOeffnen(intent: Intent): Boolean {
    intent.schnellstart()?.let {
        schnellstartAnfragen(it)
        return true
    }
    val uri = intent.data ?: return false
    if (!graph.hasDeepLink(uri)) return false
    navigate(uri, navOptions { launchSingleTop = true })
    return true
}

/**
 * Widget „Scannen“ oder „KI-Foto“: zurück zu „Heute“ wie ein Tipp auf den Tab und dort eine neue Anfrage hinterlegen
 * (je Tipp eine eigene ID, also auch beim zweiten Tipp ein neuer Scan bzw. ein neues Foto).
 */
fun NavHostController.schnellstartAnfragen(art: Schnellstart.Art) {
    popBackStack<Heute>(inclusive = false)
    getBackStackEntry<Heute>().savedStateHandle[SCHNELLSTART] = Schnellstart(art, UUID.randomUUID().toString()).alsText()
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

package de.foody.app.navigation

import android.content.Intent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navOptions
import androidx.navigation.toRoute
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.feature.einkauf.EinkaufRoute
import de.foody.feature.einstellungen.EinstellungenRoute
import de.foody.feature.einstellungen.VerbindenRoute
import de.foody.feature.heute.HeuteRoute
import de.foody.feature.heute.tagebuch.EintragenRoute
import de.foody.feature.heute.tagebuch.EintragenStart
import de.foody.feature.heute.tagebuch.TagebuchTagRoute
import de.foody.feature.kochen.KochenRoute
import de.foody.feature.rezepte.RezepteBereich
import de.foody.feature.rezepte.detail.RezeptDetailRoute
import de.foody.feature.rezepte.editor.EditorRoute
import de.foody.feature.rezepte.importieren.ImportBlatt
import de.foody.feature.woche.WocheRoute
import de.foody.feature.zutaten.ZutatEditorRoute
import de.foody.feature.zutaten.ZutatenListeRoute
import java.time.LocalDate

/** Die Bildschirme mit ViewModel (Hilt); Navigationstests setzen eigene Platzhalter ein (`Platzhalter.kt` in den Tests). */
class Bildschirme(
    val heute: @Composable (NavHostController) -> Unit,
    val tagebuch: @Composable (LocalDate, NavHostController) -> Unit,
    /** Eintragen mit seinem Ziel (Datum, Mahlzeit, Sofort-Start, Auftrag); `onFertig` schließt die Seite (einmal). */
    val eintragen: @Composable (Eintragen, onFertig: () -> Unit) -> Unit,
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
                    onEintragen = nav::eintragenOeffnen,
                )
            },
            tagebuch = { datum, nav ->
                // Blättern ersetzt den Tag, statt ihn auf den Stapel zu legen: „Zurück“ führt zu „Heute“
                TagebuchTagRoute(
                    datum,
                    onDatum = { neu -> nav.navigate(TagebuchTag(neu.toString())) { popUpTo<TagebuchTag> { inclusive = true } } },
                    onZurueck = { nav.popBackStack() },
                    onEintragen = nav::eintragenOeffnen,
                )
            },
            // Speichern und Zurück aus Erfassen führen dorthin, woher man kam
            eintragen = { ziel, onFertig -> EintragenRoute(ziel.alsStart(LocalDate.now()), ziel.auftrag, onFertig = onFertig) },
            einstellungen = { nav ->
                // Der Markdown-Import gehört zu „Rezepte“; die Einstellungen öffnen sein Blatt (Features kennen sich nicht)
                var importOffen by rememberSaveable { mutableStateOf(false) }
                EinstellungenRoute(
                    onServer = { nav.navigate(ServerVerbinden) }, onZutaten = { nav.navigate(Zutaten) }, onZurueck = { nav.popBackStack() },
                    onRezepteImport = { importOffen = true },
                )
                if (importOffen) ImportBlatt(onSchliessen = { importOffen = false })
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

/** Dauer, in der das Tagebuch aus dem Auge auftaucht (Grauen). */
private const val AUFTAUCHEN_MS = 380

/** Schema der Deep Links (die v1 hatte keine). */
const val SCHEMA = "foody://"

/** Tipp aufs Widget „Scannen“: Eintragen über „Heute“, gleich mit dem Strichcode-Scan ([WidgetStart.SCANNEN]). */
const val SCAN_LINK = "${SCHEMA}scannen"

/** Tipp aufs Widget „KI-Foto“: Eintragen über „Heute“, gleich mit der Kamera für die Fotoerkennung ([WidgetStart.FOTO]). */
const val FOTO_LINK = "${SCHEMA}foto"

/** Tipp aufs Widget „Schnell eintragen“ (oder dessen Knopf im Heute-Widget): Eintragen über „Heute“, mit Erfassen. */
const val EINTRAGEN_LINK = "${SCHEMA}eintragen"

/**
 * Alle Ziele der V3. Deep Links: `foody://rezept/{id}`, `foody://einkauf/{listeId}`,
 * `foody://kochen/{rezeptId}?portionen={n}`, `foody://tagebuch/{datum}` (ungültiges Datum → heute), `foody://woche`; dazu
 * die Links der Widgets ([WidgetStart]), die [widgetStartOeffnen] behandelt (bei laufender App über [deepLinkOeffnen]).
 *
 * Das Tagebuch taucht im Grauen (Immersiv) aus dem Dunkel des Auges auf, wenn es von „Heute“ kommt.
 * Eintragen mit Sofort-Start kommt ohne Übergang, damit „Heute“ nicht aufblitzt, bevor Kamera bzw. Scanner aufgehen;
 * geschlossen wird es wie jede Seite (Abbrechen wie Speichern).
 */
@Composable
fun FoodyNavHost(nav: NavHostController, bildschirme: Bildschirme, modifier: Modifier = Modifier) {
    // Grauen Immersiv: Nach dem Blick ins Auge (das Dunkel der Pupille auf „Heute“) taucht das Tagebuch daraus auf
    val ausDemAuge = FoodyTheme.stil.immersiv(FoodyStil.GRAUEN)
    NavHost(nav, startDestination = Heute, modifier = modifier) {
        composable<Heute>(exitTransition = { if (targetState.istSofortZiel()) ExitTransition.None else null }) {
            bildschirme.heute(nav)
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
        composable<TagebuchTag>(
            deepLinks = listOf(navDeepLink<TagebuchTag>(basePath = "${SCHEMA}tagebuch")),
            enterTransition = {
                if (ausDemAuge && initialState.destination.hasRoute<Heute>()) {
                    fadeIn(tween(AUFTAUCHEN_MS)) + scaleIn(tween(AUFTAUCHEN_MS), initialScale = 0.86f)
                } else {
                    null
                }
            },
        ) {
            bildschirme.tagebuch(tagebuchDatum(it.toRoute<TagebuchTag>().datum, LocalDate.now()), nav)
        }
        composable<Eintragen>(enterTransition = { if (targetState.istSofortZiel()) EnterTransition.None else null }) { eintrag ->
            // Nur solange diese Seite oben auf dem Stapel liegt (er ändert sich sofort): Ein zweites „fertig“ (etwa Speichern
            // und Zurück fast zugleich) räumt nicht auch noch „Heute“ ab. Nicht an RESUMED gebunden: Nach einem
            // abgebrochenen Sofort-Scan liegt die Scanner-Activity noch obenauf, in der Einblendung ist die Seite erst STARTED.
            bildschirme.eintragen(eintrag.toRoute<Eintragen>()) { if (nav.currentBackStackEntry?.id == eintrag.id) nav.popBackStack() }
        }
    }
}

/** Eintragen, das gleich mit Kamera oder Scanner beginnt ([Eintragen.sofort]). */
private fun NavBackStackEntry.istSofortZiel(): Boolean = destination.hasRoute<Eintragen>() && toRoute<Eintragen>().sofort != null

/**
 * Deep Link bei laufender App (`onNewIntent`): ins laufende Gerüst navigieren. [NavHostController.handleDeepLink] baut
 * bei `FLAG_ACTIVITY_NEW_TASK` (Tipp auf die Timer-Meldung) den Task neu auf und verwirft ungespeicherte Eingaben.
 * Derselbe Link zweimal legt das Ziel nicht doppelt an. Die Widget-Links führen zu [widgetStartOeffnen].
 */
fun NavHostController.deepLinkOeffnen(intent: Intent): Boolean {
    intent.widgetStart()?.let {
        widgetStartOeffnen(it)
        return true
    }
    val uri = intent.data ?: return false
    if (!graph.hasDeepLink(uri)) return false
    navigate(uri, navOptions { launchSingleTop = true })
    return true
}

/**
 * Eintragen aus Heute oder dem Tagebuch (Zeilen, Knöpfe, Sofort-Knöpfe der Szenen). Liegt Eintragen schon obenauf,
 * passiert nichts: Während der Überblendung ist die Seite darunter noch bedienbar, und ein zweiter schneller Tipp legte
 * sonst eine zweite Seite auf den Stapel (nach Speichern stünde man auf einer leeren statt auf Heute). Der Stapel ändert
 * sich beim Navigieren sofort, also greift die Prüfung schon beim zweiten Tipp. Widgets nehmen [widgetStartOeffnen]: Dort
 * ersetzt ein neuer Tipp die offene Seite.
 */
fun NavHostController.eintragenOeffnen(start: EintragenStart) {
    if (currentBackStackEntry?.destination?.hasRoute<Eintragen>() == true) return
    navigate(start.alsZiel())
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

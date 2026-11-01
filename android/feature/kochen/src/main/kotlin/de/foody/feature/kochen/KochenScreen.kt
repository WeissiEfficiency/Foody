package de.foody.feature.kochen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.FoodyKnopf
import de.foody.core.designsystem.komponenten.KnopfArt
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.domain.StepTimer
import de.foody.feature.kochen.timer.KochTimerDienst
import kotlinx.coroutines.launch

/** Was die Seiten auslösen können. */
internal class KochAktionen(
    val onAbhaken: (Int) -> Unit,
    val onAlleZuruecksetzen: () -> Unit,
    val onPortionen: (Boolean) -> Unit,
    val onTimerStarten: (StepTimer) -> Unit,
)

/**
 * Kochmodus: „Bereitlegen“, dann ein Schritt pro Seite (wischen oder Zurück/Weiter), unten laufende Timer. Das Display
 * bleibt an. Schließen und Zurück behalten die Sitzung (12 Stunden).
 */
@Composable
fun KochenRoute(
    rezeptId: String,
    portionen: Int?,
    onRezeptAnpassen: (String, Int) -> Unit,
    onSchliessen: () -> Unit,
    vm: KochenViewModel = hiltViewModel(),
) {
    LaunchedEffect(rezeptId, portionen) { vm.starten(rezeptId, portionen) }
    val zustand by vm.zustand.collectAsStateWithLifecycle()
    val timer by vm.timer.collectAsStateWithLifecycle()
    // Die Uhr tickt sekündlich: nur Timerleiste und Ablaufmeldung lesen sie, nicht der ganze Bildschirm
    val jetzt = vm.jetzt.collectAsStateWithLifecycle()
    val fertigOffen by vm.fertigOffen.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val snackbar = remember { SnackbarHostState() }
    val bereich = rememberCoroutineScope()
    // Die Meldung am Ende braucht eine Freigabe – gefragt wird beim ersten Timer; abgelehnt läuft er trotzdem
    val freigabe = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { erlaubt ->
        if (!erlaubt && vm.hinweisOhneBenachrichtigungEinmal()) {
            bereich.launch { snackbar.showSnackbar("Ohne Benachrichtigungen meldet sich der Timer nur bei offener App") }
        }
    }
    val aktionen = KochAktionen(
        onAbhaken = vm::abhaken,
        onAlleZuruecksetzen = vm::alleZuruecksetzen,
        onPortionen = vm::portionenSchritt,
        onTimerStarten = { t ->
            vm.timerStarten(t)
            KochTimerDienst.wennNoetigStarten(context, vm.timerSpeicher)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                freigabe.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )
    AblaufMelden(timer) { jetzt.value }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val breit = maxWidth >= FoodyBreiten.zweispaltig
        Row(Modifier.fillMaxSize()) {
            val z = zustand
            if (breit && z is KochZustand.Da) SeitenListe(z, z.seite, vm::seiteWaehlen)
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxSize()) {
                    when (z) {
                        KochZustand.Laden -> Unit
                        KochZustand.NichtGefunden -> Column(Modifier.weight(1f)) {
                            LeererZustandFlaeche(
                                Icons.AutoMirrored.Outlined.MenuBook, "Rezept nicht gefunden", "Es wurde vielleicht gelöscht.",
                                aktionText = "Zurück", onAktion = onSchliessen,
                            )
                        }
                        is KochZustand.Da -> KochInhalt(
                            z, aktionen, onSeite = vm::seiteWaehlen, onFertig = { bereich.launch { vm.fertig() } },
                            onSchliessen = onSchliessen, Modifier.weight(1f),
                            timerLeiste = { TimerLeiste(timer, rezeptId, { jetzt.value }, vm::timerEntfernen) },
                        )
                    }
                    if (z !is KochZustand.Da) TimerLeiste(timer, rezeptId, { jetzt.value }, vm::timerEntfernen)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    val da = zustand as? KochZustand.Da
    if (fertigOffen && da != null) {
        FertigBlatt(
            ohneFoto = da.bild == null,
            onFoto = vm::fotoUebernehmen,
            onAnpassen = { onRezeptAnpassen(rezeptId, da.portionen) },
            onSchliessen = { vm.schliessen(); onSchliessen() },
        )
    }
}

@Composable
private fun KochInhalt(
    z: KochZustand.Da,
    a: KochAktionen,
    onSeite: (Int) -> Unit,
    onFertig: () -> Unit,
    onSchliessen: () -> Unit,
    modifier: Modifier = Modifier,
    timerLeiste: @Composable () -> Unit = {},
) {
    val pager = rememberPagerState(initialPage = z.seite) { z.seitenzahl }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect(onSeite) }
    LaunchedEffect(z.seite) { if (pager.currentPage != z.seite) pager.animateScrollToPage(z.seite) }
    Column(modifier) {
        Kopf(z, pager.currentPage, onSchliessen)
        HorizontalPager(pager, Modifier.weight(1f)) { seite ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = FoodyAbstaende.s6, vertical = FoodyAbstaende.s4),
            ) {
                val schritt = z.schritt(seite)
                if (schritt == null) BereitlegenSeite(z, a) else SchrittSeite(schritt, z.abgehakt, a)
            }
        }
        // Laufende Timer über den Knöpfen
        timerLeiste()
        Knoepfe(z, pager, onSeite, onFertig)
    }
}

@Composable
private fun Kopf(z: KochZustand.Da, seite: Int, onSchliessen: () -> Unit) {
    Row(Modifier.padding(horizontal = FoodyAbstaende.s2, vertical = FoodyAbstaende.s1), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onSchliessen) { Icon(Icons.Outlined.Close, contentDescription = "Kochen beenden", tint = FoodyTheme.farben.text) }
        Text(z.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = FoodyTheme.farben.text)
    }
    LinearProgressIndicator(
        progress = { (seite + 1f) / z.seitenzahl.coerceAtLeast(1) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = FoodyAbstaende.s6),
        color = FoodyTheme.farben.primaer,
        trackColor = FoodyTheme.farben.balkenSpur,
        drawStopIndicator = {},
    )
    val nummer = seite - if (z.hatBereitlegen) 1 else 0
    Text(
        if (z.schritt(seite) == null) "Bereitlegen" else "Schritt ${nummer + 1} von ${z.schritte.size}",
        Modifier.padding(horizontal = FoodyAbstaende.s6, vertical = FoodyAbstaende.s2),
        style = MaterialTheme.typography.labelLarge, color = FoodyTheme.farben.primaer,
    )
}

@Composable
private fun Knoepfe(z: KochZustand.Da, pager: PagerState, onSeite: (Int) -> Unit, onFertig: () -> Unit) {
    val seite = pager.currentPage
    val letzte = seite >= z.seitenzahl - 1
    Row(Modifier.fillMaxWidth().padding(FoodyAbstaende.s4), horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
        FoodyKnopf("Zurück", { onSeite(seite - 1) }, Modifier.weight(1f), art = KnopfArt.UMRISS, aktiv = seite > 0)
        FoodyKnopf(if (letzte) "Fertig" else "Weiter", { if (letzte) onFertig() else onSeite(seite + 1) }, Modifier.weight(2f))
    }
}

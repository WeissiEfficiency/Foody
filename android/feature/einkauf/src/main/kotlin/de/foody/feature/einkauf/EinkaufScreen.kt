package de.foody.feature.einkauf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.LeererZustandZeile
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.feature.einkauf.stil.EinkaufSzene
import de.foody.feature.einkauf.stil.animeHinzufuegen
import de.foody.feature.einkauf.stil.animeKopf
import de.foody.feature.einkauf.stil.animePosten
import de.foody.feature.einkauf.stil.bayrischKlappkopf
import de.foody.feature.einkauf.stil.bayrischKopf
import de.foody.feature.einkauf.stil.bayrischPosten
import de.foody.feature.einkauf.stil.edoKlappkopf
import de.foody.feature.einkauf.stil.edoKopf
import de.foody.feature.einkauf.stil.edoPosten
import de.foody.feature.einkauf.stil.grauenKlappkopf
import de.foody.feature.einkauf.stil.grauenKopf
import de.foody.feature.einkauf.stil.grauenPosten
import de.foody.feature.einkauf.stil.haendlerKopf
import de.foody.feature.einkauf.stil.haendlerPosten
import de.foody.feature.einkauf.stil.kreuzerKlappkopf
import de.foody.feature.einkauf.stil.kreuzerKopf
import de.foody.feature.einkauf.stil.kreuzerPosten
import de.foody.feature.einkauf.stil.sageKlappkopf

/** Platz unter der Liste für die schwebende Navigationsleiste. */
private val LISTE_UNTEN = 104.dp
private val KACHEL_MIN = 104.dp

/**
 * Der Tab „Einkauf“ (und `EinkaufListe(id)`): Kopf, Hinweis „Plan geändert“, Eingabe, offene Kacheln je Abteilung,
 * „Zuletzt verwendet“ und der Katalog; ab 840 dp der Katalog rechts daneben. [listeId] `null` = aktuelle Liste. Die
 * Düstere Sage zeigt in Immersiv Kopf und Posten als „Der Händler“ (`stil/EinkaufSage.kt`), die Stile R–V ihre eigene
 * Szene (`stil/Einkauf*.kt`).
 */
@Composable
fun EinkaufRoute(listeId: String?, onListe: (String) -> Unit, onAktuelle: () -> Unit, vm: EinkaufViewModel = hiltViewModel()) {
    LaunchedEffect(listeId) { vm.anzeigen(listeId) }
    val z by vm.zustand.collectAsStateWithLifecycle()
    var aufgeklappt by rememberSaveable { mutableStateOf(listOf(ZULETZT)) }
    var loeschenFrage by rememberSaveable { mutableStateOf(false) }
    // Anime: die gewählte Pille unter „Hinzufügen“ (`null` = Zuletzt bzw. die erste Abteilung)
    var animeAbteilung by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val umschalten = { name: String -> aufgeklappt = if (name in aufgeklappt) aufgeklappt - name else aufgeklappt + name }
    val alle = z.ansicht?.alle.orEmpty()
    // Stile Q–V: Neue Liste, Abgleich und Frühere als Seite mit Reitern wie in den Entwürfen; Milchglas behält die Blätter
    val blatt by vm.blatt.collectAsStateWithLifecycle()
    val seitenBlatt = blatt.takeIf { FoodyTheme.stil.stil != FoodyStil.MILCHGLAS && it in LISTEN_REITER }

    // Immersiv: Kopf und Posten als Szene des Stils (Sage „Der Händler“, R–V), sonst die normale Ansicht. Der Knopf
    // „Listen“ der Szenen ist der einzige Weg zu den Listen (erster Reiter „Neue Liste“); nur ohne Szene das Menü ⋮.
    val szene = EinkaufSzene.aktuell()
    val onListen: () -> Unit = { vm.blattOeffnen(Blatt.Vorschau) }
    // Zuletzt und Katalog: Klappköpfe in Schrift und Farben der Szene; Anime wählt die Abteilung über Pillen
    val kopf = when (szene) {
        FoodyStil.SAGE -> sageKlappkopf
        FoodyStil.EDO -> edoKlappkopf
        FoodyStil.BAYRISCH -> bayrischKlappkopf
        FoodyStil.GRAUEN -> grauenKlappkopf
        FoodyStil.KREUZER -> kreuzerKlappkopf
        else -> StandardKlappkopf
    }
    val oben: LazyGridScope.() -> Unit = {
        when (szene) {
            FoodyStil.SAGE -> haendlerKopf(z.ansicht, onListen)
            FoodyStil.ANIME -> animeKopf(z.ansicht, onListen)
            FoodyStil.EDO -> edoKopf(z.ansicht, onListen)
            FoodyStil.BAYRISCH -> bayrischKopf(z.ansicht, onListen)
            FoodyStil.GRAUEN -> grauenKopf(z.ansicht, onListen)
            FoodyStil.KREUZER -> kreuzerKopf(z.ansicht, onListen)
            else -> ganz("kopf") { EinkaufKopf(z.ansicht) { EinkaufMenue(z.ansicht, vm::blattOeffnen) { loeschenFrage = true } } }
        }
        if (z.abgleich.isNotEmpty()) ganz("hinweis") { AbgleichHinweis { vm.blattOeffnen(Blatt.Abgleich) } }
        ganz("eingabe") { EingabeFeld(z.suche, vm::suche, vm::eingeben) }
    }
    val liste: LazyGridScope.() -> Unit = {
        if (z.suche.isNotBlank()) {
            suchKacheln(z.suche, alle, onTreffer = { n, a -> vm.antippen(n, a, null); vm.suche("") }, onNeu = vm::eingeben)
        } else {
            if (z.ansicht?.offen.isNullOrEmpty()) {
                ganz("leer") {
                    LeererZustandZeile("Noch nichts auf der Liste", "Neue Liste aus dem Plan", { vm.blattOeffnen(Blatt.Vorschau) })
                }
            }
            val umschalten: (String, Boolean) -> Unit = { id, an -> vm.abhaken(id, an) }
            val details: (String) -> Unit = { vm.blattOeffnen(Blatt.Details(it)) }
            when (szene) {
                FoodyStil.SAGE -> haendlerPosten(z.ansicht, umschalten, details)
                FoodyStil.ANIME -> animePosten(z.ansicht, umschalten, details)
                FoodyStil.EDO -> edoPosten(z.ansicht, umschalten, details)
                FoodyStil.BAYRISCH -> bayrischPosten(z.ansicht, umschalten, details)
                FoodyStil.GRAUEN -> grauenPosten(z.ansicht, umschalten, details)
                FoodyStil.KREUZER -> kreuzerPosten(z.ansicht, umschalten, details)
                else -> offeneKacheln(z.ansicht?.offen.orEmpty(), onGekauft = { vm.abhaken(it, true) }, onDetails = details)
            }
            // Anime zeigt „Zuletzt“ als erste Pille unter „Hinzufügen“
            if (szene != FoodyStil.ANIME) {
                zuletztKacheln(z.zuletzt, alle, ZULETZT in aufgeklappt, { umschalten(ZULETZT) }, { vm.zuletztAntippen(it) }, kopf)
            }
        }
    }
    val katalog: LazyGridScope.() -> Unit = {
        if (szene == FoodyStil.ANIME) {
            animeHinzufuegen(z.zuletzt, alle, animeAbteilung, { animeAbteilung = it }, { vm.zuletztAntippen(it) }) { n, a -> vm.antippen(n, a, null) }
        } else {
            katalogKacheln(alle, aufgeklappt.toSet(), umschalten, onTipp = { n, a -> vm.antippen(n, a, null) }, kopf = kopf)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        val breit = maxWidth >= FoodyBreiten.zweispaltig
        val raster = Modifier.widthIn(max = FoodyBreiten.zweiBereiche).fillMaxSize()
        when {
            seitenBlatt != null -> ListenSeite(
                seitenBlatt, z.ansicht?.von != null, vm::blattOeffnen, vm::blattSchliessen,
                onLoeschen = if (z.ansicht != null) ({ loeschenFrage = true }) else null,
            ) {
                EinkaufBlaetter(vm, z, snackbar, onListe)
            }
            z.listeFehlt -> LeererZustandFlaeche(
                Icons.Outlined.RemoveShoppingCart, "Diese Liste gibt es nicht mehr", "Sie wurde gelöscht.",
                aktionText = "Zur aktuellen Liste", onAktion = onAktuelle, modifier = Modifier.padding(FoodyAbstaende.s4),
            )
            breit -> Column(raster) {
                Raster(Modifier.fillMaxWidth(), unten = 0.dp) { oben() }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s4)) {
                    Raster(Modifier.weight(1f)) { liste() }
                    Raster(Modifier.weight(1f)) { katalog() }
                }
            }
            else -> Raster(raster) { oben(); liste(); if (z.suche.isBlank()) katalog() }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = ueberLeiste(LISTE_UNTEN)))
    }
    if (seitenBlatt == null) EinkaufBlaetter(vm, z, snackbar, onListe)
    // Aus dem Menü ⋮ (Milchglas) oder von der Listen-Seite (Q–V); die Seite schließt nach dem Löschen
    z.ansicht?.takeIf { loeschenFrage }?.let { a ->
        LoeschenFrage(a.name, onLoeschen = { loeschenFrage = false; vm.listeLoeschen(); vm.blattSchliessen() }, onAbbrechen = { loeschenFrage = false })
    }
}

@Composable
private fun Raster(modifier: Modifier, unten: androidx.compose.ui.unit.Dp = ueberLeiste(LISTE_UNTEN), inhalt: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(KACHEL_MIN),
        modifier = modifier,
        contentPadding = PaddingValues(start = FoodyAbstaende.s4, end = FoodyAbstaende.s4, top = FoodyAbstaende.s2, bottom = unten),
        horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2),
        verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2),
        content = inhalt,
    )
}

private fun LazyGridScope.ganz(key: String, inhalt: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { inhalt() }

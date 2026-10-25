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
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.LeererZustandZeile

/** Inhalt höchstens so breit; ab [ZWEI_BEREICHE_AB] Liste links, Katalog rechts. */
private val INHALT_MAX = 1100.dp
private val ZWEI_BEREICHE_AB = 840.dp

/** Platz unter der Liste für die schwebende Navigationsleiste. */
private val LISTE_UNTEN = 104.dp
private val KACHEL_MIN = 104.dp

/**
 * Der Tab „Einkauf“ (und `EinkaufListe(id)`): Kopf, Hinweis „Plan geändert“, Eingabe, offene Kacheln je Abteilung,
 * „Zuletzt verwendet“ und der Katalog; ab 840 dp der Katalog rechts daneben. [listeId] `null` = aktuelle Liste.
 */
@Composable
fun EinkaufRoute(listeId: String?, onListe: (String) -> Unit, onAktuelle: () -> Unit, vm: EinkaufViewModel = hiltViewModel()) {
    LaunchedEffect(listeId) { vm.anzeigen(listeId) }
    val z by vm.zustand.collectAsStateWithLifecycle()
    var aufgeklappt by rememberSaveable { mutableStateOf(listOf(ZULETZT)) }
    var loeschenFrage by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val umschalten = { name: String -> aufgeklappt = if (name in aufgeklappt) aufgeklappt - name else aufgeklappt + name }
    val alle = z.ansicht?.alle.orEmpty()

    val oben: LazyGridScope.() -> Unit = {
        ganz("kopf") { EinkaufKopf(z.ansicht) { EinkaufMenue(z.ansicht, vm::blattOeffnen) { loeschenFrage = true } } }
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
            offeneKacheln(z.ansicht?.offen.orEmpty(), onGekauft = { vm.abhaken(it, true) }, onDetails = { vm.blattOeffnen(Blatt.Details(it)) })
            zuletztKacheln(z.zuletzt, alle, ZULETZT in aufgeklappt, { umschalten(ZULETZT) }, { vm.zuletztAntippen(it) })
        }
    }
    val katalog: LazyGridScope.() -> Unit = {
        katalogKacheln(alle, aufgeklappt.toSet(), umschalten) { n, a -> vm.antippen(n, a, null) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        val breit = maxWidth >= ZWEI_BEREICHE_AB
        val raster = Modifier.widthIn(max = INHALT_MAX).fillMaxSize()
        when {
            z.listeFehlt -> LeererZustandFlaeche(
                Icons.Outlined.RemoveShoppingCart, "Diese Liste gibt es nicht mehr", "Sie wurde gelöscht.",
                aktionText = "Zur aktuellen Liste", onAktion = onAktuelle, modifier = Modifier.padding(16.dp),
            )
            breit -> Column(raster) {
                Raster(Modifier.fillMaxWidth(), unten = 0.dp) { oben() }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Raster(Modifier.weight(1f)) { liste() }
                    Raster(Modifier.weight(1f)) { katalog() }
                }
            }
            else -> Raster(raster) { oben(); liste(); if (z.suche.isBlank()) katalog() }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LISTE_UNTEN))
    }
    EinkaufBlaetter(vm, z, snackbar, onListe)
    z.ansicht?.takeIf { loeschenFrage }?.let { a ->
        LoeschenFrage(a.name, onLoeschen = { loeschenFrage = false; vm.listeLoeschen() }, onAbbrechen = { loeschenFrage = false })
    }
}

@Composable
private fun Raster(modifier: Modifier, unten: androidx.compose.ui.unit.Dp = LISTE_UNTEN, inhalt: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(KACHEL_MIN),
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = unten),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = inhalt,
    )
}

private fun LazyGridScope.ganz(key: String, inhalt: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { inhalt() }

package de.foody.katalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.FoodyHintergrund
import de.foody.core.designsystem.komponenten.Abschnittskopf
import de.foody.core.designsystem.komponenten.FoodyChip
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.katalog.seiten.KatalogSeitenInhalt

/**
 * Katalog aller Komponenten. Handy: Seiten als Chip-Reihe oben; Tablet: Seitenliste links, Inhalt rechts. Die
 * Umschalter wirken auf den ganzen Inhalt (Darstellung, Schrift 200 %, Glas ohne Unschärfe).
 */
@Composable
fun KatalogApp(start: KatalogSeite = KatalogSeite.FARBEN_SCHRIFT, einstellung: KatalogEinstellung = KatalogEinstellung()) {
    var seite by remember(start) { mutableStateOf(start) }
    var e by remember(einstellung) { mutableStateOf(einstellung) }
    val dunkel = when (e.darstellung) {
        Darstellung.SYSTEM -> isSystemInDarkTheme()
        Darstellung.HELL -> false
        Darstellung.DUNKEL -> true
    }
    FoodyTheme(dunkel = dunkel, unschaerfe = !e.ohneUnschaerfe) {
        val dichte = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides if (e.schrift200) Density(dichte.density, fontScale = 2f) else dichte,
        ) {
            FoodyHintergrund {
                BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    val inhalt: @Composable (Modifier) -> Unit = { m ->
                        Column(
                            m.testTag(INHALT_TAG)
                                .semantics { darstellung = if (dunkel) "dunkel" else "hell" }
                                .verticalScroll(rememberScrollState())
                                .padding(FoodyAbstaende.s4),
                            verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s4),
                        ) {
                            Umschalter(e) { e = it }
                            Abschnittskopf(seite.titel)
                            KatalogSeitenInhalt(seite)
                        }
                    }
                    if (maxWidth >= FoodyBreiten.zweispaltig) {
                        Row(Modifier.fillMaxSize()) {
                            Column(
                                Modifier.width(260.dp).testTag(SEITENLISTE_TAG).verticalScroll(rememberScrollState()).padding(FoodyAbstaende.s4),
                                verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2),
                            ) {
                                KatalogSeite.entries.forEach { s ->
                                    FoodyChip(s.titel, ausgewaehlt = s == seite, onKlick = { seite = s }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                            inhalt(Modifier.weight(1f))
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Row(
                                Modifier.testTag(SEITENREIHE_TAG).horizontalScroll(rememberScrollState())
                                    .padding(horizontal = FoodyAbstaende.s4, vertical = FoodyAbstaende.s2),
                                horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2),
                            ) {
                                KatalogSeite.entries.forEach { s ->
                                    FoodyChip(s.titel, ausgewaehlt = s == seite, onKlick = { seite = s })
                                }
                            }
                            inhalt(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Umschalter(e: KatalogEinstellung, aendern: (KatalogEinstellung) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2)) {
        FoodyChip("System", e.darstellung == Darstellung.SYSTEM, { aendern(e.copy(darstellung = Darstellung.SYSTEM)) })
        FoodyChip("Hell", e.darstellung == Darstellung.HELL, { aendern(e.copy(darstellung = Darstellung.HELL)) })
        FoodyChip("Dunkel", e.darstellung == Darstellung.DUNKEL, { aendern(e.copy(darstellung = Darstellung.DUNKEL)) })
        FoodyChip("Schrift 200 %", e.schrift200, { aendern(e.copy(schrift200 = !e.schrift200)) })
        FoodyChip("Ohne Unschärfe", e.ohneUnschaerfe, { aendern(e.copy(ohneUnschaerfe = !e.ohneUnschaerfe)) })
    }
}

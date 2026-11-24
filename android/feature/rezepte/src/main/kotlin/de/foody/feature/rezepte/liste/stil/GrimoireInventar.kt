package de.foody.feature.rezepte.liste.stil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.platzFuerLeiste
import de.foody.core.designsystem.komponenten.rezeptBild
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.core.designsystem.stil.sage.SageHauptknopf
import de.foody.core.designsystem.stil.sage.SageKopf
import de.foody.core.designsystem.stil.sage.sageHell
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.feature.rezepte.liste.FilterChips
import de.foody.feature.rezepte.liste.LEER_TEXT
import de.foody.feature.rezepte.liste.ListeFilter
import de.foody.feature.rezepte.liste.ListeZustand
import de.foody.feature.rezepte.liste.Suche

/*
 * Das Grimoire der Düsteren Sage in Immersiv als Inventar eines Spiels (Wunsch 2026-11-24): Kopf, Suche und Filter
 * stehen oben fest, darunter die Rezepte als Inventarfelder (auf dem Handy vier je Reihe), die allein scrollen; unten
 * steht fest die Beschreibung des gewählten Gegenstands mit Werten und „Aufschlagen“. Ein Tipp wählt ein Feld, ein
 * zweiter Tipp auf das gewählte schlägt es auf. Ruhig behält die Seite zum Blättern ([RezepteSage]).
 */

private val FACH_DUNKEL = Color(0xFF0B0A08)
private val FACH_HELL = Color(0xFF1A1712)
private val TAFEL = Color(0xF0100E0B)

@Composable
internal fun GrimoireInventar(
    z: ListeZustand,
    aendern: ((ListeFilter) -> ListeFilter) -> Unit,
    zuruecksetzen: () -> Unit,
    onOeffnen: (String) -> Unit,
    onNeu: () -> Unit,
) {
    // Die Wahl beginnt bei jeder neuen Suche oder jedem Filter wieder beim ersten Rezept
    var gewaehlt by rememberSaveable(z.filter) { mutableStateOf<String?>(null) }
    val raster = rememberSaveable(z.filter, saver = LazyGridState.Saver) { LazyGridState() }
    val r = z.treffer.firstOrNull { it.id == gewaehlt } ?: z.treffer.firstOrNull()
    val n = z.treffer.size
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().padding(top = 22.dp)) {
            val rand = Modifier.padding(horizontal = 20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SageKopf("GESAMMELTE REZEPTE", "GRIMOIRE", "Rezepte", rand) {
                    if (n > 0) {
                        Text(
                            if (n == 1) "1 Rezept" else "$n Rezepte", Modifier.padding(bottom = 12.dp, end = 10.dp),
                            style = Sage.text(15f), color = FoodyTheme.farben.nebentext,
                        )
                    }
                    InventarNeu(onNeu)
                }
                if (z.geladen && z.keineRezepte) {
                    LeererZustandFlaeche(
                        Icons.AutoMirrored.Outlined.MenuBook, "Noch keine Rezepte", LEER_TEXT, aktionText = "Anlegen", onAktion = onNeu, modifier = rand,
                    )
                    return@Column
                }
                Suche(z.filter, aendern, "Im Grimoire suchen", rand)
                FilterChips(z.filter, aendern)
                if (z.geladen && n == 0) {
                    LeererZustandFlaeche(
                        Icons.AutoMirrored.Outlined.MenuBook, "Nichts gefunden", "Kein Rezept passt zu Suche und Filtern.",
                        aktionText = "Filter zurücksetzen", onAktion = zuruecksetzen, modifier = rand,
                    )
                }
            }
            if (z.keineRezepte || n == 0) return@Column
            val kante = FoodyTheme.farben.lichtkante
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                state = raster,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp).drawBehind {
                    drawLine(kante, Offset(20.dp.toPx(), 0f), Offset(size.width - 20.dp.toPx(), 0f), 1.dp.toPx())
                },
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(z.treffer, key = { it.id }) { k ->
                    val an = k.id == r?.id
                    Fach(k, an) { if (an) onOeffnen(k.id) else gewaehlt = k.id }
                }
            }
            r?.let { Beschreibung(it, { onOeffnen(it.id) }, rand) }
            Spacer(Modifier.height(platzFuerLeiste() + 12.dp))
        }
    }
}

/**
 * Ein Inventarfeld: dunkles, quadratisches Fach mit Kante; darin das Bild des Rezepts oder sein Anfangsbuchstabe als
 * Rune, oben links ♦ für Favoriten, unten rechts die kcal wie eine Stückzahl. Das gewählte glüht. TalkBack: Tab,
 * gewählt, „Reispfanne, 30 Min., 717 kcal, Favorit“; der zweite Tipp schlägt auf.
 */
@Composable
private fun Fach(r: RezeptKarte, an: Boolean, onKlick: () -> Unit) {
    val glut = FoodyTheme.stil.zier
    val f = FoodyTheme.farben
    val bild = rezeptBild(r.bild)
    Box(
        Modifier.aspectRatio(1f)
            .then(if (an) Modifier.shadow(12.dp, RectangleShape, ambientColor = glut, spotColor = glut) else Modifier)
            .background(Brush.radialGradient(listOf(if (an) glut.copy(alpha = 0.22f) else FACH_HELL, FACH_DUNKEL)))
            .border(if (an) 1.5.dp else 1.dp, if (an) glut else f.lichtkante)
            .rezeptWahl(r, an, onKlick)
            .padding(3.dp)
            // Innere Kante: das Fach wirkt eingelassen
            .drawWithContent {
                drawContent()
                drawRect(Color.Black.copy(alpha = 0.5f), style = Stroke(1.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bild != null) {
            Box(Modifier.fillMaxSize().clip(RectangleShape)) { bild() }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))))
        } else {
            Text(anfang(r), style = Sage.titel(26f, 0f), color = if (an) glut else sageHell().copy(alpha = 0.75f))
        }
        if (r.favorit) Text("♦", Modifier.align(Alignment.TopStart).padding(start = 3.dp), style = Sage.text(11f), color = glut)
        r.kcalJePortion?.let {
            Text("$it", Modifier.align(Alignment.BottomEnd).padding(end = 3.dp), style = Sage.text(11f), color = f.text.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}

/**
 * Beschreibung des gewählten Gegenstands wie im Inventar eines Spiels: Rune bzw. Bild, Art, Name, Werte (Zeit, kcal,
 * gekocht), der Satz aus den Zutaten und „Aufschlagen“.
 */
@Composable
private fun Beschreibung(r: RezeptKarte, onOeffnen: () -> Unit, modifier: Modifier) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    Column(
        modifier.fillMaxWidth()
            .drawBehind {
                val a = 3.dp.toPx()
                drawRect(f.balkenSpur, Offset(-a, -a), Size(size.width + 2 * a, size.height + 2 * a), style = Stroke(1.dp.toPx()))
            }
            .background(TAFEL).border(1.dp, f.lichtkante)
            .background(Brush.verticalGradient(listOf(glut.copy(alpha = 0.08f), Color.Transparent)))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val bild = rezeptBild(r.bild)
            Box(
                Modifier.size(56.dp).background(Brush.radialGradient(listOf(glut.copy(alpha = 0.25f), FACH_DUNKEL))).border(1.dp, glut.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                if (bild != null) {
                    Box(Modifier.fillMaxSize().padding(2.dp).clip(RectangleShape)) { bild() }
                } else {
                    Text(anfang(r), style = Sage.titel(24f, 0f), color = glut)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                rezeptArt(r).takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = Sage.titel(10f, 0.2f, FontWeight.Medium), color = f.nebentext, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(r.name, style = Sage.titel(17f, 0.03f), color = sageHell(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(inventarWerte(r), style = Sage.text(14f), color = f.text)
            }
        }
        zutatenSatz(r)?.let {
            Text(it, style = Sage.text(14f, kursiv = true), color = f.nebentext, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start)
        }
        SageHauptknopf("AUFSCHLAGEN", "Öffnen: ${r.name}", onOeffnen, Modifier.fillMaxWidth())
    }
}

/** „30 Min. · 717 kcal · 2× gekocht“ – nur, was bekannt ist; gekocht steht immer da. */
internal fun inventarWerte(r: RezeptKarte): String =
    listOfNotNull(r.zeitMinuten?.let { "$it Min." }, r.kcalJePortion?.let { "$it kcal" }, "${r.gekocht}× gekocht").joinToString(" · ")

/** „+“ als eckiger Knopf im Akzent; öffnet ein neues Rezept. */
@Composable
private fun InventarNeu(onNeu: () -> Unit) {
    Box(
        Modifier.size(48.dp).border(1.dp, FoodyTheme.stil.zier)
            .clickable(role = Role.Button, onClick = onNeu)
            .semantics { contentDescription = "Neues Rezept" },
        contentAlignment = Alignment.Center,
    ) { Text("+", style = Sage.titel(22f, 0f), color = FoodyTheme.farben.primaer) }
}

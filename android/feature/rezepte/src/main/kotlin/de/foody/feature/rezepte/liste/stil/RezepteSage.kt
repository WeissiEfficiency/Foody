package de.foody.feature.rezepte.liste.stil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.Suchfeld
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.core.designsystem.stil.sage.SageHauptknopf
import de.foody.core.designsystem.stil.sage.SageKopf
import de.foody.core.designsystem.stil.sage.SageRaute
import de.foody.core.designsystem.stil.sage.sageHell
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.feature.rezepte.liste.FilterChips
import de.foody.feature.rezepte.liste.ListeFilter
import de.foody.feature.rezepte.liste.ListeZustand
import java.util.Locale

/**
 * Rezeptliste der Düsteren Sage in Immersiv (Entwurf `Q-Rezepte`): „Grimoire“ mit Suche und denselben Filtern wie die
 * normale Liste; die Treffer werden einzeln aufgeschlagen, mit Emblem, Art, Zeit, kcal und „gekocht“, darunter
 * „‹ Aufschlagen ›“. Dieselbe Logik und dieselben Ziele wie die normale Liste (öffnen, neu, importieren).
 */
@Composable
internal fun RezepteSage(
    z: ListeZustand,
    aendern: ((ListeFilter) -> ListeFilter) -> Unit,
    zuruecksetzen: () -> Unit,
    onOeffnen: (String) -> Unit,
    onNeu: () -> Unit,
    onImport: () -> Unit,
) {
    // Blättern beginnt bei jeder neuen Suche oder jedem Filter wieder vorn
    var i by rememberSaveable(z.filter) { mutableIntStateOf(0) }
    val n = z.treffer.size
    val index = if (n == 0) 0 else i.coerceIn(0, n - 1)
    Box(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().padding(top = 22.dp, bottom = ueberLeiste(104.dp)),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val rand = Modifier.padding(horizontal = 20.dp)
            SageKopf("GESAMMELTE REZEPTE", "GRIMOIRE", "Rezepte", rand) {
                if (n > 0) Text("${index + 1} / $n", Modifier.padding(bottom = 12.dp), style = Sage.titel(15f, 0.04f), color = FoodyTheme.farben.text)
                NeuKnopf(onNeu, onImport)
            }
            if (z.geladen && z.keineRezepte) {
                LeererZustandFlaeche(
                    Icons.AutoMirrored.Outlined.MenuBook, "Noch keine Rezepte", "Lege ein Rezept an oder importiere Markdown-Dateien.",
                    aktionText = "Anlegen", onAktion = onNeu, modifier = rand,
                )
                return@Column
            }
            Suchfeld(z.filter.suche, { s -> aendern { it.copy(suche = s) } }, "Im Grimoire suchen", rand)
            FilterChips(z.filter, z.tags, aendern)
            val r = z.treffer.getOrNull(index)
            if (r == null) {
                if (z.geladen) {
                    LeererZustandFlaeche(
                        Icons.AutoMirrored.Outlined.MenuBook, "Nichts gefunden", "Kein Rezept passt zu Suche und Filtern.",
                        aktionText = "Filter zurücksetzen", onAktion = zuruecksetzen, modifier = rand,
                    )
                }
                return@Column
            }
            Seite(r, { onOeffnen(r.id) }, rand)
            Row(rand, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Blaettern("‹", "Vorheriges Rezept", n > 1) { i = (index + n - 1) % n }
                SageHauptknopf("AUFSCHLAGEN", "Öffnen: ${r.name}", { onOeffnen(r.id) }, Modifier.weight(1f))
                Blaettern("›", "Nächstes Rezept", n > 1) { i = (index + 1) % n }
            }
            if (n in 2..MAX_RAUTEN) {
                Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                    repeat(n) { k -> SageRaute(8.dp, gefuellt = k == index) }
                }
            }
        }
    }
}

/** Die „Art“ über dem Namen: erster Tag und Favorit, wie „OFENGERICHT · FAVORIT“. */
internal fun rezeptArt(r: RezeptKarte): String =
    listOfNotNull(r.tags.firstOrNull(), if (r.favorit) "Favorit" else null).joinToString(" · ").uppercase(Locale.GERMAN)

/** Kurzer Satz aus den Zutaten statt eines erfundenen Textes: „Mit Paprika, Zucchini und Feta.“ */
internal fun zutatenSatz(r: RezeptKarte, hoechstens: Int = 4): String? {
    val z = r.zutatNamen.distinct().take(hoechstens)
    if (z.isEmpty()) return null
    val mehr = r.zutatNamen.distinct().size > hoechstens
    return "Mit " + when {
        z.size == 1 -> z.first()
        mehr -> z.joinToString(", ") + " und mehr"
        else -> z.dropLast(1).joinToString(", ") + " und " + z.last()
    } + "."
}

@Composable
private fun Seite(r: RezeptKarte, onOeffnen: () -> Unit, modifier: Modifier) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    val immersiv = FoodyTheme.stil.variante == StilVariante.IMMERSIV
    Column(
        modifier.fillMaxWidth()
            // Doppelter Rahmen wie im Entwurf: innen Kante, 4 dp außen eine dunklere Linie
            .drawBehind {
                val a = 4.dp.toPx()
                drawRect(f.balkenSpur, Offset(-a, -a), Size(size.width + 2 * a, size.height + 2 * a), style = Stroke(1.dp.toPx()))
            }
            .padding(4.dp)
            .border(1.dp, f.lichtkante)
            .background(Brush.verticalGradient(listOf(glut.copy(alpha = 0.07f), Color.Transparent)))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val zeichen = r.name.trim().firstOrNull()?.uppercase() ?: "?"
        if (immersiv) {
            Emblem(zeichen)
        } else {
            Box(Modifier.size(72.dp).border(1.5.dp, glut, CircleShape), contentAlignment = Alignment.Center) {
                Text(zeichen, style = Sage.titel(30f, 0f), color = f.primaer)
            }
        }
        rezeptArt(r).takeIf { it.isNotEmpty() }?.let {
            Text(it, style = Sage.titel(11f, 0.22f, FontWeight.Medium), color = f.nebentext, textAlign = TextAlign.Center)
        }
        Text(
            "${r.name} ›",
            Modifier.clickable(role = Role.Button, onClick = onOeffnen).semantics { contentDescription = "Öffnen: ${r.name}" }.padding(vertical = 4.dp),
            style = Sage.titel(22f, 0.03f), color = sageHell(), textAlign = TextAlign.Center,
        )
        Row(
            Modifier.fillMaxWidth().drawBehind {
                drawLine(f.balkenSpur, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
                drawLine(f.balkenSpur, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                drawLine(f.balkenSpur, Offset(size.width / 3, 0f), Offset(size.width / 3, size.height), 1.dp.toPx())
                drawLine(f.balkenSpur, Offset(size.width * 2 / 3, 0f), Offset(size.width * 2 / 3, size.height), 1.dp.toPx())
            }.padding(vertical = 8.dp),
        ) {
            Wert("ZEIT", r.zeitMinuten?.let { "$it Min." } ?: "–", Modifier.weight(1f))
            Wert("KCAL", r.kcalJePortion?.toString() ?: "–", Modifier.weight(1f))
            Wert("GEKOCHT", "${r.gekocht}×", Modifier.weight(1f))
        }
        zutatenSatz(r)?.let { Text(it, style = Sage.text(16f, kursiv = immersiv), color = f.nebentext, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun Wert(name: String, wert: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = Sage.titel(10f, 0.16f), color = FoodyTheme.farben.nebentext)
        Text(wert, style = Sage.text(19f), color = FoodyTheme.farben.text)
    }
}

/** Emblem des Entwurfs: Kreise, vier Rauten, Schale mit Dampf und der Anfangsbuchstabe. */
@Composable
private fun Emblem(zeichen: String) {
    val glut = FoodyTheme.stil.zier
    val text = FoodyTheme.farben.text
    val pfade = remember { EmblemPfade() }
    Box(Modifier.size(168.dp).clearAndSetSemantics { }) {
        Canvas(Modifier.matchParentSize()) {
            scale(size.width / 168f, Offset.Zero) {
                drawCircle(Brush.radialGradient(listOf(glut.copy(alpha = 0.32f), Color.Transparent), Offset(84f, 84f), 80f), 80f, Offset(84f, 84f))
                drawCircle(glut.copy(alpha = 0.7f), 66f, Offset(84f, 84f), style = Stroke(1f))
                drawCircle(glut.copy(alpha = 0.35f), 60f, Offset(84f, 84f), style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f))))
                drawPath(pfade.rauten, glut)
                drawPath(pfade.schale, Color(0xFF15130F))
                drawPath(pfade.schale, glut, style = Stroke(2f))
                drawLine(glut, Offset(40f, 92f), Offset(128f, 92f), 2f, StrokeCap.Round)
                drawPath(pfade.dampf, text.copy(alpha = 0.6f), style = Stroke(1.6f, cap = StrokeCap.Round))
            }
        }
        Text(zeichen, Modifier.align(Alignment.TopCenter).padding(top = 96.dp), style = Sage.titel(20f, 0f), color = glut)
    }
}

private class EmblemPfade {
    private fun pfad(d: String) = PathParser().parsePathString(d).toPath()
    val rauten = pfad("M84 12l5 8-5 8-5-8zM84 140l5 8-5 8-5-8zM12 84l8-5 8 5-8 5zM140 84l8-5 8 5-8 5z")
    val schale = pfad("M46 92h76c0 22-17 36-38 36S46 114 46 92z")
    val dampf = pfad("M70 80c-6-8 6-12 0-22M86 78c-6-9 6-13 0-24M102 80c-6-8 6-12 0-22")
}

@Composable
private fun Blaettern(zeichen: String, beschreibung: String, aktiv: Boolean, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    Box(
        Modifier.size(52.dp).border(1.dp, f.lichtkante)
            .clickable(enabled = aktiv, role = Role.Button, onClick = onKlick)
            .semantics { contentDescription = beschreibung },
        contentAlignment = Alignment.Center,
    ) {
        Text(zeichen, style = Sage.titel(22f, 0f), color = if (aktiv) FoodyTheme.stil.zier else f.nebentext)
    }
}

/** „+“ als eckiger Knopf im Akzent; öffnet „Neues Rezept“ oder „Markdown importieren“. */
@Composable
private fun NeuKnopf(onNeu: () -> Unit, onImport: () -> Unit) {
    var menue by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(48.dp).border(1.dp, FoodyTheme.stil.zier)
                .clickable(role = Role.Button) { menue = true }
                .semantics { contentDescription = "Rezept hinzufügen" },
            contentAlignment = Alignment.Center,
        ) { Text("+", style = Sage.titel(22f, 0f), color = FoodyTheme.farben.primaer) }
        DropdownMenu(menue, onDismissRequest = { menue = false }) {
            DropdownMenuItem(text = { Text("Neues Rezept") }, onClick = { menue = false; onNeu() })
            DropdownMenuItem(text = { Text("Markdown importieren") }, onClick = { menue = false; onImport() })
        }
    }
}

private const val MAX_RAUTEN = 12

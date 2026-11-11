package de.foody.feature.heute.stil

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.animationenErlaubt
import de.foody.core.designsystem.komponenten.FoodyFrage
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.SyncAnzeige
import de.foody.core.designsystem.komponenten.SyncStatus
import de.foody.core.designsystem.komponenten.balkenanteile
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.core.designsystem.stil.sage.SageHauptknopf
import de.foody.core.designsystem.stil.sage.sageHell
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.feature.heute.HEUTE_INHALT
import de.foody.feature.heute.HeuteAktionen
import de.foody.feature.heute.HeuteZustand
import de.foody.feature.heute.TagebuchHeute
import de.foody.feature.heute.anzeigename
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATUM = DateTimeFormatter.ofPattern("EEEE · d. MMMM", Locale.GERMAN)

/**
 * „Heute“ der Düsteren Sage in Immersiv (Entwurf `Q-Heute`): oben die Szene „Rast am Feuer“ mit Datum und Titel,
 * darunter die Vitalität (kcal gegen das Ziel, Nährwerte) und das Menü „Was tun?“ mit einem Hauptknopf. Zustand und
 * Aktionen sind dieselben wie in der normalen Ansicht; Flamme und Glut ruhen, wenn Animationen aus sind.
 */
@Composable
internal fun HeuteSage(zustand: HeuteZustand?, aktionen: HeuteAktionen, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(bottom = ueberLeiste(PLATZ_FUER_LEISTE))
                .testTag(HEUTE_INHALT),
        ) {
            RastAmFeuer(zustand?.datum, zustand?.sync, aktionen.onEinstellungen)
            if (zustand == null) return@Column
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (zustand.datenbankLeer) {
                    LeererZustandFlaeche(
                        Icons.AutoMirrored.Outlined.MenuBook, "Noch keine Rezepte", "Verbinde Foody mit deinem Server, um deine Rezepte zu laden.",
                        aktionText = "Server verbinden", onAktion = aktionen.onEinstellungen,
                    )
                }
                Vitalitaet(zustand.tagebuch)
                WasTun(sageMenue(zustand), aktionen)
            }
        }
    }
}

@Composable
private fun RastAmFeuer(datum: LocalDate?, sync: SyncAnzeige?, onEinstellungen: () -> Unit) {
    val glut = FoodyTheme.stil.zier
    Box(Modifier.fillMaxWidth().heightIn(min = 300.dp)) {
        Lagerfeuer(glut, Modifier.matchParentSize())
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 52.dp).padding(top = 22.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            datum?.let {
                Text(
                    DATUM.format(it).uppercase(Locale.GERMAN), style = Sage.titel(11.5f, 0.3f, FontWeight.Medium),
                    color = FoodyTheme.farben.nebentext, textAlign = TextAlign.Center,
                )
            }
            Text(
                "RAST AM FEUER",
                Modifier.semantics {
                    heading()
                    contentDescription = "Heute"
                },
                style = Sage.titel(30f, 0.14f).copy(shadow = Shadow(Color.Black.copy(alpha = 0.9f), blurRadius = 18f)),
                color = sageHell(),
                textAlign = TextAlign.Center,
            )
            sync?.let { SyncStatus(it, Modifier.padding(top = 6.dp)) }
        }
        IconButton(onEinstellungen, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(4.dp)) {
            Icon(Icons.Outlined.Settings, contentDescription = "Einstellungen", tint = FoodyTheme.farben.nebentext)
        }
    }
}

/**
 * Die Szene des Entwurfs als Zeichnung: Nachthimmel, Mond, Hügel, Ruinen mit Torbogen, Lichtschein, Holzscheite,
 * Flamme und aufsteigende Glut. Gezeichnet im Maß des Entwurfs (390 × 318) und auf die Höhe skaliert, mittig; links
 * und rechts laufen Himmel und Boden weiter.
 */
@Composable
private fun Lagerfeuer(glut: Color, modifier: Modifier) {
    val bewegt = animationenErlaubt()
    val zeit = rememberInfiniteTransition(label = "Feuer")
    val flamme by if (bewegt) {
        zeit.animateFloat(0f, 1f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "Flamme")
    } else {
        remember { mutableStateOf(0f) }
    }
    val funken by if (bewegt) {
        zeit.animateFloat(0f, 1f, infiniteRepeatable(tween(2700, easing = LinearEasing)), label = "Glut")
    } else {
        remember { mutableStateOf(0.35f) }
    }
    val pfade = remember { SzenePfade() }
    Canvas(modifier.clearAndSetSemantics { }) {
        val s = size.height / ENTWURF_HOEHE
        val x0 = (size.width - ENTWURF_BREITE * s) / 2f
        drawRect(Brush.verticalGradient(0f to Color(0xFF07070A), 0.7f to Color(0xFF1A1712), 1f to Color(0xFF0D0C0A)))
        // Hügel und Boden über die ganze Breite
        drawRect(Color(0xFF121010), Offset(0f, 214f * s), Size(size.width, size.height - 214f * s))
        translate(x0, 0f) {
            scale(s, Offset.Zero) {
                drawCircle(Color(0xFFC9C2AE).copy(alpha = 0.12f), 20f, Offset(300f, 62f))
                drawPath(pfade.huegel, Color(0xFF121010))
                pfade.ruinen.forEach { drawPath(it, Color(0xFF191612)) }
            }
        }
        // Lichtschein des Feuers
        drawRect(
            Brush.radialGradient(
                0f to glut.copy(alpha = 0.45f), 0.5f to glut.copy(alpha = 0.1f), 1f to Color.Transparent,
                center = Offset(size.width / 2f, size.height * 0.86f), radius = size.height * 0.62f,
            ),
        )
        drawRect(Color(0xFF0D0C0A), Offset(0f, 268f * s), Size(size.width, size.height - 268f * s))
        translate(x0, 0f) {
            scale(s, Offset.Zero) {
                drawLine(Color(0xFF3A2A18), Offset(160f, 270f), Offset(230f, 264f), 6f, StrokeCap.Round)
                drawLine(Color(0xFF3A2A18), Offset(168f, 266f), Offset(226f, 274f), 6f, StrokeCap.Round)
                feuer(pfade, flamme)
                glutFunken(glut, funken)
            }
        }
    }
}

private fun DrawScope.feuer(pfade: SzenePfade, flamme: Float) {
    withTransform({ scale(1f, 1f + 0.06f * flamme, Offset(195f, 268f)); translate(0f, -2f * flamme) }) {
        drawPath(pfade.flamme, Brush.verticalGradient(0f to Color(0xFFFDE68A), 0.5f to Color(0xFFF59E0B), 1f to Color(0xFFC2410C), startY = 196f, endY = 268f))
        drawPath(pfade.kern, Color(0xFFFFF7D6))
    }
}

private fun DrawScope.glutFunken(glut: Color, t: Float) {
    listOf(Triple(186f, 236f, 0f), Triple(204f, 230f, 0.3f), Triple(196f, 240f, 0.55f)).forEachIndexed { i, (x, y, versatz) ->
        val p = (t + versatz) % 1f
        drawCircle(glut.copy(alpha = 0.9f * (1f - p)), 1.2f + 0.2f * i, Offset(x, y - 70f * p))
    }
}

/** Pfade aus dem SVG des Entwurfs (Maß 390 × 318). */
private class SzenePfade {
    private fun pfad(d: String) = PathParser().parsePathString(d).toPath()
    val huegel = pfad("M0 236L60 214 96 222 150 196 210 208 260 186 330 210 390 200V318H0z")
    val ruinen = listOf(
        pfad("M70 250V128l8-10h22l6 12v120z"),
        pfad("M62 128h52v10H62z"),
        pfad("M276 250V150l6-8 10 4 4-14 12 6v112z"),
        pfad("M106 150q84-90 170 0v14q-86-76-170 0z"),
        pfad("M150 250v-60h12v60zM228 250v-48h12v48z"),
    )
    val flamme = pfad("M195 268c-22 0-30-16-24-32 4-10 12-14 12-26 8 6 10 14 10 20 4-8 10-14 8-26 14 10 22 26 20 40-2 16-12 24-26 24z")
    val kern = pfad("M195 268c-9 0-13-7-10-14 2-5 6-7 6-12 4 3 5 7 5 9 2-4 5-6 4-12 6 5 9 12 8 18-1 7-6 11-13 11z")
}

/** kcal gegen das Ziel als Balken in Gold, darunter Eiweiß, Kohlenhydrate und Fett; TalkBack liest einen Satz. */
@Composable
private fun Vitalitaet(t: TagebuchHeute) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    val s = t.summe
    val ziel = t.ziel?.takeIf { it > 0 }
    val ueber = ziel != null && s.kcal > ziel
    val satz = (if (ziel != null) "${ganzzahlDeutsch(s.kcal)} von ${ganzzahlDeutsch(ziel)} kcal" else "${ganzzahlDeutsch(s.kcal)} kcal") +
        (if (ueber) ", über Ziel" else "") + ": Eiweiß ${s.eiweiss} g, Kohlenhydrate ${s.kohlenhydrate} g, Fett ${s.fett} g"
    Column(Modifier.clearAndSetSemantics { contentDescription = satz }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("VITALITÄT", Modifier.weight(1f), style = Sage.titel(12f, 0.2f), color = f.nebentext)
            Text(ganzzahlDeutsch(s.kcal), style = Sage.titel(22f, 0.02f), color = sageHell())
            Text(
                (if (ziel != null) " / ${ganzzahlDeutsch(ziel)} kcal" else " kcal") + if (ueber) " · über Ziel" else "",
                style = Sage.text(15f), color = f.nebentext,
            )
        }
        if (ziel != null) {
            val anteil = (s.kcal.toFloat() / ziel).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth().height(10.dp).background(FoodyTheme.werte.glas).border(1.dp, f.lichtkante)
                    .drawBehind {
                        drawRect(
                            Brush.horizontalGradient(listOf(lerp(glut, Color.Black, 0.45f), glut)),
                            Offset(1.dp.toPx(), 1.dp.toPx()),
                            Size((size.width - 2.dp.toPx()) * anteil, size.height - 2.dp.toPx()),
                        )
                    },
            )
        }
        val a = balkenanteile(s.kcal, null, s.eiweiss, s.kohlenhydrate, s.fett)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Makro("Eiweiß", s.eiweiss, a.eiweiss, f.makro.eiweiss, Modifier.weight(1f))
            Makro("KH", s.kohlenhydrate, a.kh, f.makro.kohlenhydrate, Modifier.weight(1f))
            Makro("Fett", s.fett, a.fett, f.makro.fett, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Makro(name: String, gramm: Int, anteil: Float, farbe: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row {
            Text(name, Modifier.weight(1f), style = Sage.text(14f), color = FoodyTheme.farben.nebentext)
            Text("$gramm g", style = Sage.text(14f), color = FoodyTheme.farben.text)
        }
        Box(
            Modifier.fillMaxWidth().height(4.dp).background(FoodyTheme.werte.glas).drawBehind {
                drawRect(farbe, size = Size(size.width * anteil.coerceIn(0f, 1f), size.height))
            },
        )
    }
}

/** Menü „Was tun?“: ein Eintrag ist gewählt, der Hauptknopf darunter führt ihn aus. Darunter zwei Wege zum Eintragen. */
@Composable
private fun WasTun(menue: List<SageEintrag>, aktionen: HeuteAktionen) {
    val f = FoodyTheme.farben
    val glut = FoodyTheme.stil.zier
    var gewaehlt by rememberSaveable { mutableIntStateOf(0) }
    // Geplante Mahlzeit, zu der gerade „Gegessen?“ gefragt wird; übersteht Drehen
    var frage by rememberSaveable { mutableStateOf<String?>(null) }
    val aktiv = menue.getOrNull(gewaehlt) ?: menue.first()
    Column(Modifier.selectableGroup().drawBehind { drawLine(f.lichtkante, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }) {
        menue.forEachIndexed { i, e ->
            val an = e == aktiv
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .then(if (an) Modifier.background(Brush.horizontalGradient(listOf(glut.copy(alpha = 0.18f), Color.Transparent))) else Modifier)
                    .selectable(an, role = Role.RadioButton) { gewaehlt = i }
                    .semantics(mergeDescendants = true) { contentDescription = "${e.beschreibung}. ${e.zeile}" }
                    .drawBehind { drawLine(f.balkenSpur, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(9.dp).rotate(45f).background(if (an) glut else Color.Transparent).border(1.5.dp, glut))
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(e.titel, style = Sage.titel(15f, 0.06f), color = if (an) sageHell() else f.nebentext)
                    Text(e.zeile, style = Sage.text(16f), color = f.nebentext)
                }
            }
        }
    }
    val ausfuehren = {
        when (aktiv.ziel) {
            SageZiel.KOCHEN -> aktionen.onKochen(requireNotNull(aktiv.rezeptId), aktiv.portionen)
            SageZiel.GEGESSEN -> frage = aktiv.vorschlag?.planEintragId
            SageZiel.REZEPT -> aktionen.onRezept(requireNotNull(aktiv.rezeptId))
            SageZiel.EINTRAGEN -> aktionen.onEintragen(aktiv.mahlzeit)
            SageZiel.CHRONIK -> aktionen.onAlleEintraege()
            SageZiel.EINKAUF -> aktionen.onEinkauf(requireNotNull(aktiv.listeId))
        }
    }
    SageHauptknopf(aktiv.aktion, aktiv.beschreibung, ausfuehren)
    val naechstes = menue.firstOrNull { it.ziel == SageZiel.KOCHEN }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        naechstes?.let { n -> NebenWeg("REZEPT ANSEHEN", "Rezept ansehen", Modifier.weight(1f)) { aktionen.onRezept(requireNotNull(n.rezeptId)) } }
        NebenWeg("+ SCANNEN · FOTO", "Eintragen mit Scan oder Foto", Modifier.weight(1f)) { aktionen.onEintragen(null) }
    }
    frage?.let { id -> menue.firstOrNull { it.vorschlag?.planEintragId == id } }?.let { e ->
        val v = requireNotNull(e.vorschlag)
        val name = requireNotNull(e.mahlzeit)
        FoodyFrage(
            titel = "Gegessen?",
            text = "„${v.titel}“ als ${name.anzeigename()} eintragen? Bei „Nein“ verschwindet der Vorschlag für heute.",
            ja = "Ja",
            onJa = { frage = null; aktionen.onGegessen(v.planEintragId) },
            onNein = { frage = null; aktionen.onNichtGegessen(v) },
            nein = "Nein",
            onAbbrechen = { frage = null },
        )
    }
}

@Composable
private fun NebenWeg(text: String, beschreibung: String, modifier: Modifier, onKlick: () -> Unit) {
    val f = FoodyTheme.farben
    Box(
        modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onKlick)
            .semantics(mergeDescendants = true) { contentDescription = beschreibung }
            .drawBehind { drawLine(f.lichtkante, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Sage.titel(11f, 0.14f), color = f.nebentext, textAlign = TextAlign.Center)
    }
}

private const val ENTWURF_BREITE = 390f
private const val ENTWURF_HOEHE = 318f
private val PLATZ_FUER_LEISTE = 104.dp

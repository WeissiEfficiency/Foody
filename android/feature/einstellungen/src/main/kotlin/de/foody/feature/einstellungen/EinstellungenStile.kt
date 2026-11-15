package de.foody.feature.einstellungen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilAbschnitt
import de.foody.core.designsystem.stil.StilAktion
import de.foody.core.designsystem.stil.StilAkzent
import de.foody.core.designsystem.stil.StilFeld
import de.foody.core.designsystem.stil.StilKarte
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.bayrisch.bayrischBlau
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.edo.edoShu
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.stil.kreuzer.kreuzerAmber

/*
 * Einstellungen der Stile R–V (Immersiv) wie in den Entwürfen: Abschnitte als Karten (Anime, Bayrisch) oder durch
 * Linien getrennt (Edo, Grauen, Kreuzer), der Abgleich als eigene Karte (Papier, Tafel, Amberkarte mit Schnittecken),
 * Zeilen mit gepunkteter oder gestrichelter Linie, Schalter, Modus-Leiste und Farbmuster in der Form des Stils, das
 * Tagesziel mit − und +.
 */

/** Grenzen des Tagesziels wie im ViewModel; − und + gehen in Schritten von [ZIEL_SCHRITT]. */
internal const val ZIEL_MIN = 500
internal const val ZIEL_MAX = 10_000
internal const val ZIEL_SCHRITT = 50
private const val ZIEL_START = 2000

/** Neues Ziel nach − oder + ([richtung] −1 oder 1); ohne gültige Eingabe beginnt es bei 2.000 kcal. */
internal fun zielSchritt(text: String, richtung: Int): Int = ((text.trim().toIntOrNull() ?: ZIEL_START) + richtung * ZIEL_SCHRITT).coerceIn(ZIEL_MIN, ZIEL_MAX)

/** Ein Abschnitt im Stil: [titel] als Überschrift (TalkBack liest [beschreibung]), [sync] = die Karte des Abgleichs. */
@Composable
internal fun StilRahmen(titel: String?, beschreibung: String?, sync: Boolean, inhalt: @Composable ColumnScope.() -> Unit) {
    val s = FoodyTheme.stil
    val abstand = Arrangement.spacedBy(10.dp)
    when {
        s.stil == FoodyStil.KREUZER && sync -> {
            val amber = kreuzerAmber()
            val form = Kreuzer.ecke()
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(amber.copy(alpha = 0.10f), amber.copy(alpha = 0.02f))), form)
                    .border(1.dp, amber.copy(alpha = 0.45f), form)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = abstand,
            ) {
                if (titel != null) StilAbschnitt(titel, beschreibung = beschreibung, farbe = amber)
                inhalt()
            }
        }
        s.stil == FoodyStil.ANIME || s.stil == FoodyStil.BAYRISCH || (sync && (s.stil == FoodyStil.EDO || s.stil == FoodyStil.GRAUEN)) ->
            StilKarte {
                if (titel != null) StilAbschnitt(titel, beschreibung = beschreibung)
                inhalt()
            }
        else -> {
            val linie = trennlinie()
            Column(
                Modifier.fillMaxWidth().drawBehind { drawLine(linie, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }.padding(top = 10.dp),
                verticalArrangement = abstand,
            ) {
                if (titel != null) StilAbschnitt(titel, beschreibung = beschreibung)
                inhalt()
            }
        }
    }
}

/** Linie zwischen Abschnitten (Edo, Grauen, Kreuzer). */
@Composable
private fun trennlinie(): Color = when (FoodyTheme.stil.stil) {
    FoodyStil.EDO -> Color(0xFFD8CBAE)
    FoodyStil.GRAUEN -> FoodyTheme.stil.zier.copy(alpha = 0.22f)
    else -> FoodyTheme.farben.nebentext.copy(alpha = 0.3f)
}

/** Schriften einer Zeile: Titel und Info darunter. */
@Composable
internal fun zeilenSchrift(): Pair<TextStyle, TextStyle> = when (FoodyTheme.stil.stil) {
    FoodyStil.ANIME -> Anime.text(14.5f, FontWeight.ExtraBold) to Anime.text(12f)
    FoodyStil.EDO -> Edo.text(15f, FontWeight.Medium) to Edo.text(12f)
    FoodyStil.BAYRISCH -> Bayrisch.titel(15.5f) to Bayrisch.text(12.5f)
    FoodyStil.GRAUEN -> Grauen.text(17f) to Grauen.maschine(11.5f)
    else -> Kreuzer.titel(15.5f, FontWeight.Bold, 0f) to Kreuzer.mono(11f, 0.02f)
}

/** Linie unter einer Zeile: gepunktet (Anime, Edo), gestrichelt (Bayrisch), durchgezogen (Grauen, Kreuzer). */
@Composable
private fun Modifier.zeilenLinie(): Modifier {
    val s = FoodyTheme.stil
    val f = FoodyTheme.farben
    val (farbe, breite, muster) = when (s.stil) {
        FoodyStil.ANIME -> Triple(f.text.copy(alpha = 0.16f), 2.dp, floatArrayOf(0.1f, 6f))
        FoodyStil.EDO -> Triple(Edo.papierRand, 1.dp, floatArrayOf(0.1f, 4f))
        FoodyStil.BAYRISCH -> Triple(Color(0xFFCDBB9C), 1.dp, floatArrayOf(6f, 4f))
        FoodyStil.GRAUEN -> Triple(s.zier.copy(alpha = 0.22f), 1.dp, null)
        else -> Triple(f.nebentext.copy(alpha = 0.3f), 1.dp, null)
    }
    return drawBehind {
        val y = size.height - breite.toPx() / 2
        drawLine(
            farbe, Offset(0f, y), Offset(size.width, y), breite.toPx(),
            cap = if (muster != null && muster[0] < 1f) StrokeCap.Round else StrokeCap.Butt,
            pathEffect = muster?.let { PathEffect.dashPathEffect(it) },
        )
    }
}

@Composable
private fun RowScope.ZeilenTexte(titel: String, info: String?, infoFarbe: Color? = null) {
    val (t, i) = zeilenSchrift()
    Column(Modifier.weight(1f)) {
        Text(titel, style = t, color = FoodyTheme.farben.text)
        if (info != null) Text(info, style = i, color = infoFarbe ?: FoodyTheme.farben.nebentext)
    }
}

/** Zeile mit Schalter des Stils; die ganze Zeile schaltet (TalkBack: Schalter). */
@Composable
internal fun SchalterZeile(titel: String, info: String?, an: Boolean, onWechsel: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).zeilenLinie().toggleable(an, role = Role.Switch, onValueChange = onWechsel)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ZeilenTexte(titel, info)
        StilSchalter(an)
    }
}

/** Zeile, die eine Seite öffnet, mit „›“ rechts; [anfang] steht links (etwa die Marke „verbunden“). */
@Composable
internal fun VerweisZeile(titel: String, info: String?, onKlick: () -> Unit, infoFarbe: Color? = null, anfang: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).zeilenLinie().clickable(role = Role.Button, onClick = onKlick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        anfang?.invoke()
        ZeilenTexte(titel, info, infoFarbe)
        val s = FoodyTheme.stil
        Text(
            "›",
            Modifier.clearAndSetSemantics {},
            style = zeilenSchrift().first.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
            color = if (s.stil == FoodyStil.KREUZER) s.zier else FoodyTheme.farben.text,
        )
    }
}

/** Kleine Überschrift über einer Wahl („Stil“, „Variante“, „Farbe“). */
@Composable
internal fun Unterkopf(text: String) = Text(text, style = zeilenSchrift().first, color = FoodyTheme.farben.text)

/** Marke „verbunden“ vor der Zeile des Abgleichs: Stempel 繋 (Edo), Punkt (Bayrisch, Grauen), Quadrat (Kreuzer). */
@Composable
internal fun VerbundenMarke() {
    val s = FoodyTheme.stil
    val m = Modifier.clearAndSetSemantics {}
    when (s.stil) {
        // Mindestens 26 dp; bei großer Schrift wächst der Stempel mit dem Zeichen
        FoodyStil.EDO -> Box(
            m.sizeIn(minWidth = 26.dp, minHeight = 26.dp).rotate(-6f).background(edoShu()).padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("繋", style = Edo.titel(13f), color = Color.White)
        }
        FoodyStil.BAYRISCH -> Box(m.size(12.dp).background(Color(0xFF2E5E3A), CircleShape))
        FoodyStil.GRAUEN -> Box(m.size(10.dp).shadow(6.dp, CircleShape, ambientColor = s.zier, spotColor = s.zier).background(s.zier, CircleShape))
        FoodyStil.KREUZER -> Box(m.size(9.dp).shadow(6.dp, RectangleShape, ambientColor = KREUZER_GRUEN, spotColor = KREUZER_GRUEN).background(KREUZER_GRUEN))
        else -> Text("✓", m, style = Anime.text(15f, FontWeight.ExtraBold), color = FoodyTheme.farben.text)
    }
}

private val KREUZER_GRUEN = Color(0xFF7CF29A)

/** Maße und Farben eines Schalters: Bahn mit Rand, darin der Knopf. */
private class Bild(
    val breite: Dp,
    val hoehe: Dp,
    val form: Shape,
    val rand: Dp,
    val randFarbe: Color,
    val bahn: Color,
    val knopf: Color,
    val knopfRand: Color?,
)

/** Schalter im Stil (nur Bild; die Zeile trägt die Bedeutung). */
@Composable
internal fun StilSchalter(an: Boolean) {
    val s = FoodyTheme.stil
    val f = FoodyTheme.farben
    val m = Modifier.clearAndSetSemantics {}
    if (s.stil == FoodyStil.KREUZER) {
        Box(
            m.width(56.dp).heightIn(min = 28.dp).background(if (an) s.zier.copy(alpha = 0.08f) else Color.Transparent)
                .border(1.dp, if (an) s.zier else f.nebentext.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) { Text(if (an) "AN" else "AUS", style = Kreuzer.mono(11.5f, 0.08f), color = if (an) s.zier else f.nebentext) }
        return
    }
    val rund = RoundedCornerShape(99.dp)
    val b = when (s.stil) {
        FoodyStil.ANIME -> Bild(50.dp, 28.dp, rund, 2.5.dp, f.text, if (an) s.akzent?.farbe ?: f.primaer else Color(0xFFF2EEF4), Color.White, f.text)
        FoodyStil.EDO -> Bild(50.dp, 28.dp, RectangleShape, 1.5.dp, f.text, if (an) edoShu() else Color.Transparent, if (an) Color.White else f.text, null)
        FoodyStil.BAYRISCH -> Bild(52.dp, 30.dp, rund, 2.dp, f.text, if (an) bayrischBlau() else Color.White, Color.White, f.text)
        else -> Bild(
            50.dp, 26.dp, rund, 1.dp, if (an) s.zier else f.nebentext.copy(alpha = 0.5f), if (an) s.zier.copy(alpha = 0.12f) else Color.Transparent,
            if (an) s.zier else f.nebentext, null,
        )
    }
    val knopfForm = if (b.form == RectangleShape) RectangleShape else CircleShape
    Box(m.size(b.breite, b.hoehe).background(b.bahn, b.form).border(b.rand, b.randFarbe, b.form).padding(horizontal = 3.dp)) {
        Box(
            Modifier.align(if (an) Alignment.CenterEnd else Alignment.CenterStart).size(b.hoehe - 9.dp).background(b.knopf, knopfForm)
                .then(if (b.knopfRand != null) Modifier.border(1.5.dp, b.knopfRand, knopfForm) else Modifier),
        )
    }
}

/** Leiste mit drei Feldern zur Wahl (TalkBack: Optionsfelder): Pillen (Anime), Felder mit Rahmen, Kreuzer mit Lücken. */
@Composable
internal fun <T> Segmente(optionen: List<Pair<T, String>>, gewaehlt: T, onWahl: (T) -> Unit) {
    val s = FoodyTheme.stil
    val f = FoodyTheme.farben
    val luecke = when (s.stil) {
        FoodyStil.ANIME -> 6.dp
        FoodyStil.KREUZER -> 4.dp
        else -> 0.dp
    }
    val aussen: Modifier = when (s.stil) {
        FoodyStil.EDO -> Modifier.border(1.5.dp, f.text)
        FoodyStil.BAYRISCH -> Modifier.clip(RoundedCornerShape(10.dp)).border(2.dp, f.text, RoundedCornerShape(10.dp))
        FoodyStil.GRAUEN -> Modifier.border(1.dp, s.zier.copy(alpha = 0.3f))
        else -> Modifier
    }
    Row(Modifier.fillMaxWidth().then(aussen).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(luecke)) {
        optionen.forEach { (wert, text) ->
            val an = wert == gewaehlt
            val (grund, schrift, rand) = when (s.stil) {
                FoodyStil.ANIME -> Triple(if (an) Anime.gelb else Color.White, f.text, Modifier.border(2.5.dp, f.text, Anime.pille))
                FoodyStil.EDO -> Triple(if (an) f.text else Color.Transparent, if (an) Edo.papier else f.text, Modifier)
                FoodyStil.BAYRISCH -> Triple(if (an) bayrischBlau() else Color.White, if (an) Color.White else f.text, Modifier)
                FoodyStil.GRAUEN -> Triple(
                    if (an) s.zier.copy(alpha = 0.1f) else Color.Transparent, f.text,
                    Modifier.drawBehind {
                        if (an) drawLine(s.zier, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx())
                    },
                )
                else -> Triple(
                    if (an) s.zier.copy(alpha = 0.08f) else Color.Transparent, if (an) s.zier else f.text,
                    Modifier.border(1.dp, if (an) s.zier else f.nebentext.copy(alpha = 0.4f)),
                )
            }
            val form = if (s.stil == FoodyStil.ANIME) Anime.pille else RectangleShape
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(form).background(grund, form).then(rand)
                    .selectable(an, role = Role.RadioButton) { onWahl(wert) }.padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                val stilSchrift = when (s.stil) {
                    FoodyStil.ANIME -> Anime.text(13f)
                    FoodyStil.EDO -> Edo.text(13.5f, FontWeight.Bold)
                    FoodyStil.BAYRISCH -> Bayrisch.text(14f, FontWeight.Bold)
                    FoodyStil.GRAUEN -> Grauen.maschine(13f)
                    else -> Kreuzer.mono(12.5f, 0.04f)
                }
                Text(if (s.stil == FoodyStil.KREUZER) text.uppercase() else text, style = stilSchrift, color = schrift, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Die vier Akzente als Farbmuster in der Form des Stils (TalkBack: Optionsfelder mit dem Namen der Farbe). */
@Composable
internal fun AkzentReihe(akzente: List<StilAkzent>, gewaehlt: StilAkzent, onWahl: (StilAkzent) -> Unit) {
    val s = FoodyTheme.stil
    val f = FoodyTheme.farben
    val form: Shape = when (s.stil) {
        FoodyStil.EDO -> RectangleShape
        FoodyStil.KREUZER -> Kreuzer.ecke(10.dp)
        else -> CircleShape
    }
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        akzente.forEach { a ->
            val an = a == gewaehlt
            val ring = when (s.stil) {
                FoodyStil.GRAUEN -> a.farbe
                FoodyStil.KREUZER -> s.zier
                else -> f.text
            }
            Box(
                Modifier.size(52.dp).selectable(an, role = Role.RadioButton) { onWahl(a) }.semantics { contentDescription = a.anzeigename }
                    .then(if (an && s.stil != FoodyStil.ANIME) Modifier.border(2.dp, ring, form) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                val innen = if (s.stil == FoodyStil.ANIME) 38.dp else 40.dp
                val rand = when (s.stil) {
                    FoodyStil.ANIME -> if (an) 3.5.dp else 2.dp
                    FoodyStil.GRAUEN, FoodyStil.KREUZER -> 1.dp
                    else -> 2.dp
                }
                val randFarbe = if (s.stil == FoodyStil.GRAUEN || s.stil == FoodyStil.KREUZER) f.nebentext.copy(alpha = 0.4f) else f.text
                Box(Modifier.size(innen).background(a.farbe, form).border(rand, randFarbe, form))
            }
        }
    }
}

/**
 * Tagesziel mit − und +: Anime mit dem Feld in der Mitte und „kcal“ daneben, sonst links „Tagesziel“ mit Einheit,
 * rechts −, Feld, +. Bei großer Schrift steht die Beschriftung über den Knöpfen. Leeres Feld = kein Ziel.
 */
@Composable
internal fun ZielStil(text: String, fehler: Boolean, onText: (String) -> Unit, onFertig: () -> Unit) {
    val s = FoodyTheme.stil.stil
    val schritt = { richtung: Int ->
        onText(zielSchritt(text, richtung).toString())
        onFertig()
    }
    val knoepfe = @Composable { feldBreite: Modifier ->
        StilAktion("−", { schritt(-1) }, Modifier.widthIn(min = 48.dp), beschreibung = "Ziel senken")
        StilFeld(
            "Tagesziel in kcal", text, onText, feldBreite, tastatur = KeyboardType.Number, etikettZeigen = false, zentriert = true,
            onFertig = onFertig,
        )
        StilAktion("+", { schritt(1) }, Modifier.widthIn(min = 48.dp), beschreibung = "Ziel erhöhen")
    }
    val gross = LocalDensity.current.fontScale > 1.3f
    if (s == FoodyStil.ANIME) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            knoepfe(Modifier.weight(1f))
        }
        Text("kcal", Modifier.fillMaxWidth(), style = Anime.text(13f, FontWeight.ExtraBold), color = FoodyTheme.farben.text, textAlign = TextAlign.Center)
    } else {
        val (t, i) = zeilenSchrift()
        val beschriftung = @Composable {
            Column(Modifier.clearAndSetSemantics {}) {
                Text(if (s == FoodyStil.KREUZER) "Energieziel" else "Tagesziel", style = t, color = FoodyTheme.farben.text)
                Text(
                    when (s) {
                        FoodyStil.EDO -> "Kilokalorien"
                        FoodyStil.KREUZER -> "KCAL / TAG"
                        else -> "kcal"
                    },
                    style = i, color = FoodyTheme.farben.nebentext,
                )
            }
        }
        if (gross) {
            beschriftung()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                knoepfe(Modifier.weight(1f))
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) { beschriftung() }
                knoepfe(Modifier.width(96.dp))
            }
        }
    }
    if (fehler) {
        Text("500 bis 10.000 kcal", style = zeilenSchrift().second, color = MaterialTheme.colorScheme.error)
    } else if (text.isBlank()) {
        Text("Kein Ziel gesetzt", style = zeilenSchrift().second, color = FoodyTheme.farben.nebentext)
    }
}

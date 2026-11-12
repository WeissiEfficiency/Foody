package de.foody.feature.kochen.stil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilAktion
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.domain.StepTimer
import de.foody.feature.kochen.KochAktionen
import de.foody.feature.kochen.KochSchritt
import de.foody.feature.kochen.KochZustand
import de.foody.feature.kochen.restzeit
import java.util.Locale
import kotlin.math.min

/*
 * Kochmodus der Stile R–V in Immersiv (Entwürfe `R/S/T/U/V-Kochen`). Alle fünf haben denselben Aufbau wie die Sage:
 * Kopf mit ✕, Überzeile und Rezept, Segmente; „Bereitlegen“ mit Abhaken und Portionen; je Schritt die Nummer groß
 * im Akzent, der Text groß, die genannten Zutaten und die Zeiten als Timer; unten Zurück und Weiter. Wörter und
 * Schriften kommen aus [KochArt]. Derselbe Pager, dieselben [KochAktionen] und dasselbe Fertig-Blatt.
 */

internal object KochSzene {
    /** Der Stil R–V, dessen Szene den Kochmodus zeigt; `null` = keiner (Milchglas, Ruhig, Sage hat ihre eigene). */
    @Composable
    fun aktuell(): FoodyStil? {
        val s = FoodyTheme.stil
        if (s.variante != StilVariante.IMMERSIV) return null
        return s.stil.takeIf { it != FoodyStil.MILCHGLAS && it != FoodyStil.SAGE }
    }
}

/** Wörter und Schriften eines Stils für den Kochmodus. */
internal class KochArt(
    val bereit: String,
    val ueberSchritt: (Int, Int) -> String,
    val ueberBereit: String,
    /** Nummer des Schritts groß: Schritt und Zahl der Schritte. */
    val nummer: (Int, Int) -> String,
    val brauchst: String,
    val naechstes: String,
    val beginnen: String,
    val weiter: String,
    val fertig: String,
    val zurueck: String,
    val timer: String,
    val portionen: (Int) -> String,
    val kopf: (String, Int) -> String,
    val titel: (Float) -> TextStyle,
    val text: (Float) -> TextStyle,
    val klein: (Float) -> TextStyle,
)

private fun portionenText(n: Int) = if (n == 1) "1 Portion" else "$n Portionen"

/** „I“, „II“ … „XXXIX“; darüber die Zahl. */
internal fun roemisch(zahl: Int): String {
    if (zahl !in 1..39) return zahl.toString()
    return "X".repeat(zahl / 10) + listOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX")[zahl % 10]
}

internal fun kochArt(stil: FoodyStil): KochArt = when (stil) {
    FoodyStil.ANIME -> KochArt(
        bereit = "BEREITLEGEN!", ueberSchritt = { n, g -> "SCHRITT $n VON $g" }, ueberBereit = "VORBEREITUNG", nummer = { n, _ -> "$n" },
        brauchst = "DU BRAUCHST", naechstes = "ALS NÄCHSTES", beginnen = "LOS GEHT'S! ▶", weiter = "WEITER ▶", fertig = "FERTIG!",
        zurueck = "◀", timer = "⏱ Start", portionen = { "$it Port." }, kopf = { name, p -> "$name · $p Port." },
        titel = { Anime.titel(it, 0f) }, text = { Anime.text(it, FontWeight.Medium) }, klein = { Anime.text(it, FontWeight.ExtraBold) },
    )
    FoodyStil.EDO -> KochArt(
        bereit = "Bereitlegen", ueberSchritt = { n, g -> "Schritt $n von $g" }, ueberBereit = "Vorbereitung", nummer = { n, _ -> "$n" },
        brauchst = "ZUTATEN", naechstes = "ALS NÄCHSTES", beginnen = "BEGINNEN", weiter = "WEITER", fertig = "FERTIG", zurueck = "Zurück",
        timer = "Timer starten", portionen = ::portionenText, kopf = { name, p -> "$name · $p Port.".uppercase(Locale.GERMAN) },
        titel = { Edo.titel(it) }, text = { Edo.text(it) }, klein = { Edo.kopf(it) },
    )
    FoodyStil.BAYRISCH -> KochArt(
        bereit = "Herrichten", ueberSchritt = { n, g -> "Schritt $n von $g" }, ueberBereit = "Herrichten", nummer = { n, _ -> "$n." },
        brauchst = "Des brauchst", naechstes = "Danach", beginnen = "Pack ma's!", weiter = "Weida", fertig = "Feddig!", zurueck = "Zruck",
        timer = "Uhr aufziehn", portionen = { if (it == 1) "1 Portion" else "$it Portiona" },
        kopf = { name, p -> "$name · " + if (p == 1) "1 Portion" else "$p Portiona" },
        titel = { Bayrisch.titel(it) }, text = { Bayrisch.text(it) }, klein = { Bayrisch.text(it, FontWeight.Bold) },
    )
    FoodyStil.GRAUEN -> KochArt(
        bereit = "Was das Ritual verlangt", ueberSchritt = { n, g -> "Schritt $n von $g" }, ueberBereit = "Vorbereitung",
        nummer = { n, _ -> roemisch(n) }, brauchst = "Es verlangt", naechstes = "Danach", beginnen = "Beginnen", weiter = "Weiter", fertig = "Vollenden",
        zurueck = "Zurück", timer = "Die Zeit beginnen", portionen = ::portionenText, kopf = { name, p -> "$name · ${portionenText(p)}" },
        titel = { Grauen.titel(it) }, text = { Grauen.text(it) }, klein = { Grauen.titel(it, 0.1f) },
    )
    else -> KochArt(
        bereit = "LADELISTE", ueberSchritt = { n, g -> "PHASE $n / $g" }, ueberBereit = "STARTVORBEREITUNG",
        nummer = { n, g -> "T-" + (g - n + 1).toString().padStart(2, '0') }, brauchst = "FRACHT", naechstes = "NÄCHSTE PHASE", beginnen = "Mission starten",
        weiter = "Nächste Phase", fertig = "Mission abschließen", zurueck = "Zurück", timer = "Countdown starten", portionen = { "CREW $it" },
        kopf = { name, p -> "MISSION · ${name.uppercase(Locale.GERMAN)} · CREW $p" },
        titel = { Kreuzer.titel(it) }, text = { Kreuzer.titel(it, FontWeight.Normal, 0f) }, klein = { Kreuzer.mono(it) },
    )
}

/** Kopf: ✕, Überzeile und Rezept mittig, darunter die Segmente des Fortschritts. */
@Composable
internal fun KochKopfStil(art: KochArt, z: KochZustand.Da, seite: Int, onSchliessen: () -> Unit) {
    val f = FoodyTheme.farben
    val akzent = FoodyTheme.stil.zier
    val nummer = seite - if (z.hatBereitlegen) 1 else 0
    val ueber = if (z.schritt(seite) == null) art.ueberBereit else art.ueberSchritt(nummer + 1, z.schritte.size)
    Column(Modifier.padding(start = 12.dp, end = 16.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StilAktion("✕", onSchliessen, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), beschreibung = "Kochen beenden")
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ueber, style = art.klein(12f), color = f.nebentext, textAlign = TextAlign.Center)
                Text(art.kopf(z.name, z.portionen), style = art.text(15f), color = f.text, textAlign = TextAlign.Center, maxLines = 2)
            }
            Box(Modifier.size(48.dp))
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(z.seitenzahl) { i -> Box(Modifier.weight(1f).height(5.dp).background(if (i <= seite) akzent else f.balkenSpur)) }
        }
    }
}

/** Kästchen zum Abhaken: gefüllt im Akzent mit ✓, sonst nur Rand. */
@Composable
private fun Haken(an: Boolean) {
    val f = FoodyTheme.farben
    val akzent = FoodyTheme.stil.zier
    Box(
        Modifier.size(24.dp).background(if (an) akzent else Color.Transparent, FoodyTheme.formen.klein)
            .border(2.dp, if (an) akzent else f.nebentext, FoodyTheme.formen.klein),
        contentAlignment = Alignment.Center,
    ) { if (an) Text("✓", style = TextStyle(fontWeight = FontWeight.Bold), color = if (akzent.luminance() > 0.3f) f.text else Color.White) }
}

/** „Bereitlegen“: Zahl der bereiten Zutaten, Portionen und die Zutaten zum Abhaken. */
@Composable
internal fun BereitlegenStil(art: KochArt, z: KochZustand.Da, a: KochAktionen) {
    val f = FoodyTheme.farben
    val k = LocalDensity.current.fontScale
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Der Titel wächst mit der Systemschrift nur bis 130 %
        Text(
            art.bereit, Modifier.semantics { heading(); contentDescription = "Bereitlegen" },
            style = art.titel(28f * min(k, 1.3f) / k), color = f.text,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${z.abgehakt.size} von ${z.bereit.size} bereit", Modifier.weight(1f), style = art.text(16f), color = f.nebentext)
            if (z.abgehakt.isNotEmpty()) StilAktion("Von vorn", a.onAlleZuruecksetzen, beschreibung = "Alle zurücksetzen")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StilAktion("−", { a.onPortionen(false) }, Modifier.sizeIn(minWidth = 52.dp, minHeight = 48.dp), beschreibung = "Weniger Portionen")
            Text(
                art.portionen(z.portionen), Modifier.weight(1f).semantics { contentDescription = portionenText(z.portionen) },
                style = art.titel(18f), color = f.text, textAlign = TextAlign.Center,
            )
            StilAktion("+", { a.onPortionen(true) }, Modifier.sizeIn(minWidth = 52.dp, minHeight = 48.dp), beschreibung = "Mehr Portionen")
        }
        Column {
            z.bereit.forEach { zeile ->
                val bereit = zeile.zeilenIndex in z.abgehakt
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .drawBehind { drawLine(f.balkenSpur, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                        .toggleable(bereit, role = Role.Checkbox, onValueChange = { a.onAbhaken(zeile.zeilenIndex) })
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Haken(bereit)
                    Column(Modifier.weight(1f)) {
                        Text(
                            zeile.name + if (zeile.optional) " (optional)" else "",
                            style = art.text(17f).copy(textDecoration = if (bereit) TextDecoration.LineThrough else null),
                            color = if (bereit) f.nebentext else f.text,
                        )
                        zeile.hinweis?.let { Text(it, style = art.text(14.5f), color = f.nebentext) }
                    }
                    zeile.menge?.let { Text(it, style = art.klein(14.5f), color = f.nebentext, textAlign = TextAlign.End) }
                }
            }
        }
    }
}

/** Ein Schritt: Nummer groß im Akzent, „Schritt 2 von 5“, der Text groß, die genannten Zutaten, Timer und was folgt. */
@Composable
internal fun SchrittStil(art: KochArt, s: KochSchritt, nummer: Int, gesamt: Int, abgehakt: Set<Int>, a: KochAktionen, naechster: String?) {
    val f = FoodyTheme.farben
    val akzent = FoodyTheme.stil.zier
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            art.nummer(nummer, gesamt), Modifier.clearAndSetSemantics { },
            style = art.titel(56f).copy(shadow = Shadow(akzent.copy(alpha = 0.45f), Offset.Zero, 20f)), color = f.primaer,
        )
        Text(
            art.ueberSchritt(nummer, gesamt), Modifier.semantics { heading(); contentDescription = "Schritt $nummer von $gesamt" },
            style = art.klein(13f), color = f.nebentext,
        )
        Text(s.text, style = art.text(24f), color = f.text)
        if (s.zutaten.isNotEmpty()) {
            Column(Modifier.padding(top = 4.dp)) {
                Text(art.brauchst, style = art.klein(12f), color = f.nebentext)
                s.zutaten.forEach { z ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 36.dp)
                            .drawBehind { drawLine(f.balkenSpur, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                            .semantics(mergeDescendants = true) { if (z.zeilenIndex in abgehakt) contentDescription = "${z.name}, bereitgelegt" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(z.name, Modifier.weight(1f), style = art.text(16.5f), color = f.text)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(z.menge ?: "nach Bedarf", style = art.klein(15f), color = f.primaer)
                            z.gesamt?.let { Text("von $it", style = art.text(13.5f), color = f.nebentext) }
                        }
                    }
                }
            }
        }
        s.timer.forEach { t -> Zeitkarte(art, t) { a.onTimerStarten(t) } }
        naechster?.let {
            Column(
                Modifier.padding(top = 6.dp).fillMaxWidth().border(1.dp, f.balkenSpur, FoodyTheme.formen.klein)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(art.naechstes, style = art.klein(12f), color = f.nebentext)
                Text(it, style = art.text(15.5f), color = f.nebentext, maxLines = 2)
            }
        }
    }
}

/** Eine Zeitangabe des Schritts: Name und Dauer, daneben der Knopf, der den Timer startet. */
@Composable
private fun Zeitkarte(art: KochArt, t: StepTimer, onStart: () -> Unit) {
    val f = FoodyTheme.farben
    val akzent = FoodyTheme.stil.zier
    val dauer = restzeit(t.duration.toMillis())
    Row(
        Modifier.padding(top = 8.dp).fillMaxWidth().border(1.5.dp, akzent, FoodyTheme.formen.klein)
            .background(akzent.copy(alpha = 0.1f), FoodyTheme.formen.klein).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.label, style = art.text(16f), color = f.text)
            Text(dauer, style = art.titel(26f), color = f.text)
        }
        StilAktion(art.timer, onStart, beschreibung = "Timer ${t.label} starten, $dauer", voll = true)
    }
}

/** Unten: Zurück und der Hauptknopf „Beginnen“, „Weiter“ oder „Fertig“ in den Wörtern des Stils. */
@Composable
internal fun KochKnoepfeStil(art: KochArt, seite: Int, seitenzahl: Int, onSeite: (Int) -> Unit, onFertig: () -> Unit) {
    val letzte = seite >= seitenzahl - 1
    val text = when {
        letzte -> art.fertig
        seite == 0 -> art.beginnen
        else -> art.weiter
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StilAktion(
            art.zurueck, { if (seite > 0) onSeite(seite - 1) }, Modifier.sizeIn(minWidth = 64.dp, minHeight = 52.dp),
            beschreibung = "Zurück", aktiv = seite > 0,
        )
        StilAktion(
            text, { if (letzte) onFertig() else onSeite(seite + 1) }, Modifier.weight(1f).heightIn(min = 52.dp),
            beschreibung = if (letzte) "Fertig" else "Weiter", voll = true,
        )
    }
}

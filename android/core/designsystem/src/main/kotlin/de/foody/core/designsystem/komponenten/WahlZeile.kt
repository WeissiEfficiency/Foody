package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.bayrisch.bayrischBlau
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.edo.edoShu
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.stil.kreuzer.kreuzerAmber
import de.foody.core.designsystem.token.FoodyAbstaende

/**
 * Auswählbare Zeile für Treffer und Listen: [titel], darunter [info], rechts [wert]. Mit [knopf] = false eine Auswahl
 * (TalkBack: Optionsfeld, [gewaehlt] mit Haken), sonst ein Knopf ohne Auswahl. [ende] steht ganz rechts (etwa ein
 * Löschen-Knopf mit eigener Beschreibung), [anfang] ganz links (etwa ein Tag). Mit [haken] ein Kästchen zum An- und
 * Abwählen (TalkBack: Kontrollkästchen); [aktiv] = false blasst die Zeile ab und sperrt sie. Mindestens 48 dp hoch.
 *
 * In den Stilen R–V (Immersiv) wie in den Entwürfen: umrandete Karte mit Akzentfläche (Anime), Haarlinie mit
 * Zinnobermarke links (Edo), gestrichelte Linie mit Punkt rechts (Bayrisch), Linie mit Glutpunkt (Grauen), Linie mit
 * Amberleiste links (Kreuzer).
 */
@Composable
fun WahlZeile(
    titel: String,
    gewaehlt: Boolean,
    onWahl: () -> Unit,
    modifier: Modifier = Modifier,
    info: String? = null,
    wert: String? = null,
    knopf: Boolean = false,
    haken: Boolean = false,
    aktiv: Boolean = true,
    anfang: (@Composable RowScope.() -> Unit)? = null,
    ende: (@Composable RowScope.() -> Unit)? = null,
) {
    val klick = when {
        haken -> Modifier.toggleable(gewaehlt, enabled = aktiv, role = Role.Checkbox) { onWahl() }
        knopf -> Modifier.clickable(enabled = aktiv, role = Role.Button, onClick = onWahl)
        else -> Modifier.selectable(gewaehlt, enabled = aktiv, onClick = onWahl, role = Role.RadioButton)
    }.alpha(if (aktiv) 1f else 0.5f)
    val a = zeilenAussehen(gewaehlt)
    if (a == null) {
        Row(
            modifier.fillMaxWidth().heightIn(min = 48.dp).then(klick).padding(vertical = FoodyAbstaende.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2),
        ) {
            anfang?.invoke(this)
            ZeilenText(titel, info, MaterialTheme.typography.bodyLarge, MaterialTheme.typography.bodySmall)
            if (wert != null) Text(wert, style = MaterialTheme.typography.labelMedium, color = FoodyTheme.farben.nebentext)
            if (gewaehlt && !knopf) Icon(Icons.Outlined.Check, contentDescription = null, tint = FoodyTheme.farben.primaer)
            ende?.invoke(this)
        }
        return
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = a.hoehe).then(a.grund).then(klick).padding(start = a.links, end = a.rechts, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2),
    ) {
        anfang?.invoke(this)
        ZeilenText(titel, info, a.titel, a.info)
        if (wert != null) Text(wert, style = a.wert, color = FoodyTheme.farben.text)
        a.punkt?.let { (farbe, groesse) ->
            Box(
                Modifier.size(groesse).border(if (groesse > 14.dp) 2.dp else 1.dp, farbe, CircleShape)
                    .background(if (gewaehlt) farbe else Color.Transparent, CircleShape),
            )
        }
        ende?.invoke(this)
    }
}

@Composable
private fun RowScope.ZeilenText(titel: String, info: String?, titelStil: TextStyle, infoStil: TextStyle) {
    Column(Modifier.weight(1f)) {
        Text(titel, style = titelStil, color = FoodyTheme.farben.text)
        if (info != null) Text(info, style = infoStil, color = FoodyTheme.farben.nebentext)
    }
}

/**
 * `true`, wenn [WahlZeile] im aktuellen Stil eigen aussieht (R–V, Immersiv). Listen, deren normale Zeilen anders gebaut
 * sind, nehmen die [WahlZeile] nur dann und behalten sonst ihr Aussehen.
 */
@Composable
fun zeilenImStil(): Boolean = zeilenAussehen(false) != null

/** Aussehen einer Zeile im Stil: Grund (Fläche, Linien, Marke), Abstände, Schriften und ein Punkt rechts. */
private class ZeilenAussehen(
    val grund: Modifier,
    val titel: TextStyle,
    val info: TextStyle,
    val wert: TextStyle,
    val links: Dp = 6.dp,
    val rechts: Dp = 6.dp,
    val hoehe: Dp = 52.dp,
    val punkt: Pair<Color, Dp>? = null,
)

/** `null` = normale Zeile (Milchglas, Sage, Ruhig). */
@Composable
private fun zeilenAussehen(gewaehlt: Boolean): ZeilenAussehen? {
    val s = FoodyTheme.stil
    if (s.variante != StilVariante.IMMERSIV) return null
    val f = FoodyTheme.farben
    return when (s.stil) {
        FoodyStil.ANIME -> {
            val form = RoundedCornerShape(14.dp)
            val grund = if (gewaehlt) lerp(Color.White, s.akzent?.farbe ?: f.primaer, 0.2f) else Color.White
            ZeilenAussehen(
                // Abstand außen, damit Karten in Listen nicht aneinanderstoßen
                Modifier.padding(vertical = 3.dp).background(grund, form).border(2.dp, f.text, form),
                Anime.text(15f, FontWeight.ExtraBold), Anime.text(11.5f), Anime.text(12.5f),
                links = 12.dp, rechts = 12.dp, hoehe = 50.dp,
            )
        }
        FoodyStil.EDO -> {
            val shu = edoShu()
            ZeilenAussehen(
                Modifier.drawBehind {
                    if (gewaehlt) {
                        drawRect(shu.copy(alpha = 0.06f))
                        drawRect(shu, size = Size(6.dp.toPx(), size.height))
                    }
                    drawLine(Edo.papierRand, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                },
                Edo.text(16f, FontWeight.Medium), Edo.text(12f), Edo.text(13f),
                links = 16.dp,
            )
        }
        FoodyStil.BAYRISCH -> {
            val blau = bayrischBlau()
            val strich = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
            ZeilenAussehen(
                Modifier.drawBehind {
                    if (gewaehlt) drawRect(blau.copy(alpha = 0.08f))
                    drawLine(f.nebentext.copy(alpha = 0.45f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx(), pathEffect = strich)
                },
                Bayrisch.titel(16f), Bayrisch.text(12.5f), Bayrisch.text(13f, FontWeight.Bold),
                links = 12.dp, rechts = 12.dp, hoehe = 56.dp, punkt = blau to 20.dp,
            )
        }
        FoodyStil.GRAUEN -> {
            val glut = s.zier
            ZeilenAussehen(
                Modifier.drawBehind {
                    if (gewaehlt) drawRect(glut.copy(alpha = 0.08f))
                    drawLine(glut.copy(alpha = 0.3f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                },
                Grauen.titel(18f, 0f), Grauen.maschine(12f), Grauen.maschine(12.5f),
                hoehe = 58.dp, punkt = glut to 12.dp,
            )
        }
        FoodyStil.KREUZER -> {
            val amber = kreuzerAmber()
            ZeilenAussehen(
                Modifier.drawBehind {
                    if (gewaehlt) {
                        drawRect(amber.copy(alpha = 0.06f))
                        drawRect(amber, size = Size(2.dp.toPx(), size.height))
                    }
                    drawLine(f.nebentext.copy(alpha = 0.3f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                },
                Kreuzer.titel(16f, FontWeight.SemiBold, 0f), Kreuzer.mono(12f, 0.02f), Kreuzer.mono(12.5f, 0.02f),
                links = 10.dp, rechts = 10.dp, hoehe = 58.dp,
            )
        }
        FoodyStil.MILCHGLAS, FoodyStil.SAGE -> null
    }
}

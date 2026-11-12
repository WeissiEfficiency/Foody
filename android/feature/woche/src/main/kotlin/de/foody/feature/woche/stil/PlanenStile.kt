package de.foody.feature.woche.stil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.data.wochentagKurz
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.WahlZeile
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
import java.time.DayOfWeek
import java.time.LocalDate

/** Wochentage als Schriftzeichen wie im Edo-Entwurf (月 = Montag … 日 = Sonntag). */
private val EDO_TAGE = mapOf(
    DayOfWeek.MONDAY to "月", DayOfWeek.TUESDAY to "火", DayOfWeek.WEDNESDAY to "水", DayOfWeek.THURSDAY to "木",
    DayOfWeek.FRIDAY to "金", DayOfWeek.SATURDAY to "土", DayOfWeek.SUNDAY to "日",
)

/** Farben der Tagkästchen im Anime-Entwurf, der Reihe nach. */
private val ANIME_TAGE = listOf(Anime.gelb, Color(0xFF7EE0A8), Anime.tuerkis)

/** Wochentag eines Vorschlags in der Art des Stils: „月“ (Edo), „MO“ (Kreuzer), sonst „Mo“. */
internal fun planenTag(stil: FoodyStil, tag: LocalDate): String = when (stil) {
    FoodyStil.EDO -> EDO_TAGE.getValue(tag.dayOfWeek)
    FoodyStil.KREUZER -> wochentagKurz(tag).uppercase()
    else -> wochentagKurz(tag)
}

/** Satz über den Vorschlägen in R–V (Immersiv) wie in den Entwürfen; sonst `null`. */
@Composable
internal fun planenEinleitung(): Pair<String, TextStyle>? {
    val s = FoodyTheme.stil
    if (s.variante != StilVariante.IMMERSIV) return null
    return when (s.stil) {
        FoodyStil.ANIME -> "Foody schlägt für jeden leeren Tag etwas vor, das länger nicht dran war." to Anime.text(14f)
        FoodyStil.EDO -> "Foody füllt die freien Tage mit Rezepten, die länger nicht dran waren." to Edo.text(14.5f)
        FoodyStil.BAYRISCH -> "Foody füllt die freien Tag mit Rezepten, die länger nimma dran warn." to Bayrisch.text(14.5f)
        FoodyStil.GRAUEN ->
            "Einige Tage liegen noch im Dunkeln. Foody schlägt vor, was länger nicht auf dem Tisch stand." to Grauen.text(16f, kursiv = true)
        FoodyStil.KREUZER ->
            "Der Navigationscomputer füllt offene Sprünge mit Systemen, die lange nicht angeflogen wurden." to Kreuzer.titel(14.5f, FontWeight.Normal, 0f)
        FoodyStil.MILCHGLAS, FoodyStil.SAGE -> null
    }
}

/**
 * Ein Vorschlag in R–V: Tag des Stils links, Rezept, rechts „↻“. Antippen wählt ihn ab und wieder an (TalkBack:
 * Kontrollkästchen „DI 27.10. übernehmen“); ohne Vorschlag ist die Zeile gesperrt. [nr] färbt das Anime-Kästchen.
 */
@Composable
internal fun VorschlagStil(
    tag: LocalDate,
    tagText: String,
    name: String?,
    gewaehlt: Boolean,
    nr: Int,
    onUmschalten: () -> Unit,
    onNeu: () -> Unit,
) {
    val stil = FoodyTheme.stil.stil
    WahlZeile(
        name ?: "kein Vorschlag",
        gewaehlt = name != null && gewaehlt,
        onUmschalten,
        Modifier.semantics { contentDescription = "$tagText übernehmen" },
        haken = true,
        aktiv = name != null,
        anfang = { TagMarke(stil, planenTag(stil, tag), nr) },
        ende = {
            IconButton(onClick = onNeu) {
                val farbe = if (stil == FoodyStil.KREUZER) FoodyTheme.stil.zier else FoodyTheme.farben.text
                Icon(Icons.Outlined.Refresh, contentDescription = "Neuer Vorschlag für $tagText", tint = farbe)
            }
        },
    )
}

/** Tag eines Vorschlags: buntes Kästchen (Anime), sonst der Tag in Schrift und Farbe des Stils. TalkBack überspringt ihn. */
@Composable
private fun TagMarke(stil: FoodyStil, text: String, nr: Int) {
    val f = FoodyTheme.farben
    val m = Modifier.clearAndSetSemantics {}
    if (stil == FoodyStil.ANIME) {
        val form = RoundedCornerShape(12.dp)
        Box(
            m.size(width = 48.dp, height = 40.dp).background(ANIME_TAGE[nr % ANIME_TAGE.size], form).border(2.dp, f.text, form),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = Anime.text(13f, FontWeight.ExtraBold), color = f.text)
        }
        return
    }
    val (schrift, farbe) = when (stil) {
        FoodyStil.EDO -> Edo.titel(18f) to edoShu()
        FoodyStil.BAYRISCH -> Bayrisch.titel(16f) to bayrischBlau()
        FoodyStil.GRAUEN -> Grauen.titel(17f) to FoodyTheme.stil.zier
        else -> Kreuzer.mono(13f) to kreuzerAmber()
    }
    Text(text, m.widthIn(min = 36.dp), style = schrift, color = farbe, textAlign = TextAlign.Center)
}

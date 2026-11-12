package de.foody.feature.woche.stil

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.data.woche.PlanEintrag
import de.foody.core.data.woche.WochenAnsicht
import de.foody.core.data.woche.WochenTag
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.ganzzahlDeutsch
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.anime.AnimeKnopf
import de.foody.core.designsystem.stil.bayrisch.BayrischKnopf
import de.foody.core.designsystem.stil.edo.EdoKnopf
import de.foody.core.designsystem.stil.grauen.GrauenKnopf
import de.foody.core.designsystem.stil.kreuzer.KreuzerKnopf
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.feature.woche.mahlzeitAnzeige
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import java.time.format.TextStyle as DatumsStil

/**
 * Gemeinsames der Wochen-Szenen der Stile R–V in Immersiv: Welche Szene gilt, der gewählte Tag (übersteht das
 * Drehen), der Rahmen mit Scrollen, Tage für TalkBack und die Texte. Jede Szene zeichnet Kopf, Tagwahl und Tag in
 * ihrer Art; die Blätter (Mahlzeit, Wahl, Füllen) liegen weiter in der Route.
 */
internal object WocheSzene {
    /** Der Stil R–V, dessen Szene die Woche zeigt; `null` = keiner (Milchglas, Ruhig, Sage hat ihre eigene). */
    @Composable
    fun aktuell(): FoodyStil? {
        val s = FoodyTheme.stil
        if (s.variante != StilVariante.IMMERSIV) return null
        return s.stil.takeIf { it != FoodyStil.MILCHGLAS && it != FoodyStil.SAGE }
    }
}

/** Die Szene des Stils [stil] (R–V). */
@Composable
internal fun WocheStil(stil: FoodyStil, woche: WochenAnsicht?, heute: LocalDate, zeigtHeute: Boolean, aktionen: WocheSageAktionen, unten: Dp) {
    val w = WocheDaten(woche, heute, zeigtHeute)
    when (stil) {
        FoodyStil.ANIME -> WocheAnime(w, aktionen, unten)
        FoodyStil.EDO -> WocheEdo(w, aktionen, unten)
        FoodyStil.BAYRISCH -> WocheBayrisch(w, aktionen, unten)
        FoodyStil.GRAUEN -> WocheGrauen(w, aktionen, unten)
        FoodyStil.KREUZER -> WocheKreuzer(w, aktionen, unten)
        FoodyStil.MILCHGLAS, FoodyStil.SAGE -> Unit
    }
}

/** Was jede Szene braucht: Woche, heute und ob die Woche von heute gezeigt wird. */
internal class WocheDaten(val woche: WochenAnsicht?, val heute: LocalDate, val zeigtHeute: Boolean) {
    val tage: List<WochenTag> get() = woche?.tage.orEmpty()
    val heuteIndex: Int get() = tage.indexOfFirst { it.datum == heute }

    /** Tage mit mindestens einem Eintrag. */
    val geplant: Int get() = tage.count { it.eintraege.isNotEmpty() }

    /** „Ø 1.840 kcal am Tag“ bzw. „Ø mind. …“; `null` ohne Durchschnitt. */
    val schnitt: String? get() = woche?.durchschnitt?.let { "Ø ${if (it.vollstaendig) "" else "mind. "}${ganzzahlDeutsch(it.kcal)} kcal am Tag" }
}

/** Der gewählte Tag (Index), zu Beginn heute oder Montag; je Woche neu, übersteht das Drehen. */
@Composable
internal fun rememberGewaehlt(w: WocheDaten): MutableIntState =
    rememberSaveable(w.woche?.montag) { mutableIntStateOf(w.heuteIndex.coerceAtLeast(0)) }

/** Scrollender Rahmen, so breit wie der Inhalt sonst, unten Platz für die Leiste. */
@Composable
internal fun WocheRahmen(unten: Dp, abstand: Dp = 12.dp, inhalt: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = unten),
            verticalArrangement = Arrangement.spacedBy(abstand),
            content = inhalt,
        )
    }
}

/** Ein Tag in der Tagwahl: Tab, gewählt, TalkBack hört „Samstag, 24.10., heute: Ofengemüse“. */
internal fun Modifier.tagWahl(t: WochenTag, heute: Boolean, an: Boolean, onKlick: () -> Unit): Modifier =
    clickable(role = Role.Tab, onClick = onKlick).semantics(mergeDescendants = true) {
        selected = an
        contentDescription = tagBeschreibung(t, heute)
    }

internal fun tagBeschreibung(t: WochenTag, heute: Boolean): String =
    tagLangText(t.datum) + (if (heute) ", heute" else "") + ": " +
        (if (t.eintraege.isEmpty()) "nichts geplant" else t.eintraege.joinToString(", ") { it.rezeptName })

/** „Samstag, 24.10.“ */
internal fun tagLangText(d: LocalDate) = "${d.dayOfWeek.getDisplayName(DatumsStil.FULL, Locale.GERMAN)}, ${d.dayOfMonth}.${d.monthValue}."

/** „SAMSTAG“ / „Samstag“ */
internal fun wochentagLang(d: LocalDate): String = d.dayOfWeek.getDisplayName(DatumsStil.FULL, Locale.GERMAN)

/** „KW 43 · 19.–25. Oktober“, über zwei Monate „KW 44 · 26. Oktober – 1. November“. */
internal fun kwText(montag: LocalDate): String {
    val sonntag = montag.plusDays(6)
    val kw = montag.get(WeekFields.ISO.weekOfWeekBasedYear())
    val monat = { d: LocalDate -> d.month.getDisplayName(DatumsStil.FULL, Locale.GERMAN) }
    val zeitraum = if (montag.month == sonntag.month) {
        "${montag.dayOfMonth}.–${sonntag.dayOfMonth}. ${monat(sonntag)}"
    } else {
        "${montag.dayOfMonth}. ${monat(montag)} – ${sonntag.dayOfMonth}. ${monat(sonntag)}"
    }
    return "KW $kw · $zeitraum"
}

/** „1.020 kcal“ bzw. „mind. 1.020 kcal“; `null`, wenn nichts geplant ist oder Werte fehlen ganz. */
internal fun tagKcal(t: WochenTag): String? = t.naehrwerte?.takeIf { t.eintraege.isNotEmpty() }?.let {
    (if (it.complete) "" else "mind. ") + "${ganzzahlDeutsch(it.kcal)} kcal"
}

/** Portionen oder „✓ gekocht“ eines Eintrags. */
internal fun eintragStand(e: PlanEintrag): String = if (e.gekocht) "✓ gekocht" else "${e.portionen} P."

/** Knopf des Stils mit eigener Beschreibung für TalkBack (‹ › und kurze Wörter). */
@Composable
internal fun WocheKnopf(text: String, onKlick: () -> Unit, modifier: Modifier = Modifier, beschreibung: String? = null, voll: Boolean = false) {
    when (FoodyTheme.stil.stil) {
        FoodyStil.ANIME -> AnimeKnopf(text, onKlick, modifier, beschreibung, voll = voll)
        FoodyStil.EDO -> EdoKnopf(text, onKlick, modifier, voll = voll, beschreibung = beschreibung)
        FoodyStil.BAYRISCH -> BayrischKnopf(text, onKlick, modifier, voll = voll, beschreibung = beschreibung)
        FoodyStil.GRAUEN -> GrauenKnopf(text, onKlick, modifier, beschreibung, umriss = !voll)
        else -> KreuzerKnopf(text, onKlick, modifier, voll = voll, beschreibung = beschreibung)
    }
}

/** Blättern wie bei der Sage: ‹, der Durchschnitt, „Heute“ (nur in anderen Wochen) und ›. */
@Composable
internal fun Blaettern(w: WocheDaten, aktionen: WocheSageAktionen, schrift: TextStyle, heuteText: String = "Heute") {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        WocheKnopf("‹", { aktionen.onBlaettern(-1) }, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), beschreibung = "Vorige Woche")
        Text(w.schnitt ?: "", Modifier.weight(1f), style = schrift, color = FoodyTheme.farben.nebentext)
        if (!w.zeigtHeute) WocheKnopf(heuteText, aktionen.onHeute, beschreibung = "Heute")
        WocheKnopf("›", { aktionen.onBlaettern(1) }, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), beschreibung = "Nächste Woche")
    }
}

/** Ein Eintrag des gewählten Tages: Mahlzeit, Gericht und Stand; Antippen öffnet das Mahlzeit-Blatt. */
@Composable
internal fun Eintragszeile(
    e: PlanEintrag,
    onKlick: () -> Unit,
    oben: TextStyle,
    gericht: TextStyle,
    stand: TextStyle,
    modifier: Modifier = Modifier,
    obenFarbe: Color = FoodyTheme.farben.nebentext,
    farbe: Color = FoodyTheme.farben.text,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onKlick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(mahlzeitAnzeige(e.mahlzeit), style = oben, color = obenFarbe)
            Text(e.rezeptName, style = gericht, color = farbe)
        }
        Text(eintragStand(e), style = stand, color = farbe)
    }
}

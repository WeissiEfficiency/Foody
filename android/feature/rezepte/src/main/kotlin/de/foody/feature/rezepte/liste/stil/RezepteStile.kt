package de.foody.feature.rezepte.liste.stil

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilAktion
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.stil.szenenStil
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.feature.rezepte.liste.FilterChips
import de.foody.feature.rezepte.liste.LEER_TEXT
import de.foody.feature.rezepte.liste.ListeFilter
import de.foody.feature.rezepte.liste.ListeZustand
import de.foody.feature.rezepte.liste.Suche

/**
 * Gemeinsames der Rezeptlisten-Szenen der Stile R–V in Immersiv: welche Szene gilt, Kopf, Suche und dieselben
 * Filter wie die normale Liste, die Treffer in Seiten zu [SEITE] (‹ Seite 1 / 3 ›), das gewählte Rezept (übersteht
 * das Drehen, beginnt bei jeder neuen Suche vorn) und die leeren Zustände. Jede Szene zeichnet ihren Kopf, die Wahl
 * (Fächer, Herzen, Regal, Sternkarte) und die Karte des gewählten Rezepts in ihrer Art. Anime und die Bibliothek des
 * Grauens (Immersiv) zeigen statt Seiten alle Treffer in einer Liste, die nur zeichnet, was gerade im Bild ist
 * ([EndlosListe]): Anime als Comic-Karten zu zweit, das Grauen als Bretter mit Bänden.
 */
internal object RezepteSzene {
    /** Der Stil R–V, dessen Szene die Rezeptliste zeigt; `null` = keiner (Milchglas, Ruhig, Sage hat ihre eigene). */
    @Composable
    fun aktuell(): FoodyStil? = szenenStil()
}

/** So viele Rezepte zeigt eine Seite der Wahl. */
internal const val SEITE = 6

/**
 * Die Szene des Stils [stil] (R–V) mit derselben Liste, denselben Filtern und Zielen wie die normale Liste. Steht das
 * Detail daneben ([nebenDetail], Tablet), öffnet schon die Wahl das Rezept rechts; sonst blieb dort „Wähle ein
 * Rezept“ stehen, bis man unter der Wahl „Öffnen“ fand (Gerätetest Tablet 2026-11-17). [offen] ist das rechts offene.
 */
@Composable
internal fun RezepteStil(
    stil: FoodyStil,
    z: ListeZustand,
    aendern: ((ListeFilter) -> ListeFilter) -> Unit,
    zuruecksetzen: () -> Unit,
    onOeffnen: (String) -> Unit,
    onNeu: () -> Unit,
    offen: String? = null,
    nebenDetail: Boolean = false,
) {
    if (stil == FoodyStil.ANIME) {
        EndlosListe(stil, z, aendern, zuruecksetzen, onNeu, 2, Arrangement.spacedBy(14.dp)) { paar, i, m -> AnimeReihe(paar, i, onOeffnen, m) }
        return
    }
    if (stil == FoodyStil.GRAUEN) {
        // Auf dem Handy kommt der Band aus dem Regal und schlägt sich auf; neben dem Detail (Tablet) öffnet er sofort
        val u = rememberRegalUebergang()
        var wurzel by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().onGloballyPositioned { wurzel = it.positionInRoot() }) {
            EndlosListe(stil, z, aendern, zuruecksetzen, onNeu, JE_BRETT, Arrangement.spacedBy(22.dp)) { baende, i, m ->
                GrauenBrett(
                    baende, i, offen,
                    onOeffnen = { r, lage, farbe -> if (nebenDetail) onOeffnen(r.id) else u.oeffnen(r.id, r.name, lage, farbe) { onOeffnen(r.id) } },
                    modifier = m,
                    fliegt = { u.band.takeIf { u.zug.value > 0f } },
                )
            }
            if (!nebenDetail) RegalEbene(u, { wurzel }, Modifier.matchParentSize())
        }
        return
    }
    // Seite und Wahl beginnen bei jeder neuen Suche oder jedem Filter wieder vorn
    var seite by rememberSaveable(z.filter) { mutableIntStateOf(0) }
    var gewaehlt by rememberSaveable(z.filter) { mutableStateOf<String?>(null) }
    val n = z.treffer.size
    val seiten = (n + SEITE - 1) / SEITE
    val s = if (seiten == 0) 0 else seite.coerceIn(0, seiten - 1)
    val aufSeite = z.treffer.drop(s * SEITE).take(SEITE)
    val r = aufSeite.firstOrNull { it.id == gewaehlt } ?: aufSeite.firstOrNull { it.id == offen } ?: aufSeite.firstOrNull()
    // Antippen wählt nur; die Karte mit „Öffnen“ liegt auf dem Handy oft unter der schwebenden Leiste. Nach der Wahl
    // rollt sie deshalb samt Platz für die Leiste ins Bild (Gerätetest 2026-11-16).
    val karte = remember { BringIntoViewRequester() }
    var kartenGroesse by remember { mutableStateOf(IntSize.Zero) }
    val leiste = with(LocalDensity.current) { PLATZ_FUER_LEISTE.toPx() }
    LaunchedEffect(gewaehlt) {
        if (gewaehlt != null && kartenGroesse != IntSize.Zero) {
            karte.bringIntoView(Rect(0f, 0f, kartenGroesse.width.toFloat(), kartenGroesse.height + leiste))
        }
    }
    Box(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().padding(top = 16.dp, bottom = ueberLeiste(PLATZ_FUER_LEISTE)),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // FilterChips bringen ihren Rand selbst mit, alles andere bekommt ihn hier
            val rand = Modifier.padding(horizontal = 16.dp)
            Oben(stil, z, aendern, zuruecksetzen, onNeu, rand)
            if (r == null) return@Column
            val waehlen = { id: String ->
                gewaehlt = id
                if (nebenDetail) onOeffnen(id)
            }
            Box(rand) {
                when (stil) {
                    FoodyStil.EDO -> EdoFaecher(aufSeite, r.id, waehlen)
                    FoodyStil.BAYRISCH -> BayrischHerzen(aufSeite, r.id, waehlen)
                    else -> KreuzerKarte(aufSeite, r.id, waehlen)
                }
            }
            if (seiten > 1) {
                Seiten(s, seiten, schrift(stil), rand) { neueSeite ->
                    seite = neueSeite
                    gewaehlt = null
                }
            }
            Box(rand.bringIntoViewRequester(karte).onSizeChanged { kartenGroesse = it }) {
                when (stil) {
                    FoodyStil.EDO -> EdoBlatt(r, onOeffnen)
                    FoodyStil.BAYRISCH -> BayrischKarte(r, onOeffnen)
                    else -> KreuzerDaten(r, onOeffnen)
                }
            }
        }
    }
}

/**
 * Kopf, Suche und Filter, darunter alle Treffer in Reihen zu [jeReihe], die [reihe] zeichnet (Reihe, Nummer, Modifier
 * mit Breite und Rand). Endlos statt Seiten; die Liste zeichnet nur die Reihen im Bild, und eine neue Suche oder ein
 * Filter beginnt wieder oben.
 */
@Composable
private fun EndlosListe(
    stil: FoodyStil,
    z: ListeZustand,
    aendern: ((ListeFilter) -> ListeFilter) -> Unit,
    zuruecksetzen: () -> Unit,
    onNeu: () -> Unit,
    jeReihe: Int,
    abstand: Arrangement.Vertical,
    reihe: @Composable (List<RezeptKarte>, Int, Modifier) -> Unit,
) {
    val reihen = remember(z.treffer, jeReihe) { z.treffer.chunked(jeReihe) }
    val zustand = rememberSaveable(z.filter, saver = LazyListState.Saver) { LazyListState() }
    val breite = Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth()
    val rand = Modifier.padding(horizontal = 16.dp)
    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding(),
        state = zustand,
        contentPadding = PaddingValues(top = 16.dp, bottom = ueberLeiste(PLATZ_FUER_LEISTE)),
        verticalArrangement = abstand,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "oben") {
            Column(breite, verticalArrangement = Arrangement.spacedBy(14.dp)) { Oben(stil, z, aendern, zuruecksetzen, onNeu, rand) }
        }
        itemsIndexed(reihen, key = { _, r -> r.first().id }) { i, r -> reihe(r, i, breite.then(rand)) }
    }
}

/** Kopf mit „+ Neu“, Suche mit Sortierknopf, Filter und die leeren Zustände; [rand] für alles außer den Filtern. */
@Composable
private fun Oben(
    stil: FoodyStil,
    z: ListeZustand,
    aendern: ((ListeFilter) -> ListeFilter) -> Unit,
    zuruecksetzen: () -> Unit,
    onNeu: () -> Unit,
    rand: Modifier,
) {
    val n = z.treffer.size
    // Im Grauen ohne „+“: das Siegel trägt schon das Zeichen
    val neuText = when (stil) {
        FoodyStil.EDO -> "＋ Neu"
        FoodyStil.GRAUEN -> "Neu"
        else -> "+ Neu"
    }
    val neu = @Composable { StilAktion(neuText, onNeu, beschreibung = "Neues Rezept", voll = true) }
    Box(rand) {
        when (stil) {
            FoodyStil.ANIME -> AnimeKopf(n, neu)
            FoodyStil.EDO -> EdoKopf(n, neu)
            FoodyStil.BAYRISCH -> BayrischKopf(n, neu)
            FoodyStil.GRAUEN -> GrauenKopf(n, neu)
            else -> KreuzerKopf(n, neu)
        }
    }
    if (z.geladen && z.keineRezepte) {
        LeererZustandFlaeche(Icons.AutoMirrored.Outlined.MenuBook, "Noch keine Rezepte", LEER_TEXT, aktionText = "Anlegen", onAktion = onNeu, modifier = rand)
        return
    }
    Suche(z.filter, aendern, suchText(stil, FoodyTheme.stil.inszeniert), rand)
    FilterChips(z.filter, aendern)
    if (z.geladen && z.treffer.isEmpty()) {
        LeererZustandFlaeche(
            Icons.AutoMirrored.Outlined.MenuBook, "Nichts gefunden", "Kein Rezept passt zu Suche und Filtern.",
            aktionText = "Filter zurücksetzen", onAktion = zuruecksetzen, modifier = rand,
        )
    }
}

/** Platz unter dem Inhalt für die schwebende Navigationsleiste. */
private val PLATZ_FUER_LEISTE = 104.dp

/** Platzhalter der Suche wie im Entwurf. */
private fun suchText(stil: FoodyStil, inszeniert: Boolean) = when (stil) {
    FoodyStil.GRAUEN if !inszeniert -> "Rezept oder Zutat suchen"
    FoodyStil.GRAUEN -> "In den Bänden suchen"
    FoodyStil.KREUZER if !inszeniert -> "Rezept oder Zutat suchen"
    FoodyStil.KREUZER -> "System suchen"
    FoodyStil.BAYRISCH -> "Rezept oder Zutat suacha"
    else -> "Rezept oder Zutat suchen"
}

/** Kleine Schrift des Stils für Seitenzahl und Nebenzeilen. */
@Composable
internal fun schrift(stil: FoodyStil): TextStyle = when (stil) {
    FoodyStil.ANIME -> Anime.text(14f)
    FoodyStil.EDO -> Edo.kopf(12.5f)
    FoodyStil.BAYRISCH -> Bayrisch.text(15f)
    FoodyStil.GRAUEN -> Grauen.text(15f, kursiv = true)
    else -> Kreuzer.mono(12.5f)
}

/** ‹ Seite 1 / 3 ›; die Pfeile sind mindestens 48 dp groß. */
@Composable
private fun Seiten(seite: Int, seiten: Int, schrift: TextStyle, modifier: Modifier, onSeite: (Int) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StilAktion("‹", { onSeite((seite + seiten - 1) % seiten) }, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), beschreibung = "Vorige Seite")
        Text(
            "Seite ${seite + 1} / $seiten", Modifier.weight(1f).semantics { contentDescription = "Seite ${seite + 1} von $seiten" },
            style = schrift, color = FoodyTheme.farben.nebentext, textAlign = TextAlign.Center,
        )
        StilAktion("›", { onSeite((seite + 1) % seiten) }, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), beschreibung = "Nächste Seite")
    }
}

/** Ein Rezept in der Wahl: Tab, gewählt; TalkBack hört „Reispfanne, 30 Min., 717 kcal, Favorit“. */
internal fun Modifier.rezeptWahl(r: RezeptKarte, an: Boolean, onKlick: () -> Unit): Modifier =
    clickable(role = Role.Tab, onClick = onKlick).semantics(mergeDescendants = true) {
        selected = an
        contentDescription = rezeptBeschreibung(r)
    }

/** „Reispfanne, 30 Min., 717 kcal, Favorit“ – nur, was bekannt ist. */
internal fun rezeptBeschreibung(r: RezeptKarte): String =
    listOfNotNull(r.name, r.zeitMinuten?.let { "$it Min." }, r.kcalJePortion?.let { "$it kcal" }, if (r.favorit) "Favorit" else null)
        .joinToString(", ")

/** „40 Min. · 553 kcal“ – nur, was bekannt ist; `null` ohne Werte. */
internal fun zeitUndKcal(r: RezeptKarte, trenner: String = " · "): String? =
    listOfNotNull(r.zeitMinuten?.let { "$it Min." }, r.kcalJePortion?.let { "$it kcal" }).joinToString(trenner).takeIf { it.isNotEmpty() }

/** Der Anfangsbuchstabe des Namens, groß; „?“ ohne Namen. */
internal fun anfang(r: RezeptKarte): String = r.name.trim().firstOrNull()?.uppercase() ?: "?"

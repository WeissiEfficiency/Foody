package de.foody.feature.rezepte.liste.stil

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foody.core.data.rezepte.RezeptKarte
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.Suchfeld
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilAktion
import de.foody.core.designsystem.stil.StilVariante
import de.foody.core.designsystem.stil.anime.Anime
import de.foody.core.designsystem.stil.bayrisch.Bayrisch
import de.foody.core.designsystem.stil.edo.Edo
import de.foody.core.designsystem.stil.grauen.Grauen
import de.foody.core.designsystem.stil.kreuzer.Kreuzer
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.feature.rezepte.liste.FilterChips
import de.foody.feature.rezepte.liste.ListeFilter
import de.foody.feature.rezepte.liste.ListeZustand

/**
 * Gemeinsames der Rezeptlisten-Szenen der Stile R–V in Immersiv: welche Szene gilt, Kopf, Suche und dieselben
 * Filter wie die normale Liste, die Treffer in Seiten zu [SEITE] (‹ Seite 1 / 3 ›), das gewählte Rezept (übersteht
 * das Drehen, beginnt bei jeder neuen Suche vorn) und die leeren Zustände. Jede Szene zeichnet ihren Kopf, die Wahl
 * (Raster, Fächer, Herzen, Regal, Sternkarte) und die Karte des gewählten Rezepts in ihrer Art.
 */
internal object RezepteSzene {
    /** Der Stil R–V, dessen Szene die Rezeptliste zeigt; `null` = keiner (Milchglas, Ruhig, Sage hat ihre eigene). */
    @Composable
    fun aktuell(): FoodyStil? {
        val s = FoodyTheme.stil
        if (s.variante != StilVariante.IMMERSIV) return null
        return s.stil.takeIf { it != FoodyStil.MILCHGLAS && it != FoodyStil.SAGE }
    }
}

/** So viele Rezepte zeigt eine Seite der Wahl. */
internal const val SEITE = 6

/** Die Szene des Stils [stil] (R–V) mit derselben Liste, denselben Filtern und Zielen wie die normale Liste. */
@Composable
internal fun RezepteStil(
    stil: FoodyStil,
    z: ListeZustand,
    aendern: ((ListeFilter) -> ListeFilter) -> Unit,
    zuruecksetzen: () -> Unit,
    onOeffnen: (String) -> Unit,
    onNeu: () -> Unit,
    onImport: () -> Unit,
) {
    // Seite und Wahl beginnen bei jeder neuen Suche oder jedem Filter wieder vorn
    var seite by rememberSaveable(z.filter) { mutableIntStateOf(0) }
    var gewaehlt by rememberSaveable(z.filter) { mutableStateOf<String?>(null) }
    val n = z.treffer.size
    val seiten = (n + SEITE - 1) / SEITE
    val s = if (seiten == 0) 0 else seite.coerceIn(0, seiten - 1)
    val aufSeite = z.treffer.drop(s * SEITE).take(SEITE)
    val r = aufSeite.firstOrNull { it.id == gewaehlt } ?: aufSeite.firstOrNull()
    val neu = @Composable { NeuMenue(stil, onNeu, onImport) }
    Box(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth().padding(top = 16.dp, bottom = ueberLeiste(104.dp)),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // FilterChips bringen ihren Rand selbst mit, alles andere bekommt ihn hier
            val rand = Modifier.padding(horizontal = 16.dp)
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
                LeererZustandFlaeche(
                    Icons.AutoMirrored.Outlined.MenuBook, "Noch keine Rezepte", "Lege ein Rezept an oder importiere Markdown-Dateien.",
                    aktionText = "Anlegen", onAktion = onNeu, modifier = rand,
                )
                return@Column
            }
            Suchfeld(z.filter.suche, { text -> aendern { it.copy(suche = text) } }, suchText(stil), rand)
            FilterChips(z.filter, z.tags, aendern)
            if (r == null) {
                if (z.geladen) {
                    LeererZustandFlaeche(
                        Icons.AutoMirrored.Outlined.MenuBook, "Nichts gefunden", "Kein Rezept passt zu Suche und Filtern.",
                        aktionText = "Filter zurücksetzen", onAktion = zuruecksetzen, modifier = rand,
                    )
                }
                return@Column
            }
            val waehlen = { id: String -> gewaehlt = id }
            Box(rand) {
                when (stil) {
                    FoodyStil.ANIME -> AnimeRaster(aufSeite, onOeffnen)
                    FoodyStil.EDO -> EdoFaecher(aufSeite, r.id, waehlen)
                    FoodyStil.BAYRISCH -> BayrischHerzen(aufSeite, r.id, waehlen)
                    FoodyStil.GRAUEN -> GrauenRegal(aufSeite, r.id, waehlen)
                    else -> KreuzerKarte(aufSeite, r.id, waehlen)
                }
            }
            if (seiten > 1) {
                Seiten(s, seiten, schrift(stil), rand) { neueSeite ->
                    seite = neueSeite
                    gewaehlt = null
                }
            }
            // Anime öffnet direkt aus dem Raster; die anderen zeigen das gewählte Rezept als Karte
            if (stil != FoodyStil.ANIME) {
                Box(rand) {
                    when (stil) {
                        FoodyStil.EDO -> EdoBlatt(r, onOeffnen)
                        FoodyStil.BAYRISCH -> BayrischKarte(r, onOeffnen)
                        FoodyStil.GRAUEN -> GrauenSeite(r, onOeffnen)
                        else -> KreuzerDaten(r, onOeffnen)
                    }
                }
            }
        }
    }
}

/** Platzhalter der Suche wie im Entwurf. */
private fun suchText(stil: FoodyStil) = when (stil) {
    FoodyStil.GRAUEN -> "In den Bänden suchen"
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

/** „+ Neu“ im Knopf des Stils; öffnet „Neues Rezept“ oder „Markdown importieren“. */
@Composable
private fun NeuMenue(stil: FoodyStil, onNeu: () -> Unit, onImport: () -> Unit) {
    var menue by remember { mutableStateOf(false) }
    Box {
        StilAktion(if (stil == FoodyStil.EDO) "＋ Neu" else "+ Neu", { menue = true }, beschreibung = "Rezept hinzufügen", voll = true)
        DropdownMenu(menue, onDismissRequest = { menue = false }) {
            DropdownMenuItem(text = { Text("Neues Rezept") }, onClick = { menue = false; onNeu() })
            DropdownMenuItem(text = { Text("Markdown importieren") }, onClick = { menue = false; onImport() })
        }
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

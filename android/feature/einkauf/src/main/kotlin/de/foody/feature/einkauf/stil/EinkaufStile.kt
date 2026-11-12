package de.foody.feature.einkauf.stil

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import de.foody.core.data.einkauf.EinkaufsAnsicht
import de.foody.core.data.einkauf.EinkaufsArtikel
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.StilVariante

/**
 * Gemeinsames der Einkaufs-Szenen in Immersiv (Sage und R–V): Welche Szene gilt, die Posten nach Abteilung und das
 * Verhalten eines Postens (Antippen hakt ab und wieder auf, Langdruck öffnet die Details, TalkBack hört Name, Menge
 * und „gekauft“ / „auf der Liste“).
 */
internal object EinkaufSzene {
    /** Der Stil, dessen Szene der Einkauf zeigt; `null` = die normale Ansicht (Milchglas, Ruhig). */
    @Composable
    fun aktuell(): FoodyStil? {
        val s = FoodyTheme.stil
        return s.stil.takeIf { s.variante == StilVariante.IMMERSIV && it != FoodyStil.MILCHGLAS }
    }
}

/** Eine Abteilung mit ihren Posten: erst die offenen, dann die schon gekauften. */
internal data class PostenGruppe(val name: String, val artikel: List<EinkaufsArtikel>)

/**
 * Die Posten nach Abteilung in Laufreihenfolge: die offenen Abteilungen wie in [EinkaufsAnsicht.offen], gekaufte
 * Posten hinten in ihrer Abteilung; ganz gekaufte Abteilungen folgen am Ende.
 */
internal fun EinkaufsAnsicht?.gruppiert(): List<PostenGruppe> {
    if (this == null) return emptyList()
    val gekauft = alle.filter { it.gekauft }.groupBy { it.abteilung }
    val offeneNamen = offen.map { it.name }.toSet()
    val vorne = offen.map { PostenGruppe(it.name, it.artikel + gekauft[it.name].orEmpty()) }
    val hinten = gekauft.filterKeys { it !in offeneNamen }.map { (name, artikel) -> PostenGruppe(name, artikel) }
    return vorne + hinten
}

/** Gekaufte / alle Posten. */
internal val EinkaufsAnsicht?.erledigt: Int get() = this?.alle?.count { it.gekauft } ?: 0
internal val EinkaufsAnsicht?.gesamt: Int get() = this?.alle?.size ?: 0

/** Ein Posten der Szene: Antippen hakt ab (und wieder auf), Langdruck öffnet die Details. */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.posten(a: EinkaufsArtikel, onUmschalten: (String, Boolean) -> Unit, onDetails: (String) -> Unit): Modifier =
    combinedClickable(
        role = Role.Checkbox,
        onClick = { onUmschalten(a.id, !a.gekauft) },
        onLongClick = { onDetails(a.id) },
        onLongClickLabel = "Details",
    ).semantics(mergeDescendants = true) {
        contentDescription = listOfNotNull(a.name, a.menge, a.notiz).joinToString(", ")
        stateDescription = if (a.gekauft) "gekauft" else "auf der Liste"
        onLongClick("Details") { onDetails(a.id); true }
    }

/** Ein Eintrag über die ganze Breite des Rasters. */
internal fun LazyGridScope.ganzeBreite(key: String, inhalt: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { inhalt() }

/** Jede Abteilung als ein Eintrag über die ganze Breite. */
internal fun LazyGridScope.jeGruppe(praefix: String, gruppen: List<PostenGruppe>, inhalt: @Composable (Int, PostenGruppe) -> Unit) {
    gruppen.forEachIndexed { i, g -> ganzeBreite("$praefix-${g.name}") { inhalt(i, g) } }
}

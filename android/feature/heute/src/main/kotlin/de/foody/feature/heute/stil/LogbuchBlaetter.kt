package de.foody.feature.heute.stil

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.foody.core.designsystem.animationenErlaubt
import de.foody.core.designsystem.komponenten.roemischVoll
import de.foody.core.designsystem.stil.grauen.GrauenSeite
import de.foody.core.designsystem.stil.grauen.umblaettern
import java.time.LocalDate
import kotlin.math.abs

/*
 * Das Logbuch des Kosmischen Grauens als Buch (Runde 2, Buch ausbauen): Jeder Tag ist ein Blatt mit dem Tag des Jahres
 * als Seitenzahl. Wischen blättert wie im Rezept um (nach links: der nächste Tag), „‹“ und „›“ blättern mit. Das
 * kommende Blatt ist beim Wischen noch leer; seine Einträge erscheinen, sobald es aufgeschlagen liegt.
 */

/** Erster Tag des Buchs; die Seiten reichen von hier rund 137 Jahre weit. */
private val ERSTER_TAG: LocalDate = LocalDate.of(2000, 1, 1)
private const val SEITEN = 50_000

/** Seite des Pagers für [datum]. */
internal fun logbuchSeite(datum: LocalDate): Int = (datum.toEpochDay() - ERSTER_TAG.toEpochDay()).toInt().coerceIn(0, SEITEN - 1)

/** Tag auf Seite [seite]. */
internal fun logbuchTag(seite: Int): LocalDate = ERSTER_TAG.plusDays(seite.toLong())

/**
 * Die Blätter des Logbuchs um [datum]; [inhalt] füllt das Blatt eines Tages. Hat der Nutzer zu einem anderen Tag
 * geblättert, meldet [onDatum] ihn; ändert sich [datum] von außen (Pfeile, Datumswahl), blättert das Buch mit – zum
 * Nachbartag sichtbar, weiter entfernt sofort.
 */
@Composable
internal fun LogbuchBlaetter(datum: LocalDate, onDatum: (LocalDate) -> Unit, inhalt: @Composable ColumnScope.(LocalDate) -> Unit) {
    val pager = rememberPagerState(initialPage = logbuchSeite(datum)) { SEITEN }
    val bewegt = animationenErlaubt()
    val gezeigt by rememberUpdatedState(datum)
    val melden by rememberUpdatedState(onDatum)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { s ->
            val tag = logbuchTag(s)
            if (tag != gezeigt) melden(tag)
        }
    }
    LaunchedEffect(datum) {
        val ziel = logbuchSeite(datum)
        if (pager.currentPage == ziel || pager.isScrollInProgress) return@LaunchedEffect
        if (bewegt && abs(ziel - pager.currentPage) == 1) pager.animateScrollToPage(ziel) else pager.scrollToPage(ziel)
    }
    HorizontalPager(pager, Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { s ->
        val tag = logbuchTag(s)
        GrauenSeite(kapitel = null, seite = roemischVoll(tag.dayOfYear), modifier = Modifier.umblaettern(pager, s), eselsohr = true) { inhalt(tag) }
    }
}

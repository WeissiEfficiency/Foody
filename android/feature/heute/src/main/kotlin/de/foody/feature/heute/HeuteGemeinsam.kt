package de.foody.feature.heute

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.foody.core.data.heute.GeplanteMahlzeit
import de.foody.core.designsystem.komponenten.FoodyFrage
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche
import de.foody.core.designsystem.komponenten.ueberLeiste
import de.foody.core.designsystem.token.FoodyBreiten
import de.foody.domain.Mahlzeit

/** Höhe der schwebenden Leiste samt Abstand, damit der letzte Bereich nicht darunter liegt. */
internal val PLATZ_FUER_LEISTE = 104.dp

/**
 * Rahmen der Stil-Szenen auf „Heute“: scrollt, mittig bis zur Inhaltsbreite, seitlich frei von Kamera und Gesten,
 * oben (außer [statusLeiste] = false) unter der Statusleiste und unten mit Platz für die Leiste. [inhalt] bekommt den
 * Modifier für seine Spalte oder Zeile; [innen] ist der Rand des Stils.
 */
@Composable
internal fun HeuteRahmen(
    modifier: Modifier = Modifier,
    statusLeiste: Boolean = true,
    innen: PaddingValues = PaddingValues(0.dp),
    inhalt: @Composable (Modifier) -> Unit,
) {
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        inhalt(
            Modifier.widthIn(max = FoodyBreiten.inhalt).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .then(if (statusLeiste) Modifier.statusBarsPadding() else Modifier)
                .padding(innen)
                .padding(bottom = ueberLeiste(PLATZ_FUER_LEISTE))
                .testTag(HEUTE_INHALT),
        )
    }
}

/** Hinweis, solange die Datenbank leer ist: Foody mit dem Server verbinden, um die Rezepte zu laden. */
@Composable
internal fun DatenbankLeerHinweis(onEinstellungen: () -> Unit) {
    LeererZustandFlaeche(
        Icons.AutoMirrored.Outlined.MenuBook, "Noch keine Rezepte", "Verbinde Foody mit deinem Server, um deine Rezepte zu laden.",
        aktionText = "Server verbinden", onAktion = onEinstellungen,
    )
}

/** Rückfrage zu „Gegessen?“: Ja trägt ein, Nein blendet den Vorschlag für heute aus, Danebentippen ändert nichts. */
@Composable
internal fun GegessenFrage(mahlzeit: Mahlzeit, v: GeplanteMahlzeit, aktionen: HeuteAktionen, onZu: () -> Unit) {
    FoodyFrage(
        titel = "Gegessen?",
        text = "„${v.titel}“ als ${mahlzeit.anzeigename()} eintragen? Bei „Nein“ verschwindet der Vorschlag für heute.",
        ja = "Ja",
        onJa = { onZu(); aktionen.onGegessen(v.planEintragId) },
        onNein = { onZu(); aktionen.onNichtGegessen(v) },
        nein = "Nein",
        onAbbrechen = onZu,
    )
}

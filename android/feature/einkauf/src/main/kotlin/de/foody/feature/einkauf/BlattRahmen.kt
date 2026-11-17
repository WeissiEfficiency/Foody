package de.foody.feature.einkauf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.glas.GlasBlatt
import de.foody.core.designsystem.komponenten.BlattTitel
import de.foody.core.designsystem.komponenten.FoodyFrage
import de.foody.core.designsystem.komponenten.platzFuerLeiste
import de.foody.core.designsystem.komponenten.ueberschrift
import de.foody.core.designsystem.token.FoodyAbstaende

/** `true` auf der Listen-Seite der Stile Q–V: [BlattRahmen] zeigt dort nur Inhalt und Knopf, ohne Blatt und Titel. */
internal val LocalAufListenSeite = staticCompositionLocalOf { false }

/**
 * Blatt des Einkaufs: ganz geöffnet, Inhalt scrollbar, [knopf] fest unten (ohne Ziehen erreichbar). Auf der
 * Listen-Seite ([LocalAufListenSeite]) derselbe Inhalt unter den Reitern, der Knopf über der schwebenden Leiste.
 */
@Composable
internal fun BlattRahmen(
    titel: String,
    onSchliessen: () -> Unit,
    knopf: (@Composable () -> Unit)? = null,
    beschreibung: String? = null,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    if (LocalAufListenSeite.current) {
        Column(Modifier.fillMaxSize().padding(horizontal = FoodyAbstaende.s4)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(top = FoodyAbstaende.s3, bottom = if (knopf == null) platzFuerLeiste() + FoodyAbstaende.s3 else FoodyAbstaende.s3),
                verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
                content = inhalt,
            )
            knopf?.let { Column(Modifier.fillMaxWidth().padding(bottom = platzFuerLeiste() + FoodyAbstaende.s3)) { it() } }
        }
        return
    }
    GlasBlatt(onSchliessen, ganzOffen = true) {
        Column(Modifier.padding(horizontal = FoodyAbstaende.s6).padding(bottom = FoodyAbstaende.s6).navigationBarsPadding()) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3),
            ) {
                BlattTitel(titel, beschreibung = beschreibung)
                inhalt()
            }
            knopf?.let { Column(Modifier.fillMaxWidth().padding(top = FoodyAbstaende.s4)) { it() } }
        }
    }
}

/** „Liste löschen?“ – vor dem Löschen einer ganzen Liste samt Artikeln. */
@Composable
internal fun LoeschenFrage(name: String, onLoeschen: () -> Unit, onAbbrechen: () -> Unit) =
    FoodyFrage("Liste löschen?", "„$name“ mit allen Artikeln löschen?", "Löschen", onLoeschen, onAbbrechen)

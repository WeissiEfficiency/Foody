package de.foody.core.designsystem.komponenten

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.stil.FoodyStil
import de.foody.core.designsystem.stil.TitelArt
import de.foody.core.designsystem.stil.sage.Sage
import de.foody.core.designsystem.stil.stilTitel
import de.foody.core.designsystem.token.FoodyAbstaende

/**
 * Kopf einer Seite wie im Entwurf: optional ein runder Zurück-Knopf, darüber die [ueberzeile] in Versalien („SAMSTAG,
 * 31. OKTOBER“, „6 REZEPTE“), darunter der Titel in Fraunces; rechts runde [aktionen].
 */
@Composable
fun Seitenkopf(
    titel: String,
    modifier: Modifier = Modifier,
    ueberzeile: String? = null,
    onZurueck: (() -> Unit)? = null,
    /** Was TalkBack statt des Titels liest, wenn der Titel ein Stilwort ist („Das Lager“ → „Einstellungen“). */
    beschreibung: String? = null,
    aktionen: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
        if (onZurueck != null) RunderKnopf(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onZurueck)
        Column(Modifier.weight(1f)) {
            if (ueberzeile != null) Augenbraue(ueberzeile)
            // Düstere Sage, Immersiv: Titel in Cinzel mit Laufweite im Akzent wie die Köpfe der Szenen
            // Stile R–V, Immersiv: Titelschrift des Stils ([stilTitel])
            val sage = FoodyTheme.stil.immersiv(FoodyStil.SAGE)
            val eigen = stilTitel(if (onZurueck != null) TitelArt.SEITE_ZURUECK else TitelArt.SEITE)
            Text(
                titel,
                Modifier.ueberschrift().then(if (beschreibung != null) Modifier.semantics { contentDescription = beschreibung } else Modifier),
                style = when {
                    sage -> if (onZurueck != null) Sage.titel(20f, 0.06f) else Sage.titel(28f, 0.1f)
                    eigen != null -> eigen
                    onZurueck != null -> MaterialTheme.typography.headlineMedium
                    else -> MaterialTheme.typography.headlineLarge
                },
                color = if (sage) FoodyTheme.farben.primaer else FoodyTheme.farben.text,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalAlignment = Alignment.CenterVertically, content = aktionen)
    }
}

/**
 * Titel eines Blatts (Überschrift für TalkBack). In der Düsteren Sage (Immersiv) in Cinzel mit Laufweite im Akzent;
 * [beschreibung] liest TalkBack statt eines Stilworts.
 */
@Composable
fun BlattTitel(titel: String, modifier: Modifier = Modifier, beschreibung: String? = null) {
    val sage = FoodyTheme.stil.immersiv(FoodyStil.SAGE)
    Text(
        titel,
        modifier.ueberschrift().then(if (beschreibung != null) Modifier.semantics { contentDescription = beschreibung } else Modifier),
        style = if (sage) Sage.titel(20f, 0.1f) else stilTitel(TitelArt.BLATT) ?: MaterialTheme.typography.headlineSmall,
        color = if (sage) FoodyTheme.farben.primaer else FoodyTheme.farben.text,
    )
}

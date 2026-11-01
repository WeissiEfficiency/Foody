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
import de.foody.core.designsystem.FoodyTheme
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
    aktionen: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s3)) {
        if (onZurueck != null) RunderKnopf(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", onZurueck)
        Column(Modifier.weight(1f)) {
            if (ueberzeile != null) Augenbraue(ueberzeile)
            Text(
                titel,
                Modifier.ueberschrift(),
                style = if (onZurueck != null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                color = FoodyTheme.farben.text,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FoodyAbstaende.s2), verticalAlignment = Alignment.CenterVertically, content = aktionen)
    }
}

package de.foody.feature.woche

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import de.foody.core.designsystem.komponenten.RezeptPlatzhalter

/**
 * Bild-Slot einer Rezeptkarte: das Foto hinter [bild] (`file:`-Link aus der Fotoablage), solange es lädt oder wenn es
 * fehlt der Platzhalter; ohne Link `null` – dann zeigt die Karte ihren eigenen Platzhalter.
 */
internal fun rezeptBild(bild: String?): (@Composable () -> Unit)? = bild?.let { link ->
    {
        SubcomposeAsyncImage(
            model = link,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { RezeptPlatzhalter(Modifier.fillMaxSize(), symbolOben = true, mitSymbol = false) },
            error = { RezeptPlatzhalter(Modifier.fillMaxSize(), symbolOben = true) },
        )
    }
}

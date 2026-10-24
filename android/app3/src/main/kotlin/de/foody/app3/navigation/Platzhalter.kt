package de.foody.app3.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.LeererZustandFlaeche

/** Ziel, das erst in Phase 6 ein echter Bereich wird; zeigt die übergebenen Argumente (Tests, Sichtprüfung). */
@Composable
fun Platzhalter(titel: String, argumente: String? = null, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LeererZustandFlaeche(Icons.Outlined.Construction, titel, Platzhalter.TEXT, aktionText = null, onAktion = null)
            if (argumente != null) {
                Text(argumente, style = MaterialTheme.typography.bodyMedium, color = FoodyTheme.farben.nebentext)
            }
        }
    }
}

object Platzhalter {
    const val TEXT = "Kommt in Phase 6"
}

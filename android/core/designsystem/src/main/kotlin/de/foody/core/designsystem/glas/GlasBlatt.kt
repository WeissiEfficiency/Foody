package de.foody.core.designsystem.glas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.GlasStufe
import de.foody.core.designsystem.token.glasWerte
import kotlinx.coroutines.launch

/** Form des Blatts: oben so rund wie große Glasflächen (32 dp), unten am Rand. */
private val BlattForm = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

/**
 * Bottom Sheet im Milchglas des Entwurfs: Grund und Verlauf der Farbwelt, darüber die Tönung der Stufe DICHT, oben die
 * Lichtkante und ein Griff in Nebentextfarbe. Das Blatt liegt in einem eigenen Fenster, der Hintergrund der App ist dort
 * keine Quelle für Unschärfe. Es zeichnet deshalb dasselbe Bild selbst, so wie eine DICHT-Fläche ohne Unschärfe; die
 * Tönung allein hält AA (Kontrast-Test).
 *
 * Gezeichnet wird im Inhalt, nicht am Modifier des Blatts: Der liegt außerhalb der Verschiebung beim Hochfahren, ein
 * Zuschnitt dort schneidet den Inhalt weg. Der Griff ist deshalb eigener Inhalt, mit denselben TalkBack-Aktionen.
 *
 * [ganzOffen] öffnet gleich ganz (Blätter mit Knopf unten), sonst zuerst halb wie im Standard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlasBlatt(onSchliessen: () -> Unit, modifier: Modifier = Modifier, ganzOffen: Boolean = false, inhalt: @Composable ColumnScope.() -> Unit) {
    val farben = FoodyTheme.farben
    val verlauf = FoodyTheme.werte.verlauf
    val glas = glasWerte(GlasStufe.DICHT, FoodyTheme.dunkel, FoodyTheme.farbwelt)
    val zustand = rememberModalBottomSheetState(skipPartiallyExpanded = ganzOffen)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onSchliessen,
        modifier = modifier,
        sheetState = zustand,
        shape = BlattForm,
        containerColor = Color.Transparent,
        contentColor = farben.text,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth().drawBehind {
                drawRect(farben.grund)
                drawRect(Brush.radialGradient(listOf(verlauf.oben, Color.Transparent), Offset.Zero, size.maxDimension * 0.85f))
                drawRect(Brush.radialGradient(listOf(verlauf.unten, Color.Transparent), Offset(size.width, size.height), size.maxDimension * 0.7f))
                drawRect(glas.toenung)
                drawOutline(BlattForm.createOutline(size, layoutDirection, this), glas.kante, style = Stroke(1.dp.toPx()))
            },
        ) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 36.dp).semantics {
                    // Dieselben Aktionen wie der Griff von Material 3
                    contentDescription = "Ziehgriff"
                    dismiss("Schließen") {
                        scope.launch { zustand.hide() }.invokeOnCompletion { if (!zustand.isVisible) onSchliessen() }
                        true
                    }
                    if (zustand.currentValue == SheetValue.PartiallyExpanded) {
                        expand("Ganz öffnen") { scope.launch { zustand.expand() }; true }
                    } else if (zustand.hasPartiallyExpandedState) {
                        collapse("Verkleinern") { scope.launch { zustand.partialExpand() }; true }
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(farben.nebentext.copy(alpha = 0.5f), CircleShape))
            }
            inhalt()
        }
    }
}

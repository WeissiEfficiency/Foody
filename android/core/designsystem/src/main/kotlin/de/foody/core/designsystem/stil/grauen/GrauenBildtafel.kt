package de.foody.core.designsystem.stil.grauen

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme

/**
 * Bildtafel des Grauens statt eines Fotos (Editor, Rezept ohne Bild): ein doppelter Rahmen in Tinte wie eine Tafel im
 * Buch, Eckzier in der Zier des Stils, in der Mitte eine Kerze. Kein Farbblock; der Grund der Seite scheint durch.
 */
@Composable
fun GrauenBildtafel(modifier: Modifier = Modifier) {
    val tinte = FoodyTheme.farben.text
    val zier = FoodyTheme.stil.zier
    Box(modifier.drawBehind { bildtafel(tinte, zier) }, contentAlignment = Alignment.Center) {
        GrauenSymbol(GrauenZeichen.KERZE, tinte.copy(alpha = 0.7f), groesse = 44.dp)
    }
}

/** Doppelter Rahmen mit Eckzier über die ganze Fläche. */
fun DrawScope.bildtafel(tinte: Color, zier: Color) {
    val aussen = 4.dp.toPx()
    val innen = aussen + 5.dp.toPx()
    drawRect(tinte, Offset(aussen, aussen), Size(size.width - 2 * aussen, size.height - 2 * aussen), style = Stroke(1.4.dp.toPx()))
    drawRect(tinte.copy(alpha = 0.55f), Offset(innen, innen), Size(size.width - 2 * innen, size.height - 2 * innen), style = Stroke(0.7.dp.toPx()))
    val r = 3.dp.toPx()
    listOf(Offset(innen, innen), Offset(size.width - innen, innen), Offset(innen, size.height - innen), Offset(size.width - innen, size.height - innen))
        .forEach { ecke ->
            drawCircle(zier, r, ecke, style = Stroke(1.dp.toPx()))
            drawCircle(zier, r * 0.35f, ecke)
        }
}

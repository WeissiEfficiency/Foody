package de.foody.core.designsystem.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Zwei feste Glasstufen: LEICHT für Navigation, Kopfzeile, Status; DICHT für alles mit Fließtext. */
enum class GlasStufe { LEICHT, DICHT }

/** Tönung (mit Alpha), Unschärfe-Radius und Lichtkante einer Glasstufe. */
@Immutable
data class GlasWerte(val toenung: Color, val unschaerfe: Dp, val kante: Color)

private val CremeGlas = Color(0xFFFFFAF3)
private val GlutGlas = Color(0xFF2B201A)

fun glasWerte(stufe: GlasStufe, dunkel: Boolean): GlasWerte {
    val alpha = when (stufe) {
        GlasStufe.LEICHT -> if (dunkel) 0.50f else 0.55f
        GlasStufe.DICHT -> if (dunkel) 0.72f else 0.78f
    }
    val farben = if (dunkel) FoodyFarben.Dunkel else FoodyFarben.Hell
    return GlasWerte(
        toenung = (if (dunkel) GlutGlas else CremeGlas).copy(alpha = alpha),
        unschaerfe = 24.dp,
        kante = farben.lichtkante,
    )
}

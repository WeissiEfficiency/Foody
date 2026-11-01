package de.foody.core.designsystem.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glasstufen: LEICHT für Navigation, Kopfzeile, Status; DICHT für alles mit Fließtext; BILD für Leisten über Fotos
 * (ohne Unschärfe, fast deckend).
 */
enum class GlasStufe { LEICHT, DICHT, BILD }

/** Tönung (mit Alpha), Unschärfe-Radius, Korn (Rauschen, 0 bis 1) und Lichtkante einer Glasstufe. */
@Immutable
data class GlasWerte(val toenung: Color, val unschaerfe: Dp, val korn: Float, val kante: Color)

/**
 * Milchglas: kräftige Unschärfe mit feinem Korn, getönt in der Glasfarbe der [Farbwelt]. Das Korn ist nur Textur; die
 * Lesbarkeit hängt allein an der Tönung (Kontrast-Test ohne Unschärfe und Korn).
 */
fun glasWerte(stufe: GlasStufe, dunkel: Boolean, farbwelt: Farbwelt = Farbwelt.Standard): GlasWerte {
    val alpha = when (stufe) {
        GlasStufe.LEICHT -> if (dunkel) 0.50f else 0.55f
        GlasStufe.DICHT -> if (dunkel) 0.72f else 0.78f
        // Über Fotos (Schwarz bis Weiß möglich) fast deckend, sonst hält Text AA nicht
        GlasStufe.BILD -> 0.92f
    }
    val werte = farbwelt.werte(dunkel)
    return GlasWerte(
        toenung = werte.glas.copy(alpha = alpha),
        unschaerfe = 40.dp,
        korn = if (dunkel) 0.05f else 0.08f,
        kante = werte.farben.lichtkante,
    )
}

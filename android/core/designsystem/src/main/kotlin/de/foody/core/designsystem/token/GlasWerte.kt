package de.foody.core.designsystem.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.stil.StilWerte

/**
 * Glasstufen: LEICHT für Navigation, Kopfzeile, Status; DICHT für alles mit Fließtext; BILD für Leisten über Fotos
 * (ohne Unschärfe, fast deckend).
 */
enum class GlasStufe { LEICHT, DICHT, BILD }

/** Tönung (mit Alpha), Unschärfe-Radius, Korn (Rauschen, 0 bis 1) und Lichtkante einer Glasstufe. */
@Immutable
data class GlasWerte(val toenung: Color, val unschaerfe: Dp, val korn: Float, val kante: Color)

/**
 * Wie Flächen gezeichnet werden: [GLAS] ist das Milchglas (Unschärfe, Korn, durchscheinend), [DECKEND] die fast
 * deckenden Karten der Stile (Papier, Tafel, Panel) ohne Unschärfe.
 */
enum class Flaechenart { GLAS, DECKEND }

/**
 * Milchglas: kräftige Unschärfe mit feinem Korn, getönt in der Glasfarbe der [Farbwelt]. Das Korn ist nur Textur; die
 * Lesbarkeit hängt allein an der Tönung (Kontrast-Test ohne Unschärfe und Korn).
 */
fun glasWerte(stufe: GlasStufe, dunkel: Boolean, farbwelt: Farbwelt = Farbwelt.Standard): GlasWerte =
    glasWerte(stufe, dunkel, farbwelt.werte(dunkel), Flaechenart.GLAS)

/** Glas oder Karte eines Stils; bei Milchglas dieselben Werte wie oben. */
fun glasWerte(stufe: GlasStufe, stil: StilWerte): GlasWerte = glasWerte(stufe, stil.dunkel, stil.werte, stil.flaeche)

internal fun glasWerte(stufe: GlasStufe, dunkel: Boolean, werte: FarbweltWerte, flaeche: Flaechenart): GlasWerte {
    val alpha = when (flaeche) {
        Flaechenart.GLAS -> when (stufe) {
            GlasStufe.LEICHT -> if (dunkel) 0.50f else 0.55f
            GlasStufe.DICHT -> if (dunkel) 0.72f else 0.78f
            // Über Fotos (Schwarz bis Weiß möglich) fast deckend, sonst hält Text AA nicht
            GlasStufe.BILD -> 0.92f
        }
        Flaechenart.DECKEND -> when (stufe) {
            GlasStufe.LEICHT -> 0.90f
            GlasStufe.DICHT -> 0.96f
            GlasStufe.BILD -> 0.94f
        }
    }
    val glas = flaeche == Flaechenart.GLAS
    return GlasWerte(
        toenung = werte.glas.copy(alpha = alpha),
        unschaerfe = if (glas) 40.dp else 0.dp,
        korn = if (!glas) 0f else if (dunkel) 0.05f else 0.08f,
        kante = werte.farben.lichtkante,
    )
}

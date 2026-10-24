package de.foody.core.designsystem.glas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.token.FoodyFormen
import de.foody.core.designsystem.token.GlasStufe
import de.foody.core.designsystem.token.glasWerte
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/** Wie eine Glasfläche gezeichnet wird. */
enum class GlasModus { UNSCHAERFE, TOENUNG }

/** Gezeichneter Modus an der Fläche – für Tests. */
val GlasModusKey = SemanticsPropertyKey<GlasModus>("GlasModus")
var SemanticsPropertyReceiver.glasModus by GlasModusKey

/**
 * Unschärfe nur, wenn sie erlaubt ist und ein [FoodyHintergrund] als Quelle da ist – sonst nur Tönung. Die Stufe BILD
 * (Leiste über Fotos) ist nie unscharf: Ihre Quelle wäre nur der Hintergrund, nicht das Foto darunter.
 */
internal fun glasModus(unschaerfe: Boolean, hatHaze: Boolean, stufe: GlasStufe): GlasModus =
    if (unschaerfe && hatHaze && stufe != GlasStufe.BILD) GlasModus.UNSCHAERFE else GlasModus.TOENUNG

/**
 * Glasfläche: LEICHT/DICHT mit unscharfem, getöntem Hintergrund, BILD als fast deckende Leiste über Fotos; dazu
 * eine Lichtkante. [unschaerfeErlaubt] = false für Flächen in Listen (Leistung).
 * Die Tönung allein hält WCAG AA (Kontrast-Test) – ohne Unschärfe bleibt alles lesbar.
 */
@Composable
fun GlasFlaeche(
    stufe: GlasStufe,
    modifier: Modifier = Modifier,
    form: Shape = FoodyFormen.gross,
    unschaerfeErlaubt: Boolean = true,
    inhalt: @Composable BoxScope.() -> Unit,
) {
    val werte = glasWerte(stufe, FoodyTheme.dunkel)
    val hazeState = LocalHazeState.current
    val modus = glasModus(FoodyTheme.unschaerfe && unschaerfeErlaubt, hazeState != null, stufe)
    val flaeche = when (modus) {
        GlasModus.UNSCHAERFE -> Modifier.hazeBlur(
            input = HazeInput.Sources(requireNotNull(hazeState)),
            style = HazeBlurStyle {
                blurRadius(werte.unschaerfe)
                colorEffects(listOf(HazeColorEffect.tint(werte.toenung)))
                fallbackColorEffect(HazeColorEffect.tint(werte.toenung))
            },
        )
        GlasModus.TOENUNG -> Modifier.background(werte.toenung)
    }
    Box(
        modifier
            .clip(form)
            .then(flaeche)
            .border(1.dp, werte.kante, form)
            .semantics { glasModus = modus },
        content = inhalt,
    )
}

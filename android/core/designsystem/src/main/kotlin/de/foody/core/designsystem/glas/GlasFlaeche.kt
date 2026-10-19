package de.foody.core.designsystem.glas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
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

/** Unschärfe nur, wenn sie erlaubt ist und ein [FoodyHintergrund] als Quelle da ist – sonst nur Tönung. */
internal fun glasModus(unschaerfe: Boolean, hatHaze: Boolean): GlasModus =
    if (unschaerfe && hatHaze) GlasModus.UNSCHAERFE else GlasModus.TOENUNG

/**
 * Glasfläche in einer der zwei festen Stufen: der Hintergrund dahinter unscharf und getönt, dazu eine Lichtkante.
 * Die Tönung allein hält WCAG AA (Kontrast-Test) – ohne Unschärfe bleibt alles lesbar.
 */
@Composable
fun GlasFlaeche(
    stufe: GlasStufe,
    modifier: Modifier = Modifier,
    form: Shape = FoodyFormen.gross,
    inhalt: @Composable BoxScope.() -> Unit,
) {
    val werte = glasWerte(stufe, FoodyTheme.dunkel)
    val hazeState = LocalHazeState.current
    val flaeche = when (glasModus(FoodyTheme.unschaerfe, hazeState != null)) {
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
            .border(1.dp, werte.kante, form),
        content = inhalt,
    )
}

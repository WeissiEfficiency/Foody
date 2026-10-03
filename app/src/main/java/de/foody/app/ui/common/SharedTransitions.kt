package de.foody.app.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/*
 * Gemeinsamer Übergang Rezeptkarte → Detailkopf. Die Scopes kommen per CompositionLocal aus FoodyRoot,
 * damit nicht jede Composable dazwischen sie als Parameter durchreichen muss. Ohne Scopes (Previews, Tests,
 * andere Einstiege) bleibt der Modifier wirkungslos.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Markiert das Rezeptbild als gemeinsames Element; Schlüssel ist die Rezept-ID. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedRecipeImage(recipeId: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedRecipeImage.sharedElement(rememberSharedContentState("recipe-image-$recipeId"), animated)
    }
}

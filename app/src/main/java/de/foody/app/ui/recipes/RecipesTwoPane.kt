package de.foody.app.ui.recipes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.foody.app.R
import de.foody.app.ui.RecipeDetailRoute
import de.foody.app.ui.common.EmptyState
import kotlinx.serialization.Serializable

@Serializable object NoRecipeSelectedRoute

/**
 * Liste und Detail nebeneinander für breite Fenster (Tablet, Foldable, Desktop-Fenster).
 * Das Detail läuft in einem eigenen NavHost, damit [RecipeDetailViewModel] seine Rezept-ID wie gewohnt
 * aus dem SavedStateHandle seines Back-Stack-Eintrags liest – und „Zurück“ zuerst das Detail schließt.
 */
@Composable
fun RecipesTwoPane(onEdit: (String) -> Unit, onCreate: () -> Unit) {
    val detailNav = rememberNavController()
    val current by detailNav.currentBackStackEntryAsState()
    val selectedId = current?.takeIf { it.destination.hasRoute<RecipeDetailRoute>() }?.toRoute<RecipeDetailRoute>()?.id
    val open: (String) -> Unit = { id ->
        detailNav.navigate(RecipeDetailRoute(id)) {
            // Aus der Liste gewählt ersetzt das Detail; „Ähnliche Rezepte“ im Detail stapeln dagegen.
            popUpTo<NoRecipeSelectedRoute>()
            launchSingleTop = true
        }
    }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(0.42f).fillMaxHeight()) {
            RecipeListScreen(onOpen = open, onCreate = onCreate, selectedId = selectedId)
        }
        VerticalDivider()
        Box(Modifier.weight(0.58f).fillMaxHeight()) {
            NavHost(detailNav, startDestination = NoRecipeSelectedRoute) {
                composable<NoRecipeSelectedRoute> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(stringResource(R.string.recipe_select_hint), icon = Icons.AutoMirrored.Outlined.MenuBook)
                    }
                }
                composable<RecipeDetailRoute> { entry ->
                    val id = entry.toRoute<RecipeDetailRoute>().id
                    RecipeDetailScreen(
                        onBack = { detailNav.popBackStack() },
                        onEdit = { onEdit(id) },
                        onOpenOther = { other -> detailNav.navigate(RecipeDetailRoute(other)) },
                    )
                }
            }
        }
    }
}

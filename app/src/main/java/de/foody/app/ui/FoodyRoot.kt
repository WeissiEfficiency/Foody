package de.foody.app.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.window.core.layout.WindowSizeClass
import de.foody.app.ui.recipes.RecipesTwoPane
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import de.foody.app.ui.common.LocalNavAnimatedScope
import de.foody.app.ui.common.LocalSharedTransitionScope
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.foody.app.R
import de.foody.app.ui.ingredients.IngredientsScreen
import de.foody.app.ui.pantry.PantryScreen
import de.foody.app.ui.planner.PlannerScreen
import de.foody.app.ui.recipes.RecipeDetailScreen
import de.foody.app.ui.recipes.RecipeEditorScreen
import de.foody.app.ui.recipes.RecipeListScreen
import de.foody.app.ui.settings.SettingsScreen
import de.foody.app.ui.shopping.ShoppingScreen
import kotlinx.serialization.Serializable

@Serializable object RecipesRoute
@Serializable data class RecipeDetailRoute(val id: String)
@Serializable data class RecipeEditorRoute(val id: String? = null)
@Serializable object PlannerRoute
@Serializable object ShoppingRoute
@Serializable object PantryRoute
@Serializable object SettingsRoute
@Serializable object IngredientsRoute

private enum class TopLevel(val route: Any, @param:StringRes val label: Int, val icon: ImageVector) {
    RECIPES(RecipesRoute, R.string.nav_recipes, Icons.Default.RestaurantMenu),
    PLANNER(PlannerRoute, R.string.nav_planner, Icons.Default.CalendarMonth),
    SHOPPING(ShoppingRoute, R.string.nav_shopping, Icons.AutoMirrored.Filled.List),
    PANTRY(PantryRoute, R.string.nav_pantry, Icons.Default.Inventory2),
    SETTINGS(SettingsRoute, R.string.nav_more, Icons.Default.Settings),
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FoodyRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val dest = backStack?.destination
    // Ab „expanded“ (≥ 840 dp) passen Raster und Rezept nebeneinander; darunter bleibt die Ein-Spalten-Navigation.
    val twoPane = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    // NavigationSuiteScaffold wechselt automatisch zwischen Bottom Bar (Telefon) und Rail (Tablet).
    val colors = MaterialTheme.colorScheme
    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = colors.onPrimary,
            indicatorColor = colors.primary,
            selectedTextColor = colors.primary,
            unselectedIconColor = colors.onSurfaceVariant,
            unselectedTextColor = colors.onSurfaceVariant,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = colors.onPrimary,
            indicatorColor = colors.primary,
            selectedTextColor = colors.primary,
        ),
    )
    NavigationSuiteScaffold(
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = colors.surfaceContainerLowest,
            navigationRailContainerColor = colors.surfaceContainerLowest,
        ),
        containerColor = colors.background,
        navigationSuiteItems = {
            TopLevel.entries.forEach { item ->
                item(
                    selected = dest?.hasRoute(item.route::class) == true,
                    onClick = {
                        nav.navigate(item.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(item.icon, contentDescription = null) },
                    label = { Text(stringResource(item.label)) },
                    colors = itemColors,
                )
            }
        },
    ) {
        SharedTransitionLayout {
        NavHost(nav, startDestination = RecipesRoute) {
            composable<RecipesRoute> {
                if (twoPane) {
                    RecipesTwoPane(onEdit = { id -> nav.navigate(RecipeEditorRoute(id)) }, onCreate = { nav.navigate(RecipeEditorRoute()) })
                } else {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout, LocalNavAnimatedScope provides this) {
                        RecipeListScreen(
                            onOpen = { nav.navigate(RecipeDetailRoute(it)) },
                            onCreate = { nav.navigate(RecipeEditorRoute()) },
                        )
                    }
                }
            }
            composable<RecipeDetailRoute> {
                val id = it.toRoute<RecipeDetailRoute>().id
                CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout, LocalNavAnimatedScope provides this) {
                    RecipeDetailScreen(
                        onBack = { nav.popBackStack() },
                        onEdit = { nav.navigate(RecipeEditorRoute(id)) },
                        onOpenOther = { other -> nav.navigate(RecipeDetailRoute(other)) },
                    )
                }
            }
            composable<RecipeEditorRoute> {
                RecipeEditorScreen(
                    onDone = { nav.popBackStack() },
                    onManageIngredients = { nav.navigate(IngredientsRoute) },
                )
            }
            composable<PlannerRoute> { PlannerScreen(onOpenRecipe = { nav.navigate(RecipeDetailRoute(it)) }) }
            composable<ShoppingRoute> { ShoppingScreen() }
            composable<PantryRoute> { PantryScreen() }
            composable<SettingsRoute> { SettingsScreen(onManageIngredients = { nav.navigate(IngredientsRoute) }) }
            composable<IngredientsRoute> { IngredientsScreen(onBack = { nav.popBackStack() }) }
        }
        }
    }
}

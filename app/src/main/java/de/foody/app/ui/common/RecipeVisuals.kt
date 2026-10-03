package de.foody.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.foody.app.R
import de.foody.app.data.db.RecipeEntity

/** Warme, appetitliche Verläufe für Rezepte ohne Foto; Auswahl stabil je Rezeptname. */
private val placeholderGradients = listOf(
    Color(0xFFFFB199) to Color(0xFFFF6F59),
    Color(0xFF9BE8C9) to Color(0xFF14B88F),
    Color(0xFFFFD98A) to Color(0xFFF2A33A),
    Color(0xFFB9D7FF) to Color(0xFF5B8DEF),
    Color(0xFFE3C2FF) to Color(0xFF9B6BDF),
    Color(0xFFC8E6A0) to Color(0xFF6FAE3A),
)

private val emojiRules = listOf(
    listOf("suppe", "eintopf", "brühe", "garbure") to "🍲",
    listOf("salat", "bowl") to "🥗",
    listOf("kuchen", "torte", "tiramisu", "cheese", "creme", "dessert", "muffin", "biskuit", "dolce") to "🍰",
    listOf("pizza", "flammkuchen", "pide") to "🍕",
    listOf("lasagne", "nudel", "pasta", "spaghetti", "rigatoni", "gnocchi", "ramen", "carbonara") to "🍝",
    listOf("curry") to "🍛",
    listOf("hähnchen", "huhn", "chicken", "ente") to "🍗",
    listOf("lachs", "fisch", "pesce", "garnele") to "🐟",
    listOf("brot", "semmel", "brötchen", "zopf", "laugen") to "🥖",
    listOf("schnitzel", "braten", "gulasch", "rind", "schwein", "hack", "frikadell", "roulade") to "🥩",
    listOf("kartoffel", "pommes", "rösti", "wedges", "knödel") to "🥔",
    listOf("waffel", "pfannkuchen", "crêpe") to "🧇",
    listOf("chili", "taco", "burrito") to "🌶️",
    listOf("shakshuka", "ei ", "omelett") to "🍳",
)

/** Emoji zum Stichwort, das am frühesten im Namen steht – das Hauptgericht steht meist vorne. */
fun recipeEmoji(name: String): String {
    val n = name.lowercase() + " "
    return emojiRules.flatMap { (keys, emoji) -> keys.map { n.indexOf(it) to emoji } }
        .filter { it.first >= 0 }
        .minByOrNull { it.first }?.second ?: "🍽️"
}

private fun gradientFor(name: String): Brush {
    val (a, b) = placeholderGradients[Math.floorMod(name.hashCode(), placeholderGradients.size)]
    return Brush.linearGradient(listOf(a, b))
}

/** Foto des Rezepts oder ein farbiger Platzhalter mit passendem Emoji. Füllt den Modifier. */
@Composable
fun RecipeImage(imageUri: String?, name: String, modifier: Modifier = Modifier, emojiSize: TextUnit = 56.sp) {
    if (imageUri != null) {
        AsyncImage(imageUri, stringResource(R.string.recipe_image), contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(gradientFor(name)), contentAlignment = Alignment.Center) {
            // Rein dekorativ: TalkBack soll nicht das Emoji vorlesen
            Text(recipeEmoji(name), fontSize = emojiSize, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/** Kleine runde Info-Pille: „⏱ 35 Min.“, „4 Portionen“. */
@Composable
fun MetaPill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onImage: Boolean = false) {
    val bg = if (onImage) Color.Black.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (onImage) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let { Icon(it, null, Modifier.size(14.dp), tint = fg) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

val RecipeEntity.totalMinutes: Int get() = (prepMinutes ?: 0) + (cookMinutes ?: 0)

/** Rasterkarte: großes quadratisches Bild, darunter Titel und Zeit – wie in Rezept-Feeds. */
@Composable
fun RecipeGridCard(recipe: RecipeEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)) {
        Box {
            RecipeImage(
                recipe.imageUri, recipe.name,
                Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium),
            )
            if (recipe.totalMinutes > 0) {
                MetaPill(
                    stringResource(R.string.minutes_total, recipe.totalMinutes),
                    Modifier.align(Alignment.BottomStart).padding(8.dp),
                    icon = Icons.Outlined.Schedule,
                    onImage = true,
                )
            }
        }
        Text(
            recipe.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp, bottom = 4.dp),
        )
    }
}

/** Großformatige Feature-Karte mit Titel auf dem Bild (Rezepte des Tages). */
@Composable
fun RecipeHeroCard(recipe: RecipeEntity, eyebrow: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, modifier = modifier, shadowElevation = 4.dp) {
        Box(Modifier.fillMaxSize()) {
            RecipeImage(recipe.imageUri, recipe.name, Modifier.fillMaxSize(), emojiSize = 96.sp)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f)),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(eyebrow.uppercase(), style = de.foody.app.ui.theme.EyebrowStyle, color = Color.White.copy(alpha = 0.85f))
                Text(
                    recipe.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recipe.totalMinutes > 0) {
                    MetaPill(stringResource(R.string.minutes_total, recipe.totalMinutes), icon = Icons.Outlined.Schedule, onImage = true)
                }
            }
        }
    }
}

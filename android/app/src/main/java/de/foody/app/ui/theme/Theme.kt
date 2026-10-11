package de.foody.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Bildlastiger Kochapp-Look: ruhige, fast weiße Flächen, damit Rezeptbilder wirken;
 * ein frisches Minzgrün als Aktionsfarbe, Koralle als warmer Akzent (Highlights, Badges).
 * Bewusst ohne Dynamic Color – die Markenfarben sollen auf jedem Gerät gleich aussehen.
 */
/*
 * Kontraste nach WCAG 2.2 AA (gegen #FAFAF7 bzw. Weiß gemessen):
 * Mint #0A7A5D als Text 5,1:1, Weiß auf Mint 5,3:1 – das hellere #14B88F erreichte nur 2,4:1.
 * Koralle #C8422E als Text 4,7:1. Das Herz (#D64A36) ist Grafik und braucht 3:1 (4,3:1).
 */
val Mint = Color(0xFF0A7A5D)
val Coral = Color(0xFFC8422E)
val FavoriteRed = Color(0xFFD64A36)
val Ink = Color(0xFF1C2321)

private val Light = lightColorScheme(
    primary = Mint,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F5EA),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Ink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEFEC),
    onSecondaryContainer = Ink,
    tertiary = Coral,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE2DC),
    onTertiaryContainer = Color(0xFF5C1408),
    background = Color(0xFFFAFAF7),
    onBackground = Ink,
    surface = Color(0xFFFAFAF7),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDEFEC),
    onSurfaceVariant = Color(0xFF5E6663),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F5F2),
    surfaceContainer = Color(0xFFF0F2EF),
    surfaceContainerHigh = Color(0xFFEAECE9),
    surfaceContainerHighest = Color(0xFFE4E7E3),
    outline = Color(0xFFC9CECB),
    outlineVariant = Color(0xFFE1E4E1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF4FDDB4),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF00513C),
    onPrimaryContainer = Color(0xFFB5F2DD),
    secondary = Color(0xFFE6E9E6),
    onSecondary = Ink,
    secondaryContainer = Color(0xFF2E3532),
    onSecondaryContainer = Color(0xFFE6E9E6),
    tertiary = Color(0xFFFF8C7A),
    onTertiary = Color(0xFF5C1408),
    tertiaryContainer = Color(0xFF7A2A1C),
    onTertiaryContainer = Color(0xFFFFDAD3),
    background = Color(0xFF121614),
    onBackground = Color(0xFFE6E9E6),
    surface = Color(0xFF121614),
    onSurface = Color(0xFFE6E9E6),
    surfaceVariant = Color(0xFF2B312E),
    onSurfaceVariant = Color(0xFFBFC6C2),
    surfaceContainerLowest = Color(0xFF0D100F),
    surfaceContainerLow = Color(0xFF1A1E1C),
    surfaceContainer = Color(0xFF1E2320),
    surfaceContainerHigh = Color(0xFF282D2A),
    surfaceContainerHighest = Color(0xFF333835),
    outline = Color(0xFF59615D),
    outlineVariant = Color(0xFF3A403D),
)

private val base = Typography()

/** Kräftige, kompakte Überschriften wie in Rezept-Feeds; Fließtext bleibt ruhig. */
private val FoodyTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
)

/** Eyebrow-Label über Überschriften („REZEPTE DES TAGES“). */
val EyebrowStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)

private val FoodyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun FoodyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = FoodyTypography,
        shapes = FoodyShapes,
        content = content,
    )
}

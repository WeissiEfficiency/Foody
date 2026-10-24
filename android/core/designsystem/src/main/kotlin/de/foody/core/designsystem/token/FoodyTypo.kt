package de.foody.core.designsystem.token

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.R

// Beide Schriften liegen als variable Dateien vor; den Schnitt wählt die Achse „wght“.
private fun schnitt(datei: Int, gewicht: FontWeight) =
    Font(datei, gewicht, variationSettings = FontVariation.Settings(FontVariation.weight(gewicht.weight)))

/** Fraunces – Serifen für Überschriften (bis `titleLarge`). */
val FrauncesFamilie = FontFamily(
    schnitt(R.font.fraunces, FontWeight.SemiBold),
    schnitt(R.font.fraunces, FontWeight.Bold),
)

/** Figtree – ruhige Grotesk für alles andere. */
val FigtreeFamilie = FontFamily(
    schnitt(R.font.figtree, FontWeight.Normal),
    schnitt(R.font.figtree, FontWeight.Medium),
    schnitt(R.font.figtree, FontWeight.SemiBold),
    schnitt(R.font.figtree, FontWeight.Bold),
)

private val basis = Typography()

// Foody ist deutschsprachig: Silbentrennung nach deutschen Regeln, statt Wörter ohne Strich zu zerreißen.
private val Deutsch = LocaleList("de-DE")

private fun TextStyle.fraunces() = copy(
    fontFamily = FrauncesFamilie,
    fontWeight = FontWeight.Bold,
    localeList = Deutsch,
    hyphens = Hyphens.Auto,
    lineBreak = LineBreak.Heading,
)

private fun TextStyle.figtree(gewicht: FontWeight) = copy(
    fontFamily = FigtreeFamilie,
    fontWeight = gewicht,
    localeList = Deutsch,
    hyphens = Hyphens.Auto,
    lineBreak = LineBreak.Paragraph,
)

/** Material-3-Größen (in sp, skalieren mit der Systemschrift); Fraunces bis `titleLarge`, darunter Figtree. */
val FoodyTypografie = Typography(
    displayLarge = basis.displayLarge.fraunces(),
    displayMedium = basis.displayMedium.fraunces(),
    displaySmall = basis.displaySmall.fraunces(),
    headlineLarge = basis.headlineLarge.fraunces(),
    headlineMedium = basis.headlineMedium.fraunces(),
    headlineSmall = basis.headlineSmall.fraunces(),
    titleLarge = basis.titleLarge.fraunces(),
    titleMedium = basis.titleMedium.figtree(FontWeight.Bold),
    titleSmall = basis.titleSmall.figtree(FontWeight.Bold),
    bodyLarge = basis.bodyLarge.figtree(FontWeight.Normal),
    bodyMedium = basis.bodyMedium.figtree(FontWeight.Normal),
    bodySmall = basis.bodySmall.figtree(FontWeight.Normal),
    labelLarge = basis.labelLarge.figtree(FontWeight.Bold),
    labelMedium = basis.labelMedium.figtree(FontWeight.SemiBold),
    labelSmall = basis.labelSmall.figtree(FontWeight.SemiBold),
)

/** Augenbraue über Überschriften („ABENDESSEN · 18:30“). */
val AugenbraueStil = TextStyle(
    fontFamily = FigtreeFamilie,
    fontSize = 12.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.2.sp,
)

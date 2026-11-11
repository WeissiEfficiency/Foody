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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.R

// Alle Schriften liegen als variable Dateien vor; den Schnitt wählt die Achse „wght“.
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

/** Cinzel – Versalien-Antiqua für Titel der „Düsteren Sage“ (Stil Q). */
val CinzelFamilie = FontFamily(
    schnitt(R.font.cinzel, FontWeight.Medium),
    schnitt(R.font.cinzel, FontWeight.SemiBold),
    schnitt(R.font.cinzel, FontWeight.Bold),
)

/** EB Garamond – Buchschrift für den Text der „Düsteren Sage“ (Stil Q). */
val EbGaramondFamilie = FontFamily(
    schnitt(R.font.eb_garamond, FontWeight.Normal),
    schnitt(R.font.eb_garamond, FontWeight.Medium),
    schnitt(R.font.eb_garamond, FontWeight.SemiBold),
    schnitt(R.font.eb_garamond, FontWeight.Bold),
)

private val basis = Typography()

// Foody ist deutschsprachig: Silbentrennung nach deutschen Regeln, statt Wörter ohne Strich zu zerreißen.
private val Deutsch = LocaleList("de-DE")

/** Überschrift: [familie] im Schnitt [gewicht], [abstand] als Laufweite (nicht angegeben = die von Material). */
private fun TextStyle.titel(familie: FontFamily, gewicht: FontWeight, abstand: TextUnit) = copy(
    fontFamily = familie,
    fontWeight = gewicht,
    letterSpacing = if (abstand.isSpecified) abstand else letterSpacing,
    localeList = Deutsch,
    hyphens = Hyphens.Auto,
    lineBreak = LineBreak.Heading,
)

private fun TextStyle.fliesstext(familie: FontFamily, gewicht: FontWeight) = copy(
    fontFamily = familie,
    fontWeight = gewicht,
    localeList = Deutsch,
    hyphens = Hyphens.Auto,
    lineBreak = LineBreak.Paragraph,
)

/**
 * Material-3-Größen (in sp, skalieren mit der Systemschrift): [titel] bis `titleLarge`, darunter [text]. Jeder Stil
 * bringt seine Schriften mit; Milchglas ist [FoodyTypografie].
 */
fun foodyTypografie(
    titel: FontFamily,
    text: FontFamily,
    titelGewicht: FontWeight = FontWeight.Bold,
    titelAbstand: TextUnit = TextUnit.Unspecified,
): Typography {
    fun TextStyle.t() = titel(titel, titelGewicht, titelAbstand)
    return Typography(
        displayLarge = basis.displayLarge.t(),
        displayMedium = basis.displayMedium.t(),
        displaySmall = basis.displaySmall.t(),
        headlineLarge = basis.headlineLarge.t(),
        headlineMedium = basis.headlineMedium.t(),
        headlineSmall = basis.headlineSmall.t(),
        titleLarge = basis.titleLarge.t(),
        titleMedium = basis.titleMedium.fliesstext(text, FontWeight.Bold),
        titleSmall = basis.titleSmall.fliesstext(text, FontWeight.Bold),
        bodyLarge = basis.bodyLarge.fliesstext(text, FontWeight.Normal),
        bodyMedium = basis.bodyMedium.fliesstext(text, FontWeight.Normal),
        bodySmall = basis.bodySmall.fliesstext(text, FontWeight.Normal),
        labelLarge = basis.labelLarge.fliesstext(text, FontWeight.Bold),
        labelMedium = basis.labelMedium.fliesstext(text, FontWeight.SemiBold),
        labelSmall = basis.labelSmall.fliesstext(text, FontWeight.SemiBold),
    )
}

/** Milchglas: Fraunces bis `titleLarge`, darunter Figtree. */
val FoodyTypografie = foodyTypografie(titel = FrauncesFamilie, text = FigtreeFamilie)

/** Augenbraue über Überschriften („ABENDESSEN · 18:30“) in der Schrift [familie]. */
fun augenbraue(familie: FontFamily) = TextStyle(
    fontFamily = familie,
    fontSize = 12.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.2.sp,
)

/** Augenbraue von Milchglas. */
val AugenbraueStil = augenbraue(FigtreeFamilie)

package de.foody.core.designsystem.token

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.R

// Variable Schriften: Den Schnitt wählt die Achse „wght“.
private fun schnitt(datei: Int, gewicht: FontWeight) =
    Font(datei, gewicht, variationSettings = FontVariation.Settings(FontVariation.weight(gewicht.weight)))

/**
 * Eine statische Datei für mehrere Gewichte: Schriften mit nur einem Schnitt (Dela Gothic One, UnifrakturCook …) sollen
 * bei „fett“ nicht künstlich verdickt werden.
 */
private fun fuer(datei: Int, vararg gewichte: FontWeight, stil: FontStyle = FontStyle.Normal) = gewichte.map { Font(datei, it, stil) }.toTypedArray()

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

// Schriften der Stile R bis V (ADR 0020), beschnitten mit `werkzeuge/schriften_beschneiden.py`

/** Dela Gothic One – plakative Titel von Anime (R), nur ein Schnitt. */
val DelaGothicFamilie = FontFamily(*fuer(R.font.dela_gothic_one, FontWeight.Normal, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black))

/** M PLUS Rounded 1c – runde Textschrift von Anime (R). */
val MPlusRundFamilie = FontFamily(
    Font(R.font.mplus_rounded_medium, FontWeight.Medium),
    Font(R.font.mplus_rounded_bold, FontWeight.Bold),
    Font(R.font.mplus_rounded_extrabold, FontWeight.ExtraBold),
)

/** Kaisei Decol – Titel von Edo (S). */
val KaiseiDecolFamilie = FontFamily(Font(R.font.kaisei_decol_medium, FontWeight.Medium), Font(R.font.kaisei_decol_bold, FontWeight.Bold))

/** Zen Kaku Gothic New – Text von Edo (S). */
val ZenKakuFamilie = FontFamily(
    Font(R.font.zen_kaku_regular, FontWeight.Normal),
    Font(R.font.zen_kaku_medium, FontWeight.Medium),
    Font(R.font.zen_kaku_bold, FontWeight.Bold),
)

/** UnifrakturCook – Fraktur für die Seitentitel von Bayrisch (T) in Immersiv, nur ein (fetter) Schnitt. */
val UnifrakturFamilie = FontFamily(*fuer(R.font.unifraktur_cook, FontWeight.Normal, FontWeight.Bold, FontWeight.ExtraBold))

/** Alegreya – Überschriften und Knöpfe von Bayrisch (T), variabel 500–800. */
val AlegreyaFamilie = FontFamily(
    schnitt(R.font.alegreya, FontWeight.Medium),
    schnitt(R.font.alegreya, FontWeight.SemiBold),
    schnitt(R.font.alegreya, FontWeight.Bold),
    schnitt(R.font.alegreya, FontWeight.ExtraBold),
)

/** Alegreya Sans – Text von Bayrisch (T). */
val AlegreyaSansFamilie = FontFamily(
    Font(R.font.alegreya_sans_regular, FontWeight.Normal),
    Font(R.font.alegreya_sans_medium, FontWeight.Medium),
    Font(R.font.alegreya_sans_bold, FontWeight.Bold),
)

/** IM Fell English – Buchschrift des Kosmischen Grauens (U), auch kursiv; nur ein Gewicht. */
val ImFellFamilie = FontFamily(
    *fuer(R.font.im_fell_english, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold),
    *fuer(R.font.im_fell_english_kursiv, FontWeight.Normal, FontWeight.Bold, stil = FontStyle.Italic),
)

/** IM Fell English SC – Kapitälchen für die Titel des Kosmischen Grauens (U). */
val ImFellKapitaelchenFamilie = FontFamily(*fuer(R.font.im_fell_english_sc, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold))

/** Special Elite – Schreibmaschine des Kosmischen Grauens (U). */
val SpecialEliteFamilie = FontFamily(*fuer(R.font.special_elite, FontWeight.Normal, FontWeight.Bold))

/** Exo 2 – Grotesk des Sternenkreuzers (V), variabel 500–800. */
val Exo2Familie = FontFamily(
    schnitt(R.font.exo2, FontWeight.Medium),
    schnitt(R.font.exo2, FontWeight.SemiBold),
    schnitt(R.font.exo2, FontWeight.Bold),
    schnitt(R.font.exo2, FontWeight.ExtraBold),
)

/** Share Tech Mono – Anzeigen des Sternenkreuzers (V). */
val ShareTechMonoFamilie = FontFamily(*fuer(R.font.share_tech_mono, FontWeight.Normal, FontWeight.Bold))

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

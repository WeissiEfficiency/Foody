package de.foody.core.designsystem.stil

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.foody.core.designsystem.token.AugenbraueStil
import de.foody.core.designsystem.token.Farbwelt
import de.foody.core.designsystem.token.FarbweltWerte
import de.foody.core.designsystem.token.Flaechenart
import de.foody.core.designsystem.token.FoodyFarben
import de.foody.core.designsystem.token.FoodyFormen
import de.foody.core.designsystem.token.FoodyTypografie
import de.foody.core.designsystem.token.GlasStufe
import de.foody.core.designsystem.token.MakroFarben
import de.foody.core.designsystem.token.Verlauf
import de.foody.core.designsystem.token.augenbraue
import de.foody.core.designsystem.token.foodyTypografie
import de.foody.core.designsystem.token.glasWerte
import de.foody.core.designsystem.token.kontrast
import de.foody.core.designsystem.token.mischen

/** Zeichnung auf dem Hintergrund, nur in [StilVariante.IMMERSIV]; reine Textur, Text liegt nie direkt auf dem Muster. */
enum class Hintergrundmuster { KEINS, PUNKTE, RAUTEN, STERNE, FASERN }

/**
 * Alles, was ein Stil in einer Variante festlegt: Farben (als [FarbweltWerte], damit Material und alle Komponenten
 * sofort passen), Formen, Schrift, Flächenart, Kanten, Muster. Gelesen über `FoodyTheme.stil`. Milchglas ergibt
 * genau die bisherigen Werte der Farbwelt ([milchglasWerte]).
 */
@Immutable
data class StilWerte(
    val stil: FoodyStil,
    val variante: StilVariante,
    val akzent: StilAkzent?,
    val dunkel: Boolean,
    val werte: FarbweltWerte,
    val formen: FoodyFormen,
    val typografie: Typography,
    val augenbraue: TextStyle,
    val flaeche: Flaechenart,
    /** Breite der Kante um Glas und Karten. */
    val kante: Dp,
    /** Harter Versatzschatten unter Karten (Anime, Immersiv); `null` = keiner. */
    val schatten: Color?,
    val muster: Hintergrundmuster,
    /** Farbe des Musters, nur Grafik. */
    val zier: Color,
    /** Herz auf dem hellen Abzeichen über Rezeptbildern (3:1 auf Weiß). */
    val herz: Color,
) {
    val farben: FoodyFarben get() = werte.farben
}

/** Milchglas mit der [farbwelt] im Modus [dunkel]: dieselben Werte wie vor den Stilen. */
fun milchglasWerte(farbwelt: Farbwelt, dunkel: Boolean): StilWerte {
    val werte = farbwelt.werte(dunkel)
    return StilWerte(
        stil = FoodyStil.MILCHGLAS, variante = StilVariante.Standard, akzent = null, dunkel = dunkel, werte = werte,
        formen = FoodyFormen.Rund, typografie = FoodyTypografie, augenbraue = AugenbraueStil, flaeche = Flaechenart.GLAS,
        kante = 1.dp, schatten = null, muster = Hintergrundmuster.KEINS, zier = werte.farben.primaer,
        herz = farbwelt.werte(dunkel = false).farben.primaer,
    )
}

/**
 * Werte für [stil] in [variante] mit dem Akzent [akzentName]; Milchglas nimmt [farbwelt] und [dunkel]. Die Farben der
 * Entwürfe werden dabei so weit aufgehellt oder abgedunkelt, dass sie WCAG 2.2 AA halten (Text 4,5:1, Grafik 3:1 auf
 * Grund, Verlauf und jeder Glasstufe); `KontrastTest` prüft das für alle Kombinationen.
 */
fun stilWerte(
    stil: FoodyStil,
    variante: StilVariante = StilVariante.Standard,
    akzentName: String? = null,
    dunkel: Boolean = false,
    farbwelt: Farbwelt = Farbwelt.Standard,
): StilWerte {
    val akzent = stil.akzent(akzentName) ?: return milchglasWerte(farbwelt, dunkel)
    val immersiv = variante == StilVariante.IMMERSIV
    val g = grundlage(stil, immersiv, akzent.farbe)
    val werte = ableiten(g, akzent.farbe, if (stil == FoodyStil.KREUZER) Holo else null)
    return StilWerte(
        stil = stil, variante = variante, akzent = akzent, dunkel = g.dunkel, werte = werte,
        formen = formen(stil, immersiv), typografie = schrift(stil).typografie, augenbraue = schrift(stil).augenbraue,
        flaeche = Flaechenart.DECKEND,
        kante = if (immersiv && (stil == FoodyStil.ANIME || stil == FoodyStil.BAYRISCH)) 2.dp else 1.dp,
        schatten = if (immersiv && stil == FoodyStil.ANIME) g.text else null,
        muster = if (!immersiv) Hintergrundmuster.KEINS else muster(stil),
        zier = if (stil == FoodyStil.KREUZER) Holo else akzent.farbe,
        herz = angleichen(akzent.farbe, AbzeichenUntergruende, GRAFIK, Color.Black),
    )
}

/** Holo-Blau des Sternenkreuzers: zweite Farbe neben dem Akzent (Grafik, Linien). */
private val Holo = Color(0xFF5CE1FF)

private const val TEXT = 4.5
private const val GRAFIK = 3.0

// Helles Abzeichen (Weiß 90 %) über einem Foto von Schwarz bis Weiß
private val AbzeichenUntergruende = listOf(mischen(Color.White.copy(alpha = 0.9f), Color.Black), Color.White)

/** Rohwerte eines Stils aus dem Entwurf, vor dem Angleichen an AA. */
private data class Grundlage(
    val dunkel: Boolean,
    val grund: Color,
    val text: Color,
    val neben: Color,
    val glas: Color,
    val linie: Color,
    val kante: Color,
    val oben: Color = Color.Transparent,
    val unten: Color = Color.Transparent,
)

private fun grundlage(stil: FoodyStil, immersiv: Boolean, akzent: Color): Grundlage = when (stil) {
    FoodyStil.SAGE -> if (immersiv) {
        Grundlage(
            dunkel = true, grund = Color(0xFF0D0C0A), text = Color(0xFFE4DCC8), neben = Color(0xFF9A9079),
            glas = Color(0xFF17140F), linie = Color(0xFF3A3428), kante = Color(0xFF5A4F38),
            oben = Color(0xFF1E1B16), unten = akzent.copy(alpha = 0.10f),
        )
    } else {
        Grundlage(
            dunkel = true, grund = Color(0xFF121110), text = Color(0xFFE4DCC8), neben = Color(0xFF9A9079),
            glas = Color(0xFF1A1814), linie = Color(0xFF2E2A22), kante = Color(0xFF3A3428),
        )
    }
    FoodyStil.ANIME -> if (immersiv) {
        Grundlage(
            dunkel = false, grund = Color(0xFFFFF4F8), text = Color(0xFF1B1A2E), neben = Color(0xFF4A4860),
            glas = Color(0xFFFFFFFF), linie = Color(0xFFF0E2EA), kante = Color(0xFF1B1A2E),
            oben = akzent.copy(alpha = 0.22f), unten = Color(0xFFFFE45C).copy(alpha = 0.30f),
        )
    } else {
        Grundlage(
            dunkel = false, grund = Color(0xFFFFFFFF), text = Color(0xFF1B1A2E), neben = Color(0xFF55546A),
            glas = Color(0xFFFFF8FB), linie = Color(0xFFEEE6EA), kante = Color(0xFFE4DAE0),
        )
    }
    FoodyStil.EDO -> if (immersiv) {
        Grundlage(
            dunkel = false, grund = Color(0xFFE6DCC4), text = Color(0xFF231F1A), neben = Color(0xFF5A5145),
            glas = Color(0xFFF6EFDF), linie = Color(0xFFD8CBAE), kante = Color(0xFFC9B98F),
            oben = Color(0xFFF6EFDF).copy(alpha = 0.7f), unten = akzent.copy(alpha = 0.10f),
        )
    } else {
        Grundlage(
            dunkel = false, grund = Color(0xFFEFE6D2), text = Color(0xFF231F1A), neben = Color(0xFF5A5145),
            glas = Color(0xFFF6EFDF), linie = Color(0xFFD8CBAE), kante = Color(0xFFD8CBAE),
        )
    }
    FoodyStil.BAYRISCH -> if (immersiv) {
        Grundlage(
            dunkel = false, grund = Color(0xFFFBF6EA), text = Color(0xFF2B1E14), neben = Color(0xFF5C4A3A),
            glas = Color(0xFFFFFFFF), linie = Color(0xFFCDBB9C), kante = akzent,
            // Himmel oben, der in Papier übergeht
            oben = Color(0xFFCFE3F3).copy(alpha = 0.9f),
        )
    } else {
        Grundlage(
            dunkel = false, grund = Color(0xFFFBF6EA), text = Color(0xFF2B1E14), neben = Color(0xFF5C4A3A),
            glas = Color(0xFFFFFFFF), linie = Color(0xFFCDBB9C), kante = Color(0xFFCDBB9C),
        )
    }
    FoodyStil.GRAUEN -> if (immersiv) {
        Grundlage(
            dunkel = true, grund = Color(0xFF070B0A), text = Color(0xFFCFE0D6), neben = Color(0xFF7F948A),
            glas = Color(0xFF0E1814), linie = Color(0xFF24352E), kante = Color(0xFF2E4A3E),
            oben = Color(0xFF13221C), unten = akzent.copy(alpha = 0.08f),
        )
    } else {
        // Pergament
        Grundlage(
            dunkel = false, grund = Color(0xFFC9BB96), text = Color(0xFF2A2418), neben = Color(0xFF4A4030),
            glas = Color(0xFFD9CDAE), linie = Color(0xFFB9AA86), kante = Color(0xFFA89A76),
            oben = Color(0xFFE2D8BC), unten = Color(0xFFB9AA86).copy(alpha = 0.9f),
        )
    }
    FoodyStil.KREUZER -> if (immersiv) {
        Grundlage(
            dunkel = true, grund = Color(0xFF050913), text = Color(0xFFE9EEF2), neben = Color(0xFF8A97AD),
            glas = Color(0xFF0A1222), linie = Color(0xFF1C2A44), kante = Holo.copy(alpha = 0.35f),
            oben = Color(0xFF0E1A33), unten = Holo.copy(alpha = 0.07f),
        )
    } else {
        Grundlage(
            dunkel = true, grund = Color(0xFF0B0F17), text = Color(0xFFE6EAF0), neben = Color(0xFF8D96A6),
            glas = Color(0xFF121826), linie = Color(0xFF222A38), kante = Color(0xFF2A3346),
        )
    }
    FoodyStil.MILCHGLAS -> error("Milchglas hat keine Grundlage, es nutzt die Farbwelt")
}

/** Rollen aus der [g]rundlage und dem [akzent]; [zweite] ersetzt Oliv (Grafik), etwa das Holo-Blau. */
private fun ableiten(g: Grundlage, akzent: Color, zweite: Color?): FarbweltWerte {
    val verlauf = Verlauf(oben = g.oben, unten = g.unten)
    val platzhalter = listOf(lerp(g.glas, akzent, 0.35f), lerp(g.glas, akzent, 0.55f), lerp(g.glas, akzent, 0.8f))
    val untergruende = listOf(g.grund, mischen(g.oben, g.grund), mischen(g.unten, g.grund))
    val roh = FoodyFarben(
        grund = g.grund, text = g.text, nebentext = g.neben, primaer = akzent, aufPrimaer = g.grund,
        primaerBehaelter = akzent, aufPrimaerBehaelter = g.text, oliv = akzent, safran = akzent, balkenSpur = g.linie,
        lichtkante = g.kante, makro = if (g.dunkel) MakroFarben.Dunkel else MakroFarben.Hell,
    )
    val vorlaeufig = FarbweltWerte(roh, verlauf, g.glas, platzhalter)
    fun toenung(stufe: GlasStufe) = glasWerte(stufe, g.dunkel, vorlaeufig, Flaechenart.DECKEND).toenung
    // Jede Glasstufe auf jedem Untergrund, dazu die Leiste über Fotos (Schwarz, Weiß, Platzhalter)
    val glasflaechen = untergruende.flatMap { u -> GlasStufe.entries.map { mischen(toenung(it), u) } }
    val bildleisten = (listOf(Color.Black, Color.White) + platzhalter.first() + platzhalter.last())
        .map { mischen(toenung(GlasStufe.BILD), it) }
    val alle = untergruende + glasflaechen + bildleisten
    val richtung = if (g.dunkel) Color.White else Color.Black
    val primaer = angleichen(akzent, alle, TEXT, richtung)
    val behaelter = lerp(g.glas, akzent, if (g.dunkel) 0.22f else 0.18f)
    val text = angleichen(g.text, alle, TEXT, richtung)
    val farben = roh.copy(
        text = text,
        nebentext = angleichen(g.neben, alle, TEXT, richtung),
        primaer = primaer,
        aufPrimaer = listOf(g.grund, g.text, Color.White, Color.Black).maxBy { kontrast(it, primaer) },
        primaerBehaelter = behaelter,
        aufPrimaerBehaelter = angleichen(text, listOf(behaelter), TEXT, richtung),
        oliv = angleichen(zweite ?: if (g.dunkel) Color(0xFFBCC67F) else Color(0xFF5C6B2A), glasflaechen, GRAFIK, richtung),
        safran = angleichen(if (g.dunkel) Color(0xFFE3A653) else Color(0xFFA76718), glasflaechen, GRAFIK, richtung),
        makro = roh.makro.let { m ->
            MakroFarben(
                eiweiss = angleichen(m.eiweiss, glasflaechen, GRAFIK, richtung),
                kohlenhydrate = angleichen(m.kohlenhydrate, glasflaechen, GRAFIK, richtung),
                fett = angleichen(m.fett, glasflaechen, GRAFIK, richtung),
            )
        },
    )
    return FarbweltWerte(farben, verlauf, g.glas, platzhalter)
}

/**
 * Schiebt [farbe] in kleinen Schritten zu [richtung] (Weiß auf dunklem, Schwarz auf hellem Grund), bis sie auf allen
 * [untergruende]n mindestens [mindest]:1 hält. Hält sie es schon, bleibt sie, wie der Entwurf sie vorsieht.
 */
internal fun angleichen(farbe: Color, untergruende: List<Color>, mindest: Double, richtung: Color): Color {
    for (schritt in 0..50) {
        val c = lerp(farbe, richtung, schritt / 50f)
        if (untergruende.all { kontrast(c, it) >= mindest + 0.05 }) return c
    }
    return richtung
}

private fun formen(stil: FoodyStil, immersiv: Boolean): FoodyFormen {
    // Knöpfe wie kleine Flächen; Anime behält die Pillen
    fun rund(k: Int, m: Int, g: Int, l: Int, pille: Boolean = false) = FoodyFormen(
        RoundedCornerShape(k.dp), RoundedCornerShape(m.dp), RoundedCornerShape(g.dp), RoundedCornerShape(l.dp),
        knopf = if (pille) CircleShape else RoundedCornerShape(k.dp),
    )
    return when (stil) {
        FoodyStil.SAGE -> rund(2, 3, 4, 4)
        FoodyStil.ANIME -> rund(12, 16, 20, 24, pille = true)
        FoodyStil.EDO -> rund(2, 4, 6, 8)
        FoodyStil.BAYRISCH -> rund(10, 12, 14, 22)
        FoodyStil.GRAUEN -> rund(3, 4, 6, 8)
        // Abgeschnittene Ecken wie Schott-Paneele; Ruhig schlicht gerundet
        FoodyStil.KREUZER -> if (immersiv) {
            FoodyFormen(CutCornerShape(4.dp), CutCornerShape(8.dp), CutCornerShape(12.dp), CutCornerShape(12.dp), knopf = CutCornerShape(8.dp))
        } else {
            rund(8, 12, 16, 20)
        }
        FoodyStil.MILCHGLAS -> FoodyFormen.Rund
    }
}

private fun muster(stil: FoodyStil): Hintergrundmuster = when (stil) {
    FoodyStil.ANIME -> Hintergrundmuster.PUNKTE
    FoodyStil.EDO -> Hintergrundmuster.FASERN
    FoodyStil.BAYRISCH -> Hintergrundmuster.RAUTEN
    FoodyStil.GRAUEN, FoodyStil.KREUZER -> Hintergrundmuster.STERNE
    FoodyStil.SAGE, FoodyStil.MILCHGLAS -> Hintergrundmuster.KEINS
}

private class Schrift(val typografie: Typography, val augenbraue: TextStyle)

/**
 * Schriften je Stil. Vorerst die Systemschriften der passenden Art (Serife, Grotesk, Monospace); die Schriften der
 * Entwürfe (Cinzel, EB Garamond, Dela Gothic One …) kommen als eigene Dateien nach `res/font` (Plan, Phase 2 und 3).
 */
private fun schrift(stil: FoodyStil): Schrift = SCHRIFTEN.getValue(stil)

private val SCHRIFTEN: Map<FoodyStil, Schrift> = mapOf(
    FoodyStil.SAGE to Schrift(
        foodyTypografie(FontFamily.Serif, FontFamily.Serif, FontWeight.SemiBold, titelAbstand = 0.8.sp),
        augenbraue(FontFamily.Serif),
    ),
    FoodyStil.ANIME to Schrift(foodyTypografie(FontFamily.SansSerif, FontFamily.SansSerif, FontWeight.Black), augenbraue(FontFamily.SansSerif)),
    FoodyStil.EDO to Schrift(foodyTypografie(FontFamily.Serif, FontFamily.SansSerif), augenbraue(FontFamily.SansSerif)),
    FoodyStil.BAYRISCH to Schrift(foodyTypografie(FontFamily.Serif, FontFamily.Serif), augenbraue(FontFamily.SansSerif)),
    FoodyStil.GRAUEN to Schrift(foodyTypografie(FontFamily.Serif, FontFamily.Serif), augenbraue(FontFamily.Monospace)),
    FoodyStil.KREUZER to Schrift(
        foodyTypografie(FontFamily.SansSerif, FontFamily.SansSerif, FontWeight.ExtraBold, titelAbstand = 0.5.sp),
        augenbraue(FontFamily.Monospace),
    ),
)

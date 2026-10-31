package de.foody.core.designsystem.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Wählbare Farbwelt (Einstellungen › Darstellung). Jede bringt eigene Farben, Verlauf, Glastönung und Platzhalter für
 * hell und dunkel mit; der Verlauf ist Ton in Ton aus der Hauptfarbe. Werte aus `docs/design/milchglas/README.md`,
 * nachgerechnet für Text direkt auf dem Verlauf; der [kontrastPaare]-Test prüft jede Farbwelt in beiden Modi.
 */
enum class Farbwelt(val anzeigename: String) {
    BEERE("Beere"),
    KIRSCHE("Kirsche"),
    MANGO("Mango"),
    PFLAUME("Pflaume"),
    HEIDELBEERE("Heidelbeere"),
    LAGUNE("Lagune"),
    PISTAZIE("Pistazie"),
    ABENDROT("Abendrot");

    /** Werte für hell oder [dunkel]. */
    fun werte(dunkel: Boolean): FarbweltWerte = if (dunkel) WERTE.getValue(this).second else WERTE.getValue(this).first

    companion object {
        /** Standard, solange nichts gewählt ist. */
        val Standard = MANGO

        /** Gespeicherter Name (`MANGO` …) zurück zur Farbwelt; unbekannt oder fehlend = [Standard]. */
        fun ausName(name: String?): Farbwelt = entries.firstOrNull { it.name == name } ?: Standard
    }
}

/** Alles, was eine Farbwelt in einem Modus festlegt: Farben, Hintergrundverlauf, Glastönung (ohne Alpha), Platzhalter-Verlauf. */
@Immutable
data class FarbweltWerte(val farben: FoodyFarben, val verlauf: Verlauf, val glas: Color, val platzhalter: List<Color>)

private val WERTE: Map<Farbwelt, Pair<FarbweltWerte, FarbweltWerte>> = mapOf(
    Farbwelt.BEERE to Pair(
        FarbweltWerte(
            farben = FoodyFarben(
                grund = Color(0xFFFBF4F6), text = Color(0xFF2A1A1E), nebentext = Color(0xFF4E4144),
                primaer = Color(0xFF7A2440), aufPrimaer = Color(0xFFFFFFFF),
                primaerBehaelter = Color(0xFFEADCE0), aufPrimaerBehaelter = Color(0xFF581A2E),
                oliv = Color(0xFF5C6B2A), safran = Color(0xFFA76718), balkenSpur = Color(0xFFE8E0E3),
                lichtkante = Color.White.copy(alpha = 0.78f),
            ),
            verlauf = Verlauf(oben = Color(0xFFEA7B9F).copy(alpha = 0.72f), unten = Color(0xFFF29788).copy(alpha = 0.62f)),
            glas = Color(0xFFFAF2EC),
            platzhalter = listOf(Color(0xFFED8FAD), Color(0xFFEA7B9F), Color(0xFFB85474)),
        ),
        FarbweltWerte(
            farben = FoodyFarben(
                grund = Color(0xFF181113), text = Color(0xFFF3E7EB), nebentext = Color(0xFFC2AEB4),
                primaer = Color(0xFFF17EA3), aufPrimaer = Color(0xFF300815),
                primaerBehaelter = Color(0xFF541C2E), aufPrimaerBehaelter = Color(0xFFFCCFDD),
                oliv = Color(0xFFBCC67F), safran = Color(0xFFE3A653), balkenSpur = Color(0xFF3B2B30),
                lichtkante = Color.White.copy(alpha = 0.12f),
            ),
            verlauf = Verlauf(oben = Color(0xFF862746).copy(alpha = 0.55f), unten = Color(0xFF863527).copy(alpha = 0.45f)),
            glas = Color(0xFF2B1D21),
            platzhalter = listOf(Color(0xFF8A2E4B), Color(0xFF671E35), Color(0xFF491224)),
        ),
    ),
    Farbwelt.KIRSCHE to Pair(
        FarbweltWerte(
            farben = FoodyFarben(
                grund = Color(0xFFFBF4F4), text = Color(0xFF2B1717), nebentext = Color(0xFF524041),
                primaer = Color(0xFF801921), aufPrimaer = Color(0xFFFFFFFF),
                primaerBehaelter = Color(0xFFEBDADB), aufPrimaerBehaelter = Color(0xFF5C1218),
                oliv = Color(0xFF5C6B2A), safran = Color(0xFFA76718), balkenSpur = Color(0xFFE8E0E0),
                lichtkante = Color.White.copy(alpha = 0.78f),
            ),
            verlauf = Verlauf(oben = Color(0xFFEA7B84).copy(alpha = 0.72f), unten = Color(0xFFF2B188).copy(alpha = 0.62f)),
            glas = Color(0xFFFBF1EB),
            platzhalter = listOf(Color(0xFFED8F96), Color(0xFFEA7B84), Color(0xFFBA4F57)),
        ),
        FarbweltWerte(
            farben = FoodyFarben(
                grund = Color(0xFF181111), text = Color(0xFFF3E7E8), nebentext = Color(0xFFC2AEAF),
                primaer = Color(0xFFF17E88), aufPrimaer = Color(0xFF30080C),
                primaerBehaelter = Color(0xFF541C21), aufPrimaerBehaelter = Color(0xFFFCCFD2),
                oliv = Color(0xFFBCC67F), safran = Color(0xFFE3A653), balkenSpur = Color(0xFF3B2B2D),
                lichtkante = Color.White.copy(alpha = 0.12f),
            ),
            verlauf = Verlauf(oben = Color(0xFF86272F).copy(alpha = 0.55f), unten = Color(0xFF864C27).copy(alpha = 0.45f)),
            glas = Color(0xFF2B1D1E),
            platzhalter = listOf(Color(0xFF8A2E35), Color(0xFF671E24), Color(0xFF491217)),
        ),
    ),
    Farbwelt.MANGO to Pair(
        FarbweltWerte(
            farben = FoodyFarben(
                grund = Color(0xFFFBF7F4), text = Color(0xFF2B1D10), nebentext = Color(0xFF5D4F45),
                primaer = Color(0xFF843A06), aufPrimaer = Color(0xFFFFFFFF),
                primaerBehaelter = Color(0xFFEBDFD7), aufPrimaerBehaelter = Color(0xFF5F2A05),
                oliv = Color(0xFF5C6B2A), safran = Color(0xFFB26E19), balkenSpur = Color(0xFFE8E3DF),
                lichtkante = Color.White.copy(alpha = 0.78f),
            ),
            verlauf = Verlauf(oben = Color(0xFFEAA97B).copy(alpha = 0.72f), unten = Color(0xFFF2E588).copy(alpha = 0.62f)),
            glas = Color(0xFFFCF3EA),
            platzhalter = listOf(Color(0xFFEDB68F), Color(0xFFEAA97B), Color(0xFFBC7746)),
        ),
        FarbweltWerte(
            farben = FoodyFarben(
                grund = Color(0xFF181411), text = Color(0xFFF3ECE7), nebentext = Color(0xFFC2B6AE),
                primaer = Color(0xFFF1AD7E), aufPrimaer = Color(0xFF301908),
                primaerBehaelter = Color(0xFF54331C), aufPrimaerBehaelter = Color(0xFFFCE1CF),
                oliv = Color(0xFFBCC67F), safran = Color(0xFFE3A653), balkenSpur = Color(0xFF3B322B),
                lichtkante = Color.White.copy(alpha = 0.12f),
            ),
            verlauf = Verlauf(oben = Color(0xFF864E27).copy(alpha = 0.55f), unten = Color(0xFF867B27).copy(alpha = 0.45f)),
            glas = Color(0xFF2B221D),
            platzhalter = listOf(Color(0xFF8A542E), Color(0xFF673C1E), Color(0xFF492912)),
        ),
    ),
    Farbwelt.PFLAUME to Pair(
        FarbweltWerte(
            farben = FoodyFarben(
                grund = Color(0xFFFAF4FB), text = Color(0xFF24182A), nebentext = Color(0xFF4E4350),
                primaer = Color(0xFF672971), aufPrimaer = Color(0xFFFFFFFF),
                primaerBehaelter = Color(0xFFE7DDE8), aufPrimaerBehaelter = Color(0xFF4B1E51),
                oliv = Color(0xFF5C6B2A), safran = Color(0xFFA76718), balkenSpur = Color(0xFFE7E0E8),
                lichtkante = Color.White.copy(alpha = 0.78f),
            ),
            verlauf = Verlauf(oben = Color(0xFFDB7BEA).copy(alpha = 0.72f), unten = Color(0xFFF288CE).copy(alpha = 0.62f)),
            glas = Color(0xFFF9F2EE),
            platzhalter = listOf(Color(0xFFE08FED), Color(0xFFDB7BEA), Color(0xFFA756B4)),
        ),
        FarbweltWerte(
            farben = FoodyFarben(
                grund = Color(0xFF171118), text = Color(0xFFF2E7F3), nebentext = Color(0xFFBFAEC2),
                primaer = Color(0xFFE07EF1), aufPrimaer = Color(0xFF2A0830),
                primaerBehaelter = Color(0xFF4C1C54), aufPrimaerBehaelter = Color(0xFFF6CFFC),
                oliv = Color(0xFFBCC67F), safran = Color(0xFFE3A653), balkenSpur = Color(0xFF382B3B),
                lichtkante = Color.White.copy(alpha = 0.12f),
            ),
            verlauf = Verlauf(oben = Color(0xFF792786).copy(alpha = 0.55f), unten = Color(0xFF862768).copy(alpha = 0.45f)),
            glas = Color(0xFF291D2B),
            platzhalter = listOf(Color(0xFF7D2E8A), Color(0xFF5C1E67), Color(0xFF421249)),
        ),
    ),
    Farbwelt.HEIDELBEERE to Pair(
        FarbweltWerte(
            farben = FoodyFarben(
                grund = Color(0xFFF4F4FB), text = Color(0xFF1A1B2E), nebentext = Color(0xFF3A3A46),
                primaer = Color(0xFF2E3174), aufPrimaer = Color(0xFFFFFFFF),
                primaerBehaelter = Color(0xFFDEDEE9), aufPrimaerBehaelter = Color(0xFF212454),
                oliv = Color(0xFF5C6B2A), safran = Color(0xFFA26317), balkenSpur = Color(0xFFE0E0E9),
                lichtkante = Color.White.copy(alpha = 0.78f),
            ),
            verlauf = Verlauf(oben = Color(0xFF7B80EA).copy(alpha = 0.72f), unten = Color(0xFFB488F2).copy(alpha = 0.62f)),
            glas = Color(0xFFF7F3EF),
            platzhalter = listOf(Color(0xFF8F93ED), Color(0xFF7B80EA), Color(0xFF585DB5)),
        ),
        FarbweltWerte(
            farben = FoodyFarben(
                grund = Color(0xFF111118), text = Color(0xFFE7E7F3), nebentext = Color(0xFFAEAFC2),
                primaer = Color(0xFF8D91F2), aufPrimaer = Color(0xFF080A30),
                primaerBehaelter = Color(0xFF1C1F54), aufPrimaerBehaelter = Color(0xFFCFD1FC),
                oliv = Color(0xFFBCC67F), safran = Color(0xFFE3A653), balkenSpur = Color(0xFF2B2C3B),
                lichtkante = Color.White.copy(alpha = 0.12f),
            ),
            verlauf = Verlauf(oben = Color(0xFF272B86).copy(alpha = 0.55f), unten = Color(0xFF4F2786).copy(alpha = 0.45f)),
            glas = Color(0xFF1D1D2B),
            platzhalter = listOf(Color(0xFF2E328A), Color(0xFF1E2167), Color(0xFF121549)),
        ),
    ),
    Farbwelt.LAGUNE to Pair(
        FarbweltWerte(
            farben = FoodyFarben(
                grund = Color(0xFFF4FAFB), text = Color(0xFF132426), nebentext = Color(0xFF4A595A),
                primaer = Color(0xFF1B5C63), aufPrimaer = Color(0xFFFFFFFF),
                primaerBehaelter = Color(0xFFDBE5E6), aufPrimaerBehaelter = Color(0xFF144247),
                oliv = Color(0xFF5C6B2A), safran = Color(0xFFB26D19), balkenSpur = Color(0xFFE0E7E8),
                lichtkante = Color.White.copy(alpha = 0.78f),
            ),
            verlauf = Verlauf(oben = Color(0xFF7BDFEA).copy(alpha = 0.72f), unten = Color(0xFF88B6F2).copy(alpha = 0.62f)),
            glas = Color(0xFFF6F4ED),
            platzhalter = listOf(Color(0xFF8FE4ED), Color(0xFF7BDFEA), Color(0xFF50A4AD)),
        ),
        FarbweltWerte(
            farben = FoodyFarben(
                grund = Color(0xFF111718), text = Color(0xFFE7F2F3), nebentext = Color(0xFFAEC0C2),
                primaer = Color(0xFF7EE6F1), aufPrimaer = Color(0xFF082C30),
                primaerBehaelter = Color(0xFF1C4F54), aufPrimaerBehaelter = Color(0xFFCFF8FC),
                oliv = Color(0xFFBCC67F), safran = Color(0xFFE3A653), balkenSpur = Color(0xFF2B393B),
                lichtkante = Color.White.copy(alpha = 0.12f),
            ),
            verlauf = Verlauf(oben = Color(0xFF277E86).copy(alpha = 0.55f), unten = Color(0xFF275186).copy(alpha = 0.45f)),
            glas = Color(0xFF1D2A2B),
            platzhalter = listOf(Color(0xFF2E818A), Color(0xFF1E6067), Color(0xFF124549)),
        ),
    ),
    Farbwelt.PISTAZIE to Pair(
        FarbweltWerte(
            farben = FoodyFarben(
                grund = Color(0xFFF7FBF4), text = Color(0xFF1C2414), nebentext = Color(0xFF5C6555),
                primaer = Color(0xFF3E6020), aufPrimaer = Color(0xFFFFFFFF),
                primaerBehaelter = Color(0xFFE0E6DB), aufPrimaerBehaelter = Color(0xFF2D4517),
                oliv = Color(0xFF5C6B2A), safran = Color(0xFFBD741B), balkenSpur = Color(0xFFE3E8E0),
                lichtkante = Color.White.copy(alpha = 0.78f),
            ),
            verlauf = Verlauf(oben = Color(0xFFAFEA7B).copy(alpha = 0.72f), unten = Color(0xFFEBF288).copy(alpha = 0.62f)),
            glas = Color(0xFFF7F4EB),
            platzhalter = listOf(Color(0xFFBBED8F), Color(0xFFAFEA7B), Color(0xFF7CAC52)),
        ),
        FarbweltWerte(
            farben = FoodyFarben(
                grund = Color(0xFF141811), text = Color(0xFFEDF3E7), nebentext = Color(0xFFB7C2AE),
                primaer = Color(0xFFB4F17E), aufPrimaer = Color(0xFF1B3008),
                primaerBehaelter = Color(0xFF36541C), aufPrimaerBehaelter = Color(0xFFE4FCCF),
                oliv = Color(0xFFBCC67F), safran = Color(0xFFE3A653), balkenSpur = Color(0xFF323B2B),
                lichtkante = Color.White.copy(alpha = 0.12f),
            ),
            verlauf = Verlauf(oben = Color(0xFF548627).copy(alpha = 0.55f), unten = Color(0xFF808627).copy(alpha = 0.45f)),
            glas = Color(0xFF232B1D),
            platzhalter = listOf(Color(0xFF598A2E), Color(0xFF40671E), Color(0xFF2C4912)),
        ),
    ),
    Farbwelt.ABENDROT to Pair(
        FarbweltWerte(
            farben = FoodyFarben(
                grund = Color(0xFFFBF4F7), text = Color(0xFF2A1820), nebentext = Color(0xFF514249),
                primaer = Color(0xFF7A224C), aufPrimaer = Color(0xFFFFFFFF),
                primaerBehaelter = Color(0xFFEADCE2), aufPrimaerBehaelter = Color(0xFF581837),
                oliv = Color(0xFF5C6B2A), safran = Color(0xFFA76718), balkenSpur = Color(0xFFE8E0E4),
                lichtkante = Color.White.copy(alpha = 0.78f),
            ),
            verlauf = Verlauf(oben = Color(0xFFEA7BB0).copy(alpha = 0.72f), unten = Color(0xFFF28889).copy(alpha = 0.62f)),
            glas = Color(0xFFFAF2ED),
            platzhalter = listOf(Color(0xFFED8FBC), Color(0xFFEA7BB0), Color(0xFFB85383)),
        ),
        FarbweltWerte(
            farben = FoodyFarben(
                grund = Color(0xFF181114), text = Color(0xFFF3E7ED), nebentext = Color(0xFFC2AEB7),
                primaer = Color(0xFFF17EB5), aufPrimaer = Color(0xFF30081B),
                primaerBehaelter = Color(0xFF541C37), aufPrimaerBehaelter = Color(0xFFFCCFE5),
                oliv = Color(0xFFBCC67F), safran = Color(0xFFE3A653), balkenSpur = Color(0xFF3B2B33),
                lichtkante = Color.White.copy(alpha = 0.12f),
            ),
            verlauf = Verlauf(oben = Color(0xFF862755).copy(alpha = 0.55f), unten = Color(0xFF862728).copy(alpha = 0.45f)),
            glas = Color(0xFF2B1D23),
            platzhalter = listOf(Color(0xFF8A2E5A), Color(0xFF671E41), Color(0xFF49122D)),
        ),
    ),
)

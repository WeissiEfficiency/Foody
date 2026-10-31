package de.foody.core.designsystem.token

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Legt [oben] (mit Alpha) auf [unten] und liefert die deckende Mischfarbe. */
fun mischen(oben: Color, unten: Color): Color {
    val a = oben.alpha
    fun kanal(o: Float, u: Float) = o * a + u * (1f - a)
    return Color(kanal(oben.red, unten.red), kanal(oben.green, unten.green), kanal(oben.blue, unten.blue), 1f)
}

/** WCAG-Kontrastverhältnis zweier deckender Farben (1 … 21). */
fun kontrast(a: Color, b: Color): Double {
    val la = luminanz(a)
    val lb = luminanz(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

private fun luminanz(c: Color): Double {
    fun lin(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
}

/** Grund und die beiden Verlaufsmitten (volle Deckkraft des Verlaufs) – dort ist der Kontrast am ungünstigsten. */
fun ungünstigsteUntergruende(farben: FoodyFarben, verlauf: Verlauf): List<Color> =
    listOf(farben.grund, mischen(verlauf.oben, farben.grund), mischen(verlauf.unten, farben.grund))

/** Ein zu prüfendes Paar: [vorne] auf [hinten] braucht mindestens [mindest]:1. */
data class KontrastPaar(val name: String, val vorne: Color, val hinten: Color, val mindest: Double)

private const val TEXT = 4.5
private const val GRAFIK = 3.0

/**
 * Alle Paare, die das Designsystem für eine [farbwelt] zusagt – Text 4,5:1, Grafik 3:1, auf Glas ohne Unschärfe und
 * Korn, gegen den Grund und die vollen Stellen beider Verläufe.
 */
fun kontrastPaare(dunkel: Boolean, farbwelt: Farbwelt = Farbwelt.Standard): List<KontrastPaar> {
    val werte = farbwelt.werte(dunkel)
    val f = werte.farben
    val untergruende = ungünstigsteUntergruende(f, werte.verlauf)
    return buildList {
        untergruende.forEachIndexed { i, u ->
            add(KontrastPaar("Text ohne Glas @$i", f.text, u, TEXT))
            add(KontrastPaar("Nebentext ohne Glas @$i", f.nebentext, u, TEXT))
            // Text- und Umriss-Knöpfe, Links stehen auch direkt auf dem Hintergrund
            add(KontrastPaar("Primär als Text ohne Glas @$i", f.primaer, u, TEXT))
            add(KontrastPaar("Primär als Kante ohne Glas @$i", f.primaer, u, GRAFIK))
            for (stufe in GlasStufe.entries) {
                val glas = mischen(glasWerte(stufe, dunkel, farbwelt).toenung, u)
                add(KontrastPaar("Text auf Glas ${stufe.name} @$i", f.text, glas, TEXT))
                add(KontrastPaar("Nebentext auf Glas ${stufe.name} @$i", f.nebentext, glas, TEXT))
                add(KontrastPaar("Primär als Text auf Glas ${stufe.name} @$i", f.primaer, glas, TEXT))
                add(KontrastPaar("Oliv als Grafik auf Glas ${stufe.name} @$i", f.oliv, glas, GRAFIK))
                add(KontrastPaar("Safran als Grafik auf Glas ${stufe.name} @$i", f.safran, glas, GRAFIK))
                add(KontrastPaar("aufPrimär auf Primär (Glas ${stufe.name})", f.aufPrimaer, f.primaer, TEXT))
                add(KontrastPaar("aufPrimärBehälter auf Behälter (Glas ${stufe.name})", f.aufPrimaerBehaelter, f.primaerBehaelter, TEXT))
            }
        }
        // Über Fotos: alles von Schwarz bis Weiß möglich, dazu hellste und dunkelste Stelle des Platzhalter-Verlaufs
        val bilder = BildUntergruende + listOf("Platzhalter hell" to werte.platzhalter.first(), "Platzhalter dunkel" to werte.platzhalter.last())
        for ((name, bild) in bilder) {
            val leiste = mischen(glasWerte(GlasStufe.BILD, dunkel, farbwelt).toenung, bild)
            add(KontrastPaar("Text über Bild $name", f.text, leiste, TEXT))
            add(KontrastPaar("Nebentext über Bild $name", f.nebentext, leiste, TEXT))
            add(KontrastPaar("Primär als Text über Bild $name", f.primaer, leiste, TEXT))
            add(KontrastPaar("Herz auf Abzeichen über Bild $name", herzFarbe(farbwelt), mischen(Color.White.copy(alpha = 0.9f), bild), GRAFIK))
            add(KontrastPaar("Abzeichen-Text über Bild $name", Color.White, mischen(Color.Black.copy(alpha = 0.6f), bild), TEXT))
        }
    }
}

private val BildUntergruende = listOf(
    "Schwarz" to Color.Black,
    "Weiß" to Color.White,
)

/** Herz auf dem hellen Abzeichen: in beiden Modi die helle Primärfarbe der Farbwelt (die dunkle hielte dort keine 3:1). */
fun herzFarbe(farbwelt: Farbwelt): Color = farbwelt.werte(dunkel = false).farben.primaer

package de.foody.core.designsystem.token

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Eiweiß, Kohlenhydrate und Fett müssen sich in Balken und Legende auf einen Blick unterscheiden – in jeder Farbwelt
 * und beiden Modi. Vorher kamen sie aus Oliv, Safran und der Primärfarbe und lagen bei „Mango“ fast auf einem Ton.
 */
class MakroFarbenTest {
    private fun alle(dunkel: Boolean) = Farbwelt.entries.map { it.werte(dunkel).farben.makro }

    @Test
    fun gleichInJederFarbwelt() {
        for (dunkel in listOf(false, true)) {
            assertEquals(1, alle(dunkel).toSet().size, "Makrofarben hängen nicht von der Farbwelt ab (dunkel=$dunkel)")
        }
    }

    @Test
    fun farbtoeneWeitAuseinander() {
        for (m in listOf(MakroFarben.Hell, MakroFarben.Dunkel)) {
            val paare = listOf("Eiweiß/KH" to (m.eiweiss to m.kohlenhydrate), "Eiweiß/Fett" to (m.eiweiss to m.fett), "KH/Fett" to (m.kohlenhydrate to m.fett))
            for ((name, paar) in paare) {
                val (a, b) = paar
                val ton = tonAbstand(lab(a), lab(b))
                val delta = deltaE(lab(a), lab(b))
                assertTrue(ton >= 80.0, "$name: Farbton nur ${ton.toInt()}° auseinander")
                assertTrue(delta >= 40.0, "$name: Farbabstand ΔE nur ${delta.toInt()}")
            }
        }
    }

    @Test
    fun alsGrafikAufJedemGlas() {
        for (dunkel in listOf(false, true)) {
            for (w in Farbwelt.entries) {
                val namen = kontrastPaare(dunkel, w).map { it.name }
                for (rolle in listOf("Eiweiß", "Kohlenhydrate", "Fett")) {
                    for (stufe in GlasStufe.entries) {
                        assertTrue(namen.any { rolle in it && stufe.name in it }, "$rolle fehlt für Glas $stufe ($w, dunkel=$dunkel)")
                    }
                }
            }
        }
    }

    private data class Lab(val l: Double, val a: Double, val b: Double)

    /** sRGB → CIE-Lab (D65). */
    private fun lab(c: Color): Lab {
        fun lin(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        val r = lin(c.red)
        val g = lin(c.green)
        val bl = lin(c.blue)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * bl) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * bl
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * bl) / 1.08883
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116.0
        return Lab(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }

    private fun deltaE(p: Lab, q: Lab) = sqrt((p.l - q.l).pow(2) + (p.a - q.a).pow(2) + (p.b - q.b).pow(2))

    private fun tonAbstand(p: Lab, q: Lab): Double {
        val d = abs(Math.toDegrees(atan2(p.b, p.a)) - Math.toDegrees(atan2(q.b, q.a))) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }
}

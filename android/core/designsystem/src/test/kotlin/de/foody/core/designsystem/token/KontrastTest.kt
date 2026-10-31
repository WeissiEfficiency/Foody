package de.foody.core.designsystem.token

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** WCAG 2.2 AA für alle Farbpaare – gemessen ohne Unschärfe gegen die ungünstigste Stelle des Verlaufs. */
class KontrastTest {
    @Test
    fun kontrastBekannterWerte() {
        assertEquals(21.0, kontrast(Color.Black, Color.White), 0.01)
        assertEquals(4.48, kontrast(Color(0xFF777777), Color.White), 0.01)
    }

    @Test
    fun mischenDeckt() {
        val m = mischen(Color.Black.copy(alpha = 0.5f), Color.White)
        val erwartet = Color(0xFF808080)
        assertEquals(1f, m.alpha)
        for ((ist, soll) in listOf(m.red to erwartet.red, m.green to erwartet.green, m.blue to erwartet.blue)) {
            assertTrue(abs(ist - soll) <= 1.5f / 255f, "Kanal $ist statt $soll")
        }
    }

    @Test
    fun allePaareHell() = pruefe(Farbwelt.entries.flatMap { w -> kontrastPaare(dunkel = false, farbwelt = w).map { it.copy(name = "${w.name}: ${it.name}") } })

    @Test
    fun allePaareDunkel() = pruefe(Farbwelt.entries.flatMap { w -> kontrastPaare(dunkel = true, farbwelt = w).map { it.copy(name = "${w.name}: ${it.name}") } })

    @Test
    fun farbweltAusNameMitStandard() {
        assertEquals(Farbwelt.LAGUNE, Farbwelt.ausName("LAGUNE"))
        assertEquals(Farbwelt.MANGO, Farbwelt.ausName(null))
        assertEquals(Farbwelt.MANGO, Farbwelt.ausName("OFENWARM"))
    }

    @Test
    fun paareDeckenAlleRollenAb() {
        for (dunkel in listOf(false, true)) {
            val namen = kontrastPaare(dunkel).map { it.name }
            for (rolle in listOf("Text", "Nebentext", "Primär", "aufPrimär", "Behälter", "Oliv", "Safran")) {
                for (stufe in GlasStufe.entries) {
                    assertTrue(
                        namen.any { rolle in it && stufe.name in it },
                        "Rolle $rolle fehlt für Glas $stufe (dunkel=$dunkel)",
                    )
                }
            }
        }
    }

    @Test
    fun paareDeckenRealeUntergruendeAb() {
        for (dunkel in listOf(false, true)) {
            val namen = kontrastPaare(dunkel).map { it.name }
            for (pflicht in listOf("Primär als Text ohne Glas", "Primär als Kante ohne Glas", "über Bild", "Herz auf Abzeichen", "Abzeichen-Text")) {
                assertTrue(namen.any { pflicht in it }, "Paar „$pflicht“ fehlt (dunkel=$dunkel)")
            }
        }
    }

    private fun pruefe(paare: List<KontrastPaar>) {
        assertTrue(paare.isNotEmpty())
        val fehler = paare.mapNotNull { p ->
            val k = kontrast(p.vorne, p.hinten)
            if (k + 1e-9 < p.mindest) "${p.name}: %.2f < %.1f".format(k, p.mindest) else null
        }
        assertTrue(fehler.isEmpty(), fehler.joinToString("\n"))
    }
}

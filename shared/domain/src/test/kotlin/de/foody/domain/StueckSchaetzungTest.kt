package de.foody.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StueckSchaetzungTest {
    private fun g(name: String) = StueckSchaetzung.gramm(name)?.toPlainString()

    @Test
    fun doenerAusDemBlsHatEinStueckgewicht() {
        assertEquals("350", g("Döner Kebab, Fladenbrot gefüllt mit Grillfleisch (Geflügel), Rohkost und Sauce"))
        assertEquals("350", g("Döner vegetarisch, Fladenbrot gefüllt mit Rohkost, Schafskäse und Sauce"))
        assertEquals("350", g("Döner"))
    }

    @Test
    fun laengstesStichwortGewinnt() {
        assertEquals("15", g("Frühlingszwiebel"))
        assertEquals("80", g("Zwiebel, roh"))
        assertEquals("250", g("Gemüsezwiebel"))
        assertEquals("18", g("Eigelb"))
        assertEquals("4", g("Knoblauchzehe"))
    }

    @Test
    fun mehrzahlUndZusammengesetzteNamen() {
        assertEquals("60", g("Eier"))
        assertEquals("60", g("Hühnerei, Vollei, gekocht"))
        assertEquals("80", g("Zwiebeln"))
        assertEquals("120", g("Bio-Zitrone"))
        assertEquals("150", g("Paprika, rot, roh"))
        assertEquals("125", g("Kugel Mozzarella"))
        assertEquals("50", g("Wiener Würstchen"))
    }

    @Test
    fun kurzeStichwoerterNurAlsGanzesWort() {
        // „ei“ steckt in „Brei“ und „Schwein“, gemeint ist aber kein Ei
        assertNull(g("Gemüsebrei"))
        assertNull(g("Schweinebraten"))
    }

    @Test
    fun nurDerTeilVorDemKommaZaehlt() {
        // Im BLS beschreibt der Teil nach dem Komma die Zubereitung, nicht das Lebensmittel
        assertEquals("150", g("Kartoffel, gekocht, mit Zwiebel"))
    }

    @Test
    fun unbekanntOderOhneStueck() {
        assertNull(g("Wasser"))
        assertNull(g("Mehl"))
        assertNull(g(""))
    }

    @Test
    fun alleWerteSindPositiv() {
        StueckSchaetzung.alle().forEach { (wort, gramm) -> kotlin.test.assertTrue(gramm > BigDecimal.ZERO, wort) }
    }
}

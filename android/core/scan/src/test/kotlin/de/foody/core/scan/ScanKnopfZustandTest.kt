package de.foody.core.scan

import androidx.compose.runtime.saveable.SaverScope
import de.foody.core.data.zutaten.Packungswerte
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Zustand des Scan-Knopfs ohne Oberfläche: Meldungen, Foto-Angebot, gemerkter Strichcode, Saver. */
class ScanKnopfZustandTest {
    private val code = "4006040002031"
    private val packung = Packungswerte("Joghurt", NutrientBasis.PER_100_G, mapOf(Nutrient.ENERGY_KJ to BigDecimal(276)), code, OpenFoodFacts.QUELLE)

    private fun meldung(a: ScanAusgang): String? = ScanKnopfZustand().apply { auswerten(a) }.meldung

    @Test
    fun meldungenWoertlich() {
        assertEquals("Produkt nicht gefunden – Foto der Nährwerttabelle?",
            meldung(ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.UNBEKANNT)))
        assertEquals("Keine Verbindung – Foto der Nährwerttabelle?",
            meldung(ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.OFFLINE)))
        assertEquals("Online-Suche ist aus – Foto der Nährwerttabelle?",
            meldung(ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.ONLINE_AUS)))
        assertEquals("Keine Nährwerttabelle erkannt – bitte näher und gerade fotografieren", meldung(ScanAusgang.KeineTabelle(null)))
        assertEquals("Scanner wird vorbereitet – gleich noch einmal versuchen", meldung(ScanAusgang.WirdGeladen))
        assertEquals("Scannen braucht die Google-Play-Dienste", meldung(ScanAusgang.NichtVerfuegbar))
        assertEquals("Scannen hat nicht geklappt", meldung(ScanAusgang.Fehler))
        assertNull(meldung(ScanAusgang.Abgebrochen))
        assertNull(meldung(ScanAusgang.Gefunden(packung)))
    }

    @Test
    fun texteWoertlich() {
        assertEquals("Packung scannen", ScanTexte.KNOPF)
        assertEquals("Strichcode", ScanTexte.STRICHCODE)
        assertEquals("Foto der Nährwerttabelle", ScanTexte.FOTO)
        assertEquals("Aus der Galerie", ScanTexte.GALERIE)
        assertEquals("Werte aus Open Food Facts – bitte prüfen", ScanTexte.bittePruefen(OpenFoodFacts.QUELLE))
        assertEquals("Werte aus dem Foto – bitte prüfen", ScanTexte.bittePruefen(PackungScan.QUELLE_FOTO))
        assertEquals("Werte aus Open Food Facts werden übernommen", ScanTexte.werdenUebernommen(OpenFoodFacts.QUELLE))
        assertEquals("Werte aus dem Foto werden übernommen", ScanTexte.werdenUebernommen(PackungScan.QUELLE_FOTO))
    }

    @Test
    fun gefundenUndImKatalogWerdenWeitergegeben() {
        val z = ScanKnopfZustand()
        z.starten()
        assertTrue(z.laeuft)
        assertEquals(ScanKnopfZustand.Ergebnis.Packung(packung), z.auswerten(ScanAusgang.Gefunden(packung)))
        assertFalse(z.laeuft)
        val imKatalog = ScanAusgang.ImKatalog("z", "Joghurt", code)
        assertEquals(ScanKnopfZustand.Ergebnis.ImKatalog(imKatalog), z.auswerten(imKatalog))
        assertNull(z.auswerten(ScanAusgang.Fehler))
    }

    @Test
    fun nichtGefundenMerktStrichcode() {
        val z = ScanKnopfZustand()
        z.auswerten(ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.UNBEKANNT))
        assertTrue(z.fotoAnbieten)
        assertEquals(code, z.strichcode)
        // Treffer danach (etwa über das angebotene Foto): Angebot und Code sind erledigt, Meldung weg
        z.auswerten(ScanAusgang.Gefunden(packung))
        assertFalse(z.fotoAnbieten)
        assertNull(z.strichcode)
        assertNull(z.meldung)
    }

    @Test
    fun keineTabelleBehaeltStrichcode() {
        val z = ScanKnopfZustand()
        z.auswerten(ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.OFFLINE))
        z.auswerten(ScanAusgang.KeineTabelle(code))
        assertTrue(z.fotoAnbieten)
        assertEquals(code, z.strichcode)
    }

    @Test
    fun menueVersuchVergisst() {
        val z = ScanKnopfZustand()
        z.auswerten(ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.ONLINE_AUS))
        z.menueVersuch()
        assertNull(z.strichcode)
        assertFalse(z.fotoAnbieten)
        assertNull(z.meldung)
    }

    @Test
    fun kamerafotoPfad() {
        val z = ScanKnopfZustand()
        z.kameraGestartet("/cache/scan/a.jpg")
        assertEquals("/cache/scan/a.jpg", z.fotoPfad)
        assertEquals("/cache/scan/a.jpg", z.kameraZurueck())
        assertNull(z.fotoPfad)
        assertNull(z.kameraZurueck())
    }

    @Test
    fun saverRundreise() {
        val z = ScanKnopfZustand()
        z.auswerten(ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.OFFLINE))
        z.kameraGestartet("/cache/scan/a.jpg")
        z.starten()
        val gespeichert = with(ScanKnopfZustand.Saver) { SaverScope { true }.save(z) }!!
        val zurueck = ScanKnopfZustand.Saver.restore(gespeichert)!!
        assertEquals(z.meldung, zurueck.meldung)
        assertTrue(zurueck.fotoAnbieten)
        assertEquals(code, zurueck.strichcode)
        assertEquals("/cache/scan/a.jpg", zurueck.fotoPfad)
        // Eine laufende Erkennung überlebt das Prozessende nicht
        assertFalse(zurueck.laeuft)
    }
}

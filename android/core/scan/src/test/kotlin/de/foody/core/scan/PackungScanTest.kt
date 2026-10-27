package de.foody.core.scan

import de.foody.core.data.zutaten.Packungswerte
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import de.foody.domain.ScanErgebnis
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Ablauf „Packung scannen“: Strichcode → eigener Katalog → Open Food Facts → Foto (übernommen aus v1). */
class PackungScanTest {
    private val code = "4006040002031"
    private val joghurt = ZutatTreffer(id = "j", name = "Joghurt")
    private val packung = Packungswerte("Naturjoghurt", NutrientBasis.PER_100_G, mapOf(Nutrient.ENERGY_KJ to BigDecimal(276)), code, OpenFoodFacts.QUELLE)

    private var onlineGefragt = 0
    private var online = true
    private var katalog: Pair<String, ZutatTreffer>? = null
    private var antwort: ProduktSuche.Antwort = ProduktSuche.Antwort.Gefunden(packung)
    private var gelesen: StrichcodeStatus = StrichcodeStatus.Gelesen(code)
    private var tabelle: ScanStatus = ScanStatus.Erkannt(ScanErgebnis(NutrientBasis.PER_100_G, mapOf(Nutrient.FAT_G to BigDecimal("3.5"))))
    private var play = true

    private fun scan() = PackungScan(
        leser = { gelesen },
        tabelle = { tabelle },
        suche = { onlineGefragt++; antwort },
        katalog = { c -> katalog?.takeIf { it.first == c }?.second },
        play = { play },
        online = { online },
    )

    @Test fun katalogTrefferOhneOnlineAbfrage() = runTest {
        katalog = code to joghurt
        assertEquals(ScanAusgang.ImKatalog("j", "Joghurt", code), scan().strichcode())
        assertEquals(0, onlineGefragt)
    }

    @Test fun imKatalogLiefertIdUndName() = runTest {
        katalog = code to ZutatTreffer("z-7", "Weizenmehl")
        val a = assertIs<ScanAusgang.ImKatalog>(scan().strichcode())
        assertEquals("z-7", a.id)
        assertEquals("Weizenmehl", a.name)
        assertEquals(code, a.strichcode)
    }

    @Test fun openFoodFactsLiefertPackung() = runTest {
        assertEquals(ScanAusgang.Gefunden(packung), scan().strichcode())
        assertEquals(1, onlineGefragt)
    }

    @Test fun onlineAusSuchtNurImKatalog() = runTest {
        online = false
        // Eigene Meldung: es wurde gar nicht online gesucht (nicht „nicht in Open Food Facts“)
        assertEquals(ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.ONLINE_AUS), scan().strichcode())
        assertEquals(0, onlineGefragt)
    }

    @Test fun offlineBietetFotoAn() = runTest {
        antwort = ProduktSuche.Antwort.Offline
        assertEquals(ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.OFFLINE), scan().strichcode())
        antwort = ProduktSuche.Antwort.Unbekannt
        assertEquals(ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.UNBEKANNT), scan().strichcode())
    }

    @Test fun abbruchLadenFehler() = runTest {
        gelesen = StrichcodeStatus.Abgebrochen
        assertEquals(ScanAusgang.Abgebrochen, scan().strichcode())
        gelesen = StrichcodeStatus.WirdGeladen
        assertEquals(ScanAusgang.WirdGeladen, scan().strichcode())
        gelesen = StrichcodeStatus.Fehler
        assertEquals(ScanAusgang.Fehler, scan().strichcode())
    }

    @Test fun onlineOhneKatalog() = runTest {
        // „Online abrufen“ im Editor: die Zutat trägt den Code selbst, der Katalog-Schritt entfällt
        katalog = code to joghurt
        assertEquals(ScanAusgang.Gefunden(packung), scan().online(code))
        assertEquals(1, onlineGefragt)
        antwort = ProduktSuche.Antwort.Unbekannt
        assertEquals(ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.UNBEKANNT), scan().online(code))
    }

    @Test fun onlineAusOhneHttp() = runTest {
        online = false
        assertEquals(ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.ONLINE_AUS), scan().online(code))
        assertEquals(ScanAusgang.NichtGefunden(code, ScanAusgang.Grund.ONLINE_AUS), scan().strichcode())
        assertEquals(0, onlineGefragt)
    }

    @Test fun fotoLiefertPackungMitGemerktemCode() = runTest {
        val p = assertIs<ScanAusgang.Gefunden>(scan().foto("content://bild", code)).packung
        assertEquals(PackungScan.QUELLE_FOTO, p.quelle)
        assertEquals("Packung (Foto)", p.quelle)
        assertEquals(code, p.strichcode)
        assertEquals(null, p.name)
        assertEquals(NutrientBasis.PER_100_G, p.basis)
        assertEquals(BigDecimal("3.5"), p.werte[Nutrient.FAT_G])
    }

    @Test fun fotoOhneTabelle() = runTest {
        tabelle = ScanStatus.Erkannt(ScanErgebnis(NutrientBasis.PER_100_G, emptyMap()))
        assertEquals(ScanAusgang.KeineTabelle(null), scan().foto("content://bild", null))
        assertEquals(ScanAusgang.KeineTabelle(code), scan().foto("content://bild", code))
    }

    @Test fun fotoLadenUndFehler() = runTest {
        tabelle = ScanStatus.WirdGeladen
        assertEquals(ScanAusgang.WirdGeladen, scan().foto("content://bild", null))
        tabelle = ScanStatus.Fehler
        assertEquals(ScanAusgang.Fehler, scan().foto("content://bild", null))
    }

    @Test fun ohnePlayDienste() = runTest {
        play = false
        assertEquals(false, scan().verfuegbar)
        assertEquals(ScanAusgang.NichtVerfuegbar, scan().strichcode())
        assertEquals(ScanAusgang.NichtVerfuegbar, scan().foto("content://bild", null))
    }

    @Test fun vorladenNurMitPlayDiensten() {
        var vorgeladen = 0
        fun mit(play: Boolean) = PackungScan(
            leser = { StrichcodeStatus.Abgebrochen }, tabelle = { ScanStatus.Fehler }, suche = { ProduktSuche.Antwort.Unbekannt },
            katalog = { null },
            play = object : PlayDienste {
                override fun verfuegbar() = play
                override fun vorladen() { vorgeladen++ }
            },
            online = { true },
        )
        mit(play = false).vorladen()
        assertEquals(0, vorgeladen)
        mit(play = true).vorladen()
        assertEquals(1, vorgeladen)
    }
}

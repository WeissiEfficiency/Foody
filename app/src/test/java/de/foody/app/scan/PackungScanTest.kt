package de.foody.app.scan

import de.foody.app.data.db.IngredientEntity
import de.foody.domain.Nutrient
import de.foody.domain.NutrientBasis
import de.foody.domain.ScanErgebnis
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Ablauf „Von Packung scannen“: Strichcode → eigener Katalog → Open Food Facts → Foto. */
class PackungScanTest {
    private val code = "4006040002031"
    private val joghurt = IngredientEntity(id = "j", canonicalName = "Joghurt", barcode = code, createdAt = 1, updatedAt = 1)
    private val packung = Packung("Naturjoghurt", NutrientBasis.PER_100_G, mapOf(Nutrient.ENERGY_KJ to BigDecimal(276)), code, OpenFoodFacts.QUELLE)

    private var onlineGefragt = 0
    private var online = true
    private var katalog: IngredientEntity? = null
    private var antwort: ProduktSuche.Antwort = ProduktSuche.Antwort.Gefunden(packung)
    private var gelesen: StrichcodeStatus = StrichcodeStatus.Gelesen(code)
    private var tabelle: ScanStatus = ScanStatus.Erkannt(ScanErgebnis(NutrientBasis.PER_100_G, mapOf(Nutrient.FAT_G to BigDecimal("3.5"))))
    private var play = true

    private fun scan() = PackungScan(
        leser = { gelesen },
        tabelle = { tabelle },
        suche = { onlineGefragt++; antwort },
        katalog = { katalog?.takeIf { k -> k.barcode == it } },
        play = { play },
        online = { online },
    )

    @Test fun katalogTrefferOhneOnlineAbfrage() = runTest {
        katalog = joghurt
        assertEquals(ScanAusgang.ImKatalog(joghurt), scan().strichcode())
        assertEquals(0, onlineGefragt)
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

    @Test fun abbruchUndLaden() = runTest {
        gelesen = StrichcodeStatus.Abgebrochen
        assertEquals(ScanAusgang.Abgebrochen, scan().strichcode())
        gelesen = StrichcodeStatus.WirdGeladen
        assertEquals(ScanAusgang.WirdGeladen, scan().strichcode())
    }

    @Test fun fotoLiefertPackungMitGemerktemCode() = runTest {
        val p = assertIs<ScanAusgang.Gefunden>(scan().foto("content://bild", code)).packung
        assertEquals(PackungScan.QUELLE_FOTO, p.quelle)
        assertEquals(code, p.strichcode)
        assertEquals(null, p.name)
        assertEquals(BigDecimal("3.5"), p.werte[Nutrient.FAT_G])
    }

    @Test fun fotoOhneTabelle() = runTest {
        tabelle = ScanStatus.Erkannt(ScanErgebnis(NutrientBasis.PER_100_G, emptyMap()))
        assertEquals(ScanAusgang.KeineTabelle(null), scan().foto("content://bild", null))
    }

    @Test fun ohnePlayDienste() = runTest {
        play = false
        assertEquals(ScanAusgang.NichtVerfuegbar, scan().strichcode())
        assertEquals(ScanAusgang.NichtVerfuegbar, scan().foto("content://bild", null))
    }
}

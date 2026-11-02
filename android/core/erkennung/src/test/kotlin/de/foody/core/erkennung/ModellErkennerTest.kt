package de.foody.core.erkennung

import de.foody.domain.FotoAnweisung
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Ablauf einer Erkennung mit einem Fake-Modell (Spec Phase 2). */
class ModellErkennerTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Antwortet mit [antwort]; [blockieren] hält `fragen` an, bis abgebrochen wird. */
    private class FakeSitzung(
        val antwort: () -> String?,
        val laedt: Boolean = true,
        val blockieren: Boolean = false,
    ) : ModellSitzung {
        val fragen = mutableListOf<String>()
        val abgebrochen = CountDownLatch(1)
        var geschlossen = false

        override fun laden(pfade: ModellPfade, bildPfad: String): Boolean {
            assertTrue(File(bildPfad).isFile, "Bild liegt während der Erkennung bereit")
            return laedt
        }

        override fun fragen(anweisung: String): String? {
            synchronized(fragen) { fragen += anweisung }
            if (blockieren) {
                abgebrochen.await(10, TimeUnit.SECONDS)
                return null
            }
            return antwort()
        }

        override fun abbrechen() = abgebrochen.countDown()

        override fun close() {
            geschlossen = true
        }
    }

    private lateinit var bild: File

    private fun erkenner(sitzung: FakeSitzung, freierSpeicher: Long = 8_000_000_000L, mitModell: Boolean = true): ModellErkenner {
        val ordner = tmp.newFolder()
        if (mitModell) {
            File(ordner, "sprache.gguf").writeText("x")
            File(ordner, "bild.gguf").writeText("x")
        }
        return ModellErkenner(
            dateien = ModellDateien(ordner),
            modell = { sitzung },
            bilder = { tmp.newFile().also { bild = it } },
            speicher = { freierSpeicher },
            ablauf = Dispatchers.IO,
        )
    }

    private fun erkennen(e: ModellErkenner) = runBlocking { e.erkennen("content://foto") }

    @Test fun nameUndKcalAusDerTrainingsanweisung() {
        val s = FakeSitzung({ """{"dish_name": "Leberkässemmel", "nutritional_summary": {"calories_kcal": 480, "fat_g": 27}}""" })
        val v = assertIs<ErkennungStatus.Erkannt>(erkennen(erkenner(s))).vorschlag
        assertEquals("Leberkässemmel", v.name)
        assertEquals(480, v.kcal)
        assertEquals(listOf(FotoAnweisung.GERICHT), s.fragen, "genau eine Frage")
        assertTrue(s.geschlossen)
        assertFalse(bild.exists(), "Foto wird danach gelöscht")
    }

    @Test fun ohneModellKeinModell() {
        val s = FakeSitzung({ error("nicht gefragt") })
        assertEquals(ErkennungStatus.KeinModell, erkennen(erkenner(s, mitModell = false)))
    }

    @Test fun zuWenigSpeicher() {
        val s = FakeSitzung({ error("nicht gefragt") })
        assertEquals(ErkennungStatus.ZuWenigSpeicher, erkennen(erkenner(s, freierSpeicher = 1_500_000_000L)))
        assertTrue(s.fragen.isEmpty())
    }

    @Test fun ladefehlerIstFehler() {
        val s = FakeSitzung({ error("nicht gefragt") }, laedt = false)
        assertEquals(ErkennungStatus.Fehler, erkennen(erkenner(s)))
        assertTrue(s.geschlossen)
        assertFalse(bild.exists())
    }

    @Test fun ohneNameUndKcalUnlesbar() {
        assertEquals(ErkennungStatus.Unlesbar, erkennen(erkenner(FakeSitzung({ "Ein Teller Nudeln." }))))
        assertEquals(ErkennungStatus.Unlesbar, erkennen(erkenner(FakeSitzung({ """{"portion_size": {"Reis": 200}}""" }))))
    }

    @Test fun abbrechenStopptDieNativeSeite() {
        val s = FakeSitzung({ null }, blockieren = true)
        val e = erkenner(s)
        runBlocking {
            val lauf = async { e.erkennen("content://foto") }
            withTimeout(5_000) { while (synchronized(s.fragen) { s.fragen.isEmpty() }) delay(10) }
            lauf.cancel()
            lauf.join()
        }
        assertEquals(0, s.abgebrochen.count, "abbrechen() wurde aufgerufen")
        assertTrue(s.geschlossen)
        assertFalse(bild.exists())
    }

    @Test fun eineErkennungZurZeit() {
        var gleichzeitig = 0
        var hoechstens = 0
        val zaehler = Any()
        val s = FakeSitzung({
            synchronized(zaehler) { hoechstens = maxOf(hoechstens, ++gleichzeitig) }
            Thread.sleep(50)
            synchronized(zaehler) { gleichzeitig-- }
            """{"dish_name": "Breze", "calories": 250}"""
        })
        val e = erkenner(s)
        runBlocking {
            List(3) { async(Dispatchers.Default) { e.erkennen("content://foto") } }.forEach { it.await() }
        }
        assertEquals(1, hoechstens)
        assertEquals(3, s.fragen.size)
    }
}

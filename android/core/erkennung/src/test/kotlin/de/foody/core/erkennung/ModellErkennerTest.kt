package de.foody.core.erkennung

import de.foody.domain.FotoAnweisung
import de.foody.domain.FotoKontext
import de.foody.domain.FotoVorschlag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ablauf einer Erkennung mit einem Fake-Modell (Spec Phase 2). */
class ModellErkennerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val kontext = FotoKontext(
        rezepte = listOf(FotoKontext.Eintrag("r1", "Linsencurry")),
        planRezepte = emptySet(),
        zutaten = listOf(FotoKontext.Eintrag("z1", "Reis (roh)"), FotoKontext.Eintrag("z2", "Brokkoli")),
    )

    /** Antwortet je Anweisung; [blockieren] hält `fragen` an, bis abgebrochen wird. */
    private class FakeSitzung(
        val antworten: (String) -> String?,
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
            fragen += anweisung
            if (blockieren) {
                abgebrochen.await(10, TimeUnit.SECONDS)
                return null
            }
            return antworten(anweisung)
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

    private fun erkennen(e: ModellErkenner) = runBlocking { e.erkennen("content://foto", kontext) }

    @Test fun rezeptAusDerAuswahlMitDemGerichtAlsAlternative() {
        val s = FakeSitzung({
            if (it == FotoAnweisung.GERICHT) """{"dish_name": "Lentil curry", "calories_kcal": 520}""" else """{"choice": "Linsencurry", "portions": 1.5}"""
        })
        val e = assertIs<ErkennungStatus.Erkannt>(erkennen(erkenner(s)))
        assertEquals("r1", assertIs<FotoVorschlag.Rezept>(e.vorschlag).rezeptId)
        assertEquals("Lentil curry", assertIs<FotoVorschlag.Frei>(e.alternative).name)
        assertEquals(listOf(FotoAnweisung.auswahl(kontext), FotoAnweisung.GERICHT), s.fragen)
        assertTrue(s.geschlossen)
        assertFalse(bild.exists(), "Foto wird danach gelöscht")
    }

    @Test fun keinsDavonDannDieTrainingsanweisung() {
        val s = FakeSitzung({
            if (it == FotoAnweisung.GERICHT) """{"dish_name": "Leberkässemmel", "nutritional_summary": {"calories_kcal": 480}}""" else """{"choice": "none"}"""
        })
        val e = assertIs<ErkennungStatus.Erkannt>(erkennen(erkenner(s)))
        assertEquals("Leberkässemmel", assertIs<FotoVorschlag.Frei>(e.vorschlag).name)
        assertNull(e.alternative)
        assertEquals(listOf(FotoAnweisung.auswahl(kontext), FotoAnweisung.GERICHT), s.fragen)
    }

    @Test fun ohneRezepteNurDieTrainingsanweisung() {
        val s = FakeSitzung({ """{"portion_size": {"Reis": 200, "Brokkoli": 100}}""" })
        val e = erkenner(s)
        val status = runBlocking { e.erkennen("content://foto", kontext.copy(rezepte = emptyList())) }
        assertIs<FotoVorschlag.Teller>(assertIs<ErkennungStatus.Erkannt>(status).vorschlag)
        assertEquals(listOf(FotoAnweisung.GERICHT), s.fragen)
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
    }

    @Test fun rezeptOhneLesbareBeschreibungOhneAlternative() {
        val s = FakeSitzung({ if (it == FotoAnweisung.GERICHT) "Ein Teller Curry." else """{"choice": "Linsencurry"}""" })
        val e = assertIs<ErkennungStatus.Erkannt>(erkennen(erkenner(s)))
        assertIs<FotoVorschlag.Rezept>(e.vorschlag)
        assertNull(e.alternative)
    }

    @Test fun ohneJsonUnlesbar() {
        val s = FakeSitzung({ if (it == FotoAnweisung.GERICHT) "Ein Teller Nudeln." else """{"choice": "none"}""" })
        assertEquals(ErkennungStatus.Unlesbar, erkennen(erkenner(s)))
    }

    @Test fun abbrechenStopptDieNativeSeite() {
        val s = FakeSitzung({ null }, blockieren = true)
        val e = erkenner(s)
        runBlocking {
            val lauf = async { e.erkennen("content://foto", kontext) }
            withTimeout(5_000) { while (s.fragen.isEmpty()) kotlinx.coroutines.delay(10) }
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
        val s = FakeSitzung({
            synchronized(this) { hoechstens = maxOf(hoechstens, ++gleichzeitig) }
            Thread.sleep(50)
            synchronized(this) { gleichzeitig-- }
            """{"choice": "Linsencurry"}"""
        })
        val e = erkenner(s)
        runBlocking {
            List(3) { async(Dispatchers.Default) { e.erkennen("content://foto", kontext) } }.forEach { it.await() }
        }
        assertEquals(1, hoechstens)
    }
}

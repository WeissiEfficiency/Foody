package de.foody.core.erkennung

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** GGUF-Kopf und Import der Modelldateien (Spec Phase 2). */
class ModellDateienTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun quelle(bytes: ByteArray): () -> InputStream = { bytes.inputStream() }

    @Test fun kopfLiestDieArchitekturHinterAnderenEintraegen() {
        assertEquals("clip", GgufKopf.architektur(ggufKopf("clip").inputStream()))
        assertEquals("qwen3vl", GgufKopf.architektur(ggufKopf("qwen3vl").inputStream()))
    }

    @Test fun kopfOhneArchitekturOderKeinGguf() {
        assertNull(GgufKopf.architektur(ggufKopf(null).inputStream()))
        assertNull(GgufKopf.architektur("PK\u0003\u0004 kein Modell".byteInputStream()))
        assertNull(GgufKopf.architektur(ggufKopf("clip").copyOf(40).inputStream()))
    }

    @Test fun importOrdnetBildUndSpracheNachDemKopfZu() {
        val ordner = tmp.newFolder()
        val d = ModellDateien(ordner)
        assertEquals(ModellDateien.Stand.Fehlt, d.stand())
        val bild = ggufKopf("clip", rest = 10)
        val sprache = ggufKopf("qwen3vl", rest = 20)
        assertEquals(ModellDateien.Import.Fertig(bekannt = false), d.importieren(listOf(quelle(bild), quelle(sprache))))
        val pfade = d.pfade()!!
        assertEquals(sprache.toList(), java.io.File(pfade.sprache).readBytes().toList())
        assertEquals(bild.toList(), java.io.File(pfade.bild).readBytes().toList())
        assertEquals(ModellDateien.Stand.Bereit((bild.size + sprache.size).toLong()), d.stand())
        assertEquals(setOf("sprache.gguf", "bild.gguf"), ordner.list()!!.toSet(), "keine .teil-Dateien")
    }

    @Test fun keinGgufWirdAbgelehntUndLaesstNichtsLiegen() {
        val ordner = tmp.newFolder()
        val d = ModellDateien(ordner)
        assertEquals(ModellDateien.Import.KeinModell, d.importieren(listOf(quelle(ggufKopf("clip")), quelle("hallo".encodeToByteArray()))))
        assertNull(d.pfade())
        assertEquals(0, ordner.list()!!.size)
    }

    @Test fun zweiSprachteileOderNurEineDateiSindFalsch() {
        val d = ModellDateien(tmp.newFolder())
        assertEquals(ModellDateien.Import.FalscheDateien, d.importieren(listOf(quelle(ggufKopf("qwen3vl")), quelle(ggufKopf("qwen3vl")))))
        assertEquals(ModellDateien.Import.FalscheDateien, d.importieren(listOf(quelle(ggufKopf("clip")), quelle(ggufKopf("clip")))))
        assertEquals(ModellDateien.Import.FalscheDateien, d.importieren(listOf(quelle(ggufKopf("clip")))))
        assertNull(d.pfade())
    }

    @Test fun abgebrochenesLesenBehaeltDasAlteModell() {
        val ordner = tmp.newFolder()
        val d = ModellDateien(ordner)
        d.importieren(listOf(quelle(ggufKopf("clip")), quelle(ggufKopf("qwen3vl"))))
        val kaputt: () -> InputStream = { throw IOException("Verbindung weg") }
        assertEquals(ModellDateien.Import.Fehler, d.importieren(listOf(quelle(ggufKopf("clip")), kaputt)))
        assertEquals("qwen3vl", GgufKopf.architektur(java.io.File(d.pfade()!!.sprache)))
        assertEquals(setOf("sprache.gguf", "bild.gguf"), ordner.list()!!.toSet())
    }

    @Test fun entfernen() {
        val d = ModellDateien(tmp.newFolder())
        d.importieren(listOf(quelle(ggufKopf("clip")), quelle(ggufKopf("qwen3vl"))))
        d.entfernen()
        assertEquals(ModellDateien.Stand.Fehlt, d.stand())
    }
}

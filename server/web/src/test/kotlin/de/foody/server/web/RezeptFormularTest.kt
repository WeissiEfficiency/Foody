package de.foody.server.web

import de.foody.domain.Gang
import de.foody.domain.Mahlzeit
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.RecipePayload
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import io.ktor.http.parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

class RezeptFormularTest {
    private fun formular(vararg zeilen: List<String>, extra: Map<String, String> = emptyMap()) = RezeptFormular.aus(parameters {
        // [extra] ersetzt Vorgaben (Parameters.get liefert den ersten Wert)
        val felder = mapOf(
            "name" to "Curry", "portionen" to "2", "vorbereitung" to "", "kochzeit" to "30", "tags" to "Abend", "quelle" to "",
            "notizen" to "", "schritte" to "Reis kochen\n\nServieren", "einordnung" to "fest", "mahlzeit" to "ABENDESSEN", "gang" to "HAUPTSPEISE",
        ) + extra
        felder.forEach { (k, v) -> append(k, v) }
        for (z in zeilen) {
            append("zeile_menge", z[0]); append("zeile_einheit", z[1]); append("zeile_zutat", z[2]); append("zeile_hinweis", z[3]); append("zeile_optional", z[4])
        }
    })

    @Test fun liestZeilenUndIgnoriertLeere() {
        val f = formular(listOf("200", "GRAM", "Reis", "gewaschen", "0"), listOf("", "PIECE", "", "", "0"), listOf("", "PIECE", "Salz", "", "1"))
        assertEquals(listOf("Reis", "Salz"), f.zeilen.map { it.zutat })
        assertTrue(f.zeilen[1].optional)
        assertEquals(setOf(Mahlzeit.ABENDESSEN), f.mahlzeiten)
        assertEquals(setOf(Gang.HAUPTSPEISE), f.gaenge)
        assertTrue(f.einordnungFest)
        assertEquals(emptyMap(), f.pruefen())
        assertEquals(listOf("Reis kochen", "Servieren"), f.schrittListe())
    }

    @Test fun pruefenMeldetFehler() {
        for ((feld, wert) in listOf("name" to "", "portionen" to "0", "portionen" to "100", "portionen" to "x", "kochzeit" to "2000", "vorbereitung" to "-5")) {
            val f = formular(listOf("1", "GRAM", "Reis", "", "0"), extra = mapOf(feld to wert))
            assertTrue(feld in f.pruefen(), "$feld=$wert → ${f.pruefen()}")
        }
        for (menge in listOf("-1", "1e5", "abc")) {
            assertTrue("zeile_0" in formular(listOf(menge, "GRAM", "Reis", "", "0")).pruefen(), menge)
        }
        assertTrue("zeile_0" in formular(listOf("1", "UNBEKANNT", "Reis", "", "0")).pruefen(), "unbekannte Einheit")
    }

    @Test fun kommaUndPunktAlsDezimal() {
        val f = formular(listOf("1,5", "TABLESPOON", "Öl", "", "0"), listOf("1.5", "TABLESPOON", "Essig", "", "0"))
        assertEquals(emptyMap(), f.pruefen())
        assertEquals(0, java.math.BigDecimal("1.5").compareTo(f.zeilen[0].mengeZahl()))
        assertEquals(0, java.math.BigDecimal("1.5").compareTo(f.zeilen[1].mengeZahl()))
    }

    @Test fun ausRezeptUndZurueck() {
        val payload = RecipePayload(
            name = "Curry", servings = 2, cook = 30, tags = "Abend", favorite = true, notes = "lecker", mahlzeiten = "ABENDESSEN",
            lines = listOf(RecipePayload.Line("l1", "reis", "200", "GRAM", 0, "gewaschen", false), RecipePayload.Line("l2", "salz", "0", "PIECE", 1, null, true)),
            steps = listOf(RecipePayload.Step("s", 0, "Kochen")),
        )
        val record = SyncRecord(id = "r", type = RecordType.RECIPE, rev = 7, updatedAt = 1, payload = Protocol.json.encodeToJsonElement(payload) as JsonObject)
        val rezept = PayloadZuDomain.rezept(record)!!
        val zutaten = listOf("reis" to "Reis", "salz" to "Salz").associate { (id, n) -> id to de.foody.domain.Ingredient(id, n) }
        val h = Haushalt(7, zutaten, mapOf("r" to rezept), emptyList(), emptyList(), emptyList(), emptyList())
        val f = RezeptFormular.aus(rezept, h)
        assertEquals("r", f.id)
        assertEquals(7L, f.rev)
        assertEquals("30", f.kochzeit)
        assertEquals(listOf("Reis", "Salz"), f.zeilen.map { it.zutat })
        assertEquals("", f.zeilen[1].menge, "Menge 0 = nach Bedarf = leer")
        assertTrue(f.einordnungFest)
        assertEquals("Kochen", f.schritte)
    }
}

package de.foody.server.web

import de.foody.domain.MeasureUnit
import de.foody.sync.protocol.IngredientPayload
import de.foody.sync.protocol.PantryItemPayload
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Heute ist laut Testuhr der 11.10.2026. */
class VorratTest {
    private fun vorrat(id: String, zutat: String = "reis", menge: String = "500", einheit: String = "GRAM", mhd: String? = null) =
        datensatz(id, RecordType.PANTRY_ITEM, PantryItemPayload(zutat, menge, einheit, mhd))

    private suspend fun HttpClient.post(cookie: String, pfad: String, vararg felder: Pair<String, String>, csrf: Boolean = true): HttpResponse {
        val token = if (csrf) csrf(cookie) else null
        return submitForm(pfad, parameters {
            token?.let { append("_csrf", it) }
            for ((k, v) in felder) append(k, v)
        }) { header(HttpHeaders.Cookie, cookie) }
    }

    private fun daten(env: WebUmgebung, h: String) = HaushaltsDaten(env.deps.db).fuer(h)

    @Test fun anlegenAendernLoeschen() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis())
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val antwort = c.post(cookie, "/vorrat", "zutat" to "Reis", "menge" to "500", "einheit" to "g", "mhd" to "2026-12-01")
        assertEquals(HttpStatusCode.SeeOther, antwort.status, antwort.bodyAsText().take(400))
        assertEquals("/vorrat", antwort.headers[HttpHeaders.Location])
        val v = daten(env, h).vorrat.single()
        assertEquals("reis", v.zutatId)
        assertEquals(0, v.menge.compareTo(BigDecimal(500)))
        assertEquals(MeasureUnit.GRAM, v.einheit)
        assertEquals(LocalDate.of(2026, 12, 1), v.haltbarBis)
        assertTrue("Reis" in c.get("/vorrat") { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
        assertTrue("value=\"500\"" in c.get("/vorrat/${v.id}") { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
        c.post(cookie, "/vorrat/${v.id}", "menge" to "0,75", "einheit" to "kg", "mhd" to "")
        val geaendert = daten(env, h).vorrat.single()
        assertEquals(0, geaendert.menge.compareTo(BigDecimal("0.75")))
        assertEquals(MeasureUnit.KILOGRAM, geaendert.einheit)
        assertNull(geaendert.haltbarBis, "leeres Datum entfernt das MHD")
        c.post(cookie, "/vorrat/${v.id}/loeschen")
        assertTrue(daten(env, h).vorrat.isEmpty())
    }

    /** Review Focus 4. */
    @Test fun unbekannteZutatWirdAngelegt() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val c = ohneWeiterleitung()
        c.post(c.anmelden(benutzer), "/vorrat", "zutat" to "Zwiebeln", "menge" to "3", "einheit" to "Stk.", "mhd" to "")
        val d = daten(env, h)
        val zutat = d.zutaten.values.single()
        assertEquals("Zwiebel", zutat.name)
        assertTrue(zutat.category != null, "Abteilung geschätzt")
        assertEquals(zutat.id, d.vorrat.single().zutatId)
    }

    @Test fun sonderzeichenEscaped() = webServer { env ->
        val (benutzer, _, _) = env.haushalt()
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        c.post(cookie, "/vorrat", "zutat" to "<b>Brot</b>", "menge" to "1", "einheit" to "Stk.", "mhd" to "")
        val html = c.get("/vorrat") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("&lt;b&gt;Brot&lt;/b&gt;" in html && "<b>Brot</b>" !in html)
    }

    @Test fun abgelaufenMarkiert() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), vorrat("v1", mhd = "2026-10-10"), vorrat("v2", mhd = "2026-10-11"))
        val c = ohneWeiterleitung()
        val html = c.get("/vorrat") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText()
        assertEquals(1, Regex("abgelaufen").findAll(html).count(), "nur gestern ist abgelaufen, heute nicht")
    }

    @Test fun ungueltigeEingaben() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis())
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        for ((felder, meldung) in listOf(
            arrayOf("zutat" to "Reis", "menge" to "1e5", "einheit" to "g", "mhd" to "") to "Bitte eine gültige Menge",
            arrayOf("zutat" to "Reis", "menge" to "-1", "einheit" to "g", "mhd" to "") to "Bitte eine gültige Menge",
            arrayOf("zutat" to "Reis", "menge" to "1", "einheit" to "Eimer", "mhd" to "") to "Unbekannte Einheit",
            arrayOf("zutat" to " ", "menge" to "1", "einheit" to "g", "mhd" to "") to "Bitte eine Zutat eingeben",
            arrayOf("zutat" to "Reis", "menge" to "1", "einheit" to "g", "mhd" to "2028-10-11") to "Datum höchstens ein Jahr",
        )) {
            val antwort = c.post(cookie, "/vorrat", *felder)
            assertEquals(HttpStatusCode.OK, antwort.status)
            val html = antwort.bodyAsText()
            assertTrue(meldung in html, "${felder.toList()}: ${html.take(1500)}")
            assertTrue("value=\"${felder[1].second}\"" in html, "Eingabe bleibt")
        }
        assertTrue(daten(env, h).vorrat.isEmpty())
    }

    @Test fun fremdeIdsSind404UndCsrf403() = webServer { env ->
        val (benutzer, _, h) = env.haushalt("Meins")
        val (_, _, fremd) = env.haushalt("Fremd")
        env.push(fremd, reis(), vorrat("fv"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        assertEquals(HttpStatusCode.NotFound, c.get("/vorrat/fv") { header(HttpHeaders.Cookie, cookie) }.status)
        assertEquals(HttpStatusCode.NotFound, c.post(cookie, "/vorrat/fv", "menge" to "1", "einheit" to "g", "mhd" to "").status)
        assertEquals(HttpStatusCode.NotFound, c.post(cookie, "/vorrat/fv/loeschen").status)
        assertEquals(HttpStatusCode.Forbidden, c.post(cookie, "/vorrat", "zutat" to "Reis", "menge" to "1", "einheit" to "g", "mhd" to "", csrf = false).status)
        assertEquals(HttpStatusCode.Forbidden, c.post(cookie, "/vorrat/fv/loeschen", csrf = false).status)
        assertEquals(1, daten(env, fremd).vorrat.size)
        assertEquals(0, daten(env, fremd).vorrat.single().menge.compareTo(BigDecimal(500)))
        assertTrue(daten(env, h).vorrat.isEmpty())
    }

    @Test fun inzwischenGeloeschtGibtHinweis() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), vorrat("v1"))
        env.push(h, SyncRecord(id = "v1", type = RecordType.PANTRY_ITEM, deleted = true, updatedAt = 9))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val antwort = c.post(cookie, "/vorrat/v1", "menge" to "1", "einheit" to "g", "mhd" to "")
        val ziel = antwort.headers[HttpHeaders.Location]!!
        assertTrue("hinweis=weg" in ziel)
        assertTrue("Eintrag gibt es nicht mehr" in c.get(ziel) { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
        assertTrue(daten(env, h).vorrat.isEmpty(), "nicht wiederbelebt")
    }

    @Test fun hundertEintraegeUnter30KB() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val zutaten = (1..100).map { datensatz(UUID.randomUUID().toString(), RecordType.INGREDIENT, IngredientPayload(name = "Zutat mit Namen $it")) }
        val vorraete = zutaten.map { vorrat(UUID.randomUUID().toString(), zutat = it.id, mhd = LocalDate.of(2026, 12, 1).toString()) }
        env.push(h, *(zutaten + vorraete).toTypedArray())
        val c = ohneWeiterleitung()
        val bytes = c.get("/vorrat") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsBytes().size
        assertTrue(bytes < 30_000, "$bytes Bytes")
    }
}


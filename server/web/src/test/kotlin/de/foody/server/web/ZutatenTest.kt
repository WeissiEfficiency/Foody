package de.foody.server.web

import de.foody.sync.protocol.IngredientPayload
import de.foody.sync.protocol.PantryItemPayload
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.ShoppingItemPayload
import de.foody.sync.protocol.ShoppingListPayload
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
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZutatenTest {
    private val leer = mapOf("id" to "", "name" to "", "kategorie" to "", "dichte" to "", "stueckgewicht" to "", "basis" to "",
        "kj" to "", "eiweiss" to "", "kh" to "", "fett" to "", "ballaststoffe" to "", "zucker" to "", "salz" to "")

    private suspend fun HttpClient.speichern(cookie: String, vararg felder: Pair<String, String>, csrf: Boolean = true): HttpResponse {
        val token = if (csrf) csrf(cookie) else null
        val alle = leer + felder.toMap()
        return submitForm("/zutat/speichern", parameters {
            token?.let { append("_csrf", it) }
            for ((k, v) in alle) append(k, v)
        }) { header(HttpHeaders.Cookie, cookie) }
    }

    private suspend fun HttpClient.post(cookie: String, pfad: String, csrf: Boolean = true): HttpResponse {
        val token = if (csrf) csrf(cookie) else null
        return submitForm(pfad, parameters { token?.let { append("_csrf", it) } }) { header(HttpHeaders.Cookie, cookie) }
    }

    private fun roh(env: WebUmgebung, h: String) = HaushaltsDaten(env.deps.db).fuer(h).zutatenRoh

    @Test fun anlegenUndBearbeiten() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val antwort = c.speichern(cookie, "name" to "Haferflocken", "basis" to "PER_100_G", "kj" to "1550", "eiweiss" to "13,5")
        assertEquals(HttpStatusCode.SeeOther, antwort.status, antwort.bodyAsText().take(400))
        val z = roh(env, h).values.single()
        assertEquals("/zutat/${z.id}", antwort.headers[HttpHeaders.Location])
        assertEquals("Haferflocken", z.payload.name)
        assertEquals("1550", z.payload.energyKj)
        assertEquals("13.5", z.payload.protein)
        assertTrue(z.payload.category != null, "Abteilung geschätzt")
        assertTrue("370 kcal" in c.get("/zutaten") { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
        val seite = c.get("/zutat/${z.id}") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("value=\"13,5\"" in seite && "value=\"1550\"" in seite)
        c.speichern(cookie, "id" to z.id, "name" to "Haferflocken", "kategorie" to "Müsli", "basis" to "PER_100_G", "kj" to "1550", "salz" to "0,02")
        val neu = roh(env, h).getValue(z.id).payload
        assertEquals("0.02", neu.salt)
        assertEquals("Müsli", neu.category)
        assertNull(neu.protein, "geleertes Feld = unbekannt")
    }

    @Test fun strichcodeUndQuelleBleiben() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, datensatz("z1", RecordType.INGREDIENT, IngredientPayload(name = "Joghurt", basis = "PER_100_G", energyKj = "300", source = "Packung", barcode = "4001234")))
        val c = ohneWeiterleitung()
        c.speichern(c.anmelden(benutzer), "id" to "z1", "name" to "Joghurt", "basis" to "PER_100_G", "kj" to "310")
        val p = roh(env, h).getValue("z1").payload
        assertEquals("310", p.energyKj)
        assertEquals("4001234", p.barcode)
        assertEquals("Packung", p.source)
    }

    /** Review Focus 1. */
    @Test fun dubletteAbgelehnt() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, datensatz("wm", RecordType.INGREDIENT, IngredientPayload(name = "Weizenmehl")), datensatz("z2", RecordType.INGREDIENT, IngredientPayload(name = "Grieß")))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        for (felder in listOf(arrayOf("name" to "Mehl"), arrayOf("id" to "z2", "name" to "weizenmehl"))) {
            val antwort = c.speichern(cookie, *felder)
            assertEquals(HttpStatusCode.OK, antwort.status)
            val html = antwort.bodyAsText()
            assertTrue("Gibt es schon" in html && "href=\"/zutat/wm\"" in html, html.take(1500))
        }
        assertEquals(setOf("Weizenmehl", "Grieß"), roh(env, h).values.map { it.payload.name }.toSet())
    }

    @Test fun ohneBasisKeineNaehrwerte() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val c = ohneWeiterleitung()
        c.speichern(c.anmelden(benutzer), "name" to "Wasser", "kj" to "100", "fett" to "1")
        val p = roh(env, h).values.single().payload
        assertNull(p.basis)
        assertNull(p.energyKj)
        assertNull(p.fat)
    }

    @Test fun loeschenNurOhneVerwendung() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val zutaten = listOf("frei", "vorrat", "einkauf").map { datensatz(it, RecordType.INGREDIENT, IngredientPayload(name = "Zutat $it")) }
        env.push(h, reis(), *zutaten.toTypedArray(), rezept("r1", "Curry"),
            datensatz("v1", RecordType.PANTRY_ITEM, PantryItemPayload("vorrat", "1", "PIECE")),
            datensatz("L", RecordType.SHOPPING_LIST, ShoppingListPayload("Wocheneinkauf", version = 1)),
            datensatz("e1", RecordType.SHOPPING_ITEM, ShoppingItemPayload(listId = "L", ingredientId = "einkauf", name = "Zutat einkauf",
                checked = false, checkedChangedAt = 0, manual = true, sortOrder = 0, sources = emptyList())))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        for ((id, wo) in listOf("reis" to "Curry", "vorrat" to "Vorrat", "einkauf" to "Wocheneinkauf")) {
            val html = c.post(cookie, "/zutat/$id/loeschen").bodyAsText()
            assertTrue("Wird noch verwendet in" in html && wo in html, "$id: ${html.take(1200)}")
        }
        assertEquals(4, roh(env, h).size, "nichts gelöscht")
        assertEquals("/zutaten", c.post(cookie, "/zutat/frei/loeschen").headers[HttpHeaders.Location])
        assertTrue("frei" !in roh(env, h))
    }

    @Test fun ungueltigeZahlen() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val html = c.speichern(cookie, "name" to "Quark", "basis" to "PER_100_G", "kj" to "abc", "dichte" to "-1").bodyAsText()
        assertTrue("Energie: bitte eine Zahl ≥ 0" in html && "Dichte: bitte eine Zahl ≥ 0" in html, html.take(1500))
        assertTrue("value=\"abc\"" in html, "Eingabe bleibt")
        assertTrue(c.speichern(cookie, "name" to " ").bodyAsText().contains("Bitte einen Namen eingeben."))
        assertTrue(roh(env, h).isEmpty())
    }

    @Test fun fremdeIdsSind404UndCsrf403() = webServer { env ->
        val (benutzer, _, h) = env.haushalt("Meins")
        val (_, _, fremd) = env.haushalt("Fremd")
        env.push(fremd, datensatz("fz", RecordType.INGREDIENT, IngredientPayload(name = "Geheimzutat")))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        assertEquals(HttpStatusCode.NotFound, c.get("/zutat/fz") { header(HttpHeaders.Cookie, cookie) }.status)
        assertEquals(HttpStatusCode.NotFound, c.speichern(cookie, "id" to "fz", "name" to "Gekapert").status)
        assertEquals(HttpStatusCode.NotFound, c.post(cookie, "/zutat/fz/loeschen").status)
        assertEquals(HttpStatusCode.Forbidden, c.speichern(cookie, "name" to "Neu", csrf = false).status)
        assertEquals(HttpStatusCode.Forbidden, c.post(cookie, "/zutat/fz/loeschen", csrf = false).status)
        assertEquals("Geheimzutat", roh(env, fremd).getValue("fz").payload.name)
        assertTrue(roh(env, h).isEmpty())
    }

    @Test fun dreihundertZutatenUnter30KB() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, *(1..300).map {
            datensatz(UUID.randomUUID().toString(), RecordType.INGREDIENT, IngredientPayload(name = "Zutat Nummer $it", category = "Vorrat", basis = "PER_100_G", energyKj = "1000"))
        }.toTypedArray())
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val bytes = c.get("/zutaten") { header(HttpHeaders.Cookie, cookie) }.bodyAsBytes().size
        assertTrue(bytes < 30_000, "$bytes Bytes")
        val suche = c.get("/zutaten/liste?q=nummer 12") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("Zutat Nummer 12" in suche && "Zutat Nummer 7<" !in suche && "<html" !in suche)
    }
}

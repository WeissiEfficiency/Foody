package de.foody.server.web

import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.RecipePayload
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.ParametersBuilder
import io.ktor.http.parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorSeitenTest {
    private fun ParametersBuilder.rezeptFelder(name: String = "Linsensuppe", rev: String = "", id: String = "") {
        append("id", id); append("rev", rev); append("name", name); append("portionen", "4"); append("vorbereitung", ""); append("kochzeit", "40")
        append("tags", "Suppe"); append("quelle", ""); append("notizen", "")
        append("schritte", "Linsen kochen"); append("einordnung", "vermuten")
        append("zeile_menge", "250"); append("zeile_einheit", "GRAM"); append("zeile_zutat", "Linsen"); append("zeile_hinweis", ""); append("zeile_optional", "0")
    }

    private suspend fun HttpClient.speichern(cookie: String, felder: ParametersBuilder.() -> Unit): HttpResponse {
        val token = csrf(cookie)
        return submitForm("/rezept/speichern", parameters { append("_csrf", token); felder() }) { header(HttpHeaders.Cookie, cookie) }
    }

    @Test fun neuAnlegenUeberFormular() = webServer { env ->
        val (benutzer, _, _) = env.haushalt()
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        assertTrue("zeile_zutat" in c.get("/rezept/neu") { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
        val antwort = c.speichern(cookie) { rezeptFelder() }
        assertEquals(HttpStatusCode.SeeOther, antwort.status, antwort.bodyAsText().take(400))
        val ziel = antwort.headers[HttpHeaders.Location]!!
        assertTrue(ziel.startsWith("/rezept/"))
        assertTrue("Linsensuppe" in c.get(ziel) { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
        assertTrue("Linsensuppe" in c.get("/") { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
    }

    @Test fun bearbeitenZeigtWerte() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry", tags = "Abend"))
        val c = ohneWeiterleitung()
        val html = c.get("/rezept/r1/bearbeiten") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText()
        assertTrue("value=\"Curry\"" in html && "value=\"Reis\"" in html && "value=\"200\"" in html && "Reis kochen" in html)
    }

    @Test fun fehlerBehaeltEingaben() = webServer { env ->
        val (benutzer, _, _) = env.haushalt()
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val antwort = c.speichern(cookie) { rezeptFelder(name = "") }
        assertEquals(HttpStatusCode.OK, antwort.status)
        val html = antwort.bodyAsText()
        assertTrue("Bitte einen Namen eingeben." in html)
        assertTrue("value=\"Linsen\"" in html && "Linsen kochen" in html, "übrige Eingaben bleiben")
    }

    @Test fun konfliktSeiteMitZweiKnoepfen() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val html = c.speichern(cookie) { rezeptFelder(name = "Curry neu", id = "r1", rev = "0") }.bodyAsText()
        assertTrue("Inzwischen geändert (z. B. am Handy)" in html, html.take(500))
        assertTrue("Aktuellen Stand laden" in html && "Trotzdem speichern" in html)
        assertTrue("name=\"erzwingen\" value=\"1\"" in html || "value=\"1\" name=\"erzwingen\"" in html)
    }

    @Test fun zeileFragment() = webServer { env ->
        val (benutzer, _, _) = env.haushalt()
        val c = ohneWeiterleitung()
        val antwort = c.get("/rezept/zeile") { header(HttpHeaders.Cookie, c.anmelden(benutzer)); header("HX-Request", "true") }
        val html = antwort.bodyAsText()
        assertTrue("zeile_zutat" in html && "<html" !in html)
        assertTrue(antwort.bodyAsBytes().size < 2_000)
    }

    @Test fun neuePostsOhneCsrfSind403() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        for (pfad in listOf("/rezept/speichern", "/rezept/r1/archivieren", "/rezept/r1/wiederherstellen")) {
            assertEquals(HttpStatusCode.Forbidden, c.submitForm(pfad, parameters { rezeptFelder() }) { header(HttpHeaders.Cookie, cookie) }.status, pfad)
        }
    }

    @Test fun fremdesRezeptBearbeitenIst404() = webServer { env ->
        val (benutzer, _, _) = env.haushalt("Meins")
        val (_, _, fremd) = env.haushalt("Fremd")
        env.push(fremd, reis(), rezept("geheim", "Geheimrezept"))
        val c = ohneWeiterleitung()
        val antwort = c.get("/rezept/geheim/bearbeiten") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }
        assertEquals(HttpStatusCode.NotFound, antwort.status)
        assertFalse("Geheimrezept" in antwort.bodyAsText())
    }

    @Test fun archivFilter() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val token = c.csrf(cookie)
        val archiviert = c.submitForm("/rezept/r1/archivieren", parameters { append("_csrf", token) }) { header(HttpHeaders.Cookie, cookie) }
        assertEquals(HttpStatusCode.SeeOther, archiviert.status)
        assertFalse("Curry" in c.get("/") { header(HttpHeaders.Cookie, cookie) }.bodyAsText().substringAfter("id=\"liste\""))
        assertTrue("Curry" in c.get("/?archiv=1") { header(HttpHeaders.Cookie, cookie) }.bodyAsText().substringAfter("id=\"liste\""))
        c.submitForm("/rezept/r1/wiederherstellen", parameters { append("_csrf", token) }) { header(HttpHeaders.Cookie, cookie) }
        assertTrue("Curry" in c.get("/") { header(HttpHeaders.Cookie, cookie) }.bodyAsText().substringAfter("id=\"liste\""))
    }

    @Test fun editorUnter30KB() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val zeilen = (0 until 30).map { RecipePayload.Line("l$it", "reis", "100", "GRAM", it, "Hinweis $it", false) }
        env.push(h, reis(), datensatz("gross", RecordType.RECIPE, RecipePayload(name = "Großes Rezept", servings = 4, tags = "", favorite = false, lines = zeilen, steps = emptyList())))
        val c = ohneWeiterleitung()
        val bytes = c.get("/rezept/gross/bearbeiten") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsBytes().size
        assertTrue(bytes < 30_000, "$bytes Bytes")
    }
}

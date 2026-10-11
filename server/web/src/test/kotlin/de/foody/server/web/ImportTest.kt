package de.foody.server.web

import de.foody.domain.MarkdownRecipeImporter
import de.foody.domain.RezeptImport
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportTest {
    /** Chefkoch-Format wie in der Sammlung (Titel, Quelle, Zutaten im Wechsel Menge/Name, Zubereitung). */
    private val linsensuppe = """
        # Linsensuppe

        _Quelle: https://www.chefkoch.de/rezepte/1/Linsensuppe.html_

        ## Zutaten

        250 g

        Linsen

        1

        Zwiebel(n)

        1 Liter

        Gemüsebrühe

        ## Zubereitung

        Zwiebel würfeln und anschwitzen.

        Linsen und Brühe zugeben, 30 Minuten köcheln.
    """.trimIndent()

    private suspend fun HttpClient.importieren(cookie: String, token: String?, vararg dateien: Pair<String, ByteArray>): HttpResponse =
        submitFormWithBinaryData("/import", formData {
            if (token != null) append("_csrf", token)
            append("tag", "Import")
            for ((name, inhalt) in dateien) append("dateien", inhalt, Headers.build { append(HttpHeaders.ContentDisposition, "filename=\"$name\"") })
        }) { header(HttpHeaders.Cookie, cookie) }

    @Test fun importWieInDerApp() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val antwort = c.importieren(cookie, c.csrf(cookie), "linsensuppe.md" to linsensuppe.toByteArray())
        assertEquals(HttpStatusCode.OK, antwort.status, antwort.bodyAsText().take(300))
        assertTrue("Linsensuppe" in antwort.bodyAsText())
        val haushalt = HaushaltsDaten(env.deps.db).fuer(h)
        val r = haushalt.rezepte.values.single()
        val parsed = MarkdownRecipeImporter.parse(linsensuppe)
        val erwartet = RezeptImport.vorbereiten(parsed, parsed.ingredients.map { ing ->
            haushalt.zutaten.values.single { it.name == de.foody.domain.IngredientCatalog.canonicalName(ing.name) }
        }, 4)
        assertEquals(erwartet.portionen, r.payload.servings)
        assertEquals(erwartet.zeilen.map { it.zutatId to it.einheit.name }, r.payload.lines.map { it.ingredientId to it.unit })
        assertEquals("Import", r.payload.tags)
        assertTrue(r.payload.notes!!.startsWith("Quelle: https://www.chefkoch.de/rezepte/1/Linsensuppe.html"))
        assertEquals(2, r.payload.steps.size)
    }

    @Test fun doppelteQuelleUebersprungen() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        c.importieren(cookie, c.csrf(cookie), "a.md" to linsensuppe.toByteArray())
        val zweiter = c.importieren(cookie, c.csrf(cookie), "b.md" to linsensuppe.toByteArray()).bodyAsText()
        assertTrue("übersprungen" in zweiter.lowercase(), zweiter.take(500))
        assertEquals(1, HaushaltsDaten(env.deps.db).fuer(h).rezepte.size)
    }

    @Test fun textOhneRezeptIstNichtErkannt() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val html = c.importieren(cookie, c.csrf(cookie), "notiz.txt" to "Einkaufszettel: Milch, Brot".toByteArray()).bodyAsText()
        assertTrue("nicht erkannt" in html.lowercase())
        assertTrue(HaushaltsDaten(env.deps.db).fuer(h).rezepte.isEmpty())
    }

    @Test fun zuGrosseDateiAbgelehnt() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val html = c.importieren(cookie, c.csrf(cookie), "gut.md" to linsensuppe.toByteArray(), "riesig.md" to ByteArray(1_048_577) { 'a'.code.toByte() }).bodyAsText()
        assertTrue("größer als 1 MB" in html, html.take(400))
        assertTrue(HaushaltsDaten(env.deps.db).fuer(h).rezepte.isEmpty(), "nichts importiert")
    }

    @Test fun zuVieleDateienAbgelehnt() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val dateien = Array(51) { "r$it.md" to linsensuppe.replace("Linsensuppe", "Suppe $it").replace("/1/", "/$it/").toByteArray() }
        val html = c.importieren(cookie, c.csrf(cookie), *dateien).bodyAsText()
        assertTrue("Höchstens 50 Dateien" in html)
        assertTrue(HaushaltsDaten(env.deps.db).fuer(h).rezepte.isEmpty())
    }

    @Test fun importOhneCsrfIst403() = webServer { env ->
        val (benutzer, _, _) = env.haushalt()
        val c = ohneWeiterleitung()
        val antwort = c.importieren(c.anmelden(benutzer), null, "a.md" to linsensuppe.toByteArray())
        assertEquals(HttpStatusCode.Forbidden, antwort.status)
    }
}

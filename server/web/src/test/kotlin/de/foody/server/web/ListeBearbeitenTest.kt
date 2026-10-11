package de.foody.server.web

import de.foody.domain.ShoppingCatalog
import de.foody.server.sync.RecordStore
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.ShoppingItemPayload
import de.foody.sync.protocol.ShoppingListPayload
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
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListeBearbeitenTest {
    private fun liste(id: String = "L", name: String = "Wocheneinkauf") = datensatz(id, RecordType.SHOPPING_LIST, ShoppingListPayload(name, version = 1))

    private fun artikel(id: String, name: String, liste: String = "L", menge: String? = null, zutat: String? = null, notiz: String? = null,
                        erledigt: Boolean = false, quellen: List<ShoppingItemPayload.Source> = emptyList()) =
        datensatz(id, RecordType.SHOPPING_ITEM, ShoppingItemPayload(
            listId = liste, ingredientId = zutat, name = name, amount = menge, unit = menge?.let { "GRAM" }, checked = erledigt,
            checkedChangedAt = 0, manual = true, sortOrder = 0, note = notiz, sources = quellen,
        ))

    private suspend fun HttpClient.post(cookie: String, pfad: String, vararg felder: Pair<String, String>, csrf: Boolean = true): HttpResponse {
        val token = if (csrf) csrf(cookie) else null
        return submitForm(pfad, parameters {
            token?.let { append("_csrf", it) }
            for ((k, v) in felder) append(k, v)
        }) { header(HttpHeaders.Cookie, cookie) }
    }

    private fun eintraege(env: WebUmgebung, h: String) = HaushaltsDaten(env.deps.db).fuer(h).eintraege

    @Test fun freitextDreiFaelle() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, liste())
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val antwort = c.post(cookie, "/einkauf/eingabe", "liste" to "L", "text" to "milch")
        assertEquals("/einkauf?liste=L", antwort.headers[HttpHeaders.Location])
        val neu = eintraege(env, h).single()
        assertEquals("Milch", neu.payload.name)
        assertEquals(ShoppingCatalog.sectionFor(null, "Milch"), neu.payload.category)
        assertTrue(neu.payload.manual && !neu.payload.checked)
        c.post(cookie, "/einkauf/eingabe", "liste" to "L", "text" to "Milch")
        assertTrue(eintraege(env, h).single().payload.checked, "offen → gekauft")
        c.post(cookie, "/einkauf/eingabe", "liste" to "L", "text" to "Milch")
        assertFalse(eintraege(env, h).single().payload.checked, "gekauft → wieder auf die Liste")
        c.post(cookie, "/einkauf/eingabe", "liste" to "L", "text" to "Grillkohle XL")
        assertEquals(ShoppingCatalog.OWN_ITEMS, eintraege(env, h).single { it.payload.name == "Grillkohle XL" }.payload.category)
    }

    @Test fun freitextOhneListeLegtListeAn() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        assertTrue("name=\"text\"" in c.get("/einkauf") { header(HttpHeaders.Cookie, cookie) }.bodyAsText(), "Eingabe auch ohne Liste")
        val antwort = c.post(cookie, "/einkauf/eingabe", "liste" to "", "text" to "Brot")
        val l = HaushaltsDaten(env.deps.db).fuer(h).listen.single()
        assertEquals("Einkauf", l.name)
        assertEquals("/einkauf?liste=${l.id}", antwort.headers[HttpHeaders.Location])
        assertEquals("Brot", eintraege(env, h).single().payload.name)
    }

    @Test fun loeschenUndNotiz() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, liste(), artikel("e1", "Milch"), artikel("e2", "Brot", notiz = "alt"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val antwort = c.post(cookie, "/einkauf/L/bearbeiten", "n_e1" to "Bio", "n_e2" to "")
        assertEquals("/einkauf?liste=L&bearbeiten=1", antwort.headers[HttpHeaders.Location])
        assertEquals("Bio", eintraege(env, h).single { it.id == "e1" }.payload.note)
        assertNull(eintraege(env, h).single { it.id == "e2" }.payload.note)
        val zuLang = c.post(cookie, "/einkauf/L/bearbeiten", "n_e1" to "x".repeat(1001), "weg" to "e2")
        assertTrue("Notiz höchstens 1000 Zeichen." in zuLang.bodyAsText())
        assertEquals(2, eintraege(env, h).size, "bei Fehler nichts geschrieben")
        c.post(cookie, "/einkauf/L/bearbeiten", "n_e1" to "Bio", "n_e2" to "", "weg" to "e1")
        assertEquals(listOf("e2"), eintraege(env, h).map { it.id })
    }

    /** Final-Review 3: Notiz, die das Handy inzwischen geändert hat, wird durch ein altes Formular nicht überschrieben. */
    @Test fun notizVomHandyBleibtBeiAltemFormular() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, liste(), artikel("e1", "Milch"), artikel("e2", "Brot"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val seite = c.get("/einkauf?liste=L&bearbeiten=1") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("name=\"o_e1\"" in seite, "ursprünglicher Wert wird mitgeschickt")
        // Handy setzt inzwischen eine Notiz
        val rev = env.deps.db.tx { cn -> RecordStore().get(cn, h, RecordType.SHOPPING_ITEM, "e1")!!.rev }
        env.push(h, artikel("e1", "Milch", notiz = "2 Liter").copy(baseRev = rev, updatedAt = 50))
        c.post(cookie, "/einkauf/L/bearbeiten", "n_e1" to "", "o_e1" to "", "n_e2" to "", "o_e2" to "", "weg" to "e2")
        assertEquals("2 Liter", eintraege(env, h).single().payload.note)
    }

    @Test fun listeLoeschenMitBestaetigung() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, liste(), artikel("e1", "Milch"), artikel("e2", "Brot"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val bestaetigung = c.get("/einkauf/L/loeschen") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("Wocheneinkauf" in bestaetigung && "action=\"/einkauf/L/loeschen\"" in bestaetigung)
        assertEquals(1, HaushaltsDaten(env.deps.db).fuer(h).listen.size, "GET löscht nichts")
        val antwort = c.post(cookie, "/einkauf/L/loeschen")
        assertEquals("/einkauf", antwort.headers[HttpHeaders.Location])
        assertTrue(HaushaltsDaten(env.deps.db).fuer(h).listen.isEmpty())
        assertTrue(eintraege(env, h).isEmpty())
        val geloescht = env.deps.db.tx { cn -> listOf("e1", "e2").map { RecordStore().get(cn, h, RecordType.SHOPPING_ITEM, it)!!.deleted } }
        assertEquals(listOf(true, true), geloescht, "Einträge als Löschmarkierungen")
    }

    @Test fun rezeptAufListeAddiert() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), liste(), artikel("e1", "Reis", menge = "500", zutat = "reis"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        assertTrue("action=\"/einkauf/rezept\"" in c.get("/rezept/r1") { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
        val antwort = c.post(cookie, "/einkauf/rezept", "rezept" to "r1", "portionen" to "2", "liste" to "L")
        val ziel = antwort.headers[HttpHeaders.Location]!!
        assertEquals("/einkauf?liste=L&hinzu=1", ziel)
        assertEquals(0, BigDecimal(eintraege(env, h).single().payload.amount!!).compareTo(BigDecimal(700)))
        assertTrue("1 Zutat hinzugefügt" in c.get(ziel) { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
    }

    @Test fun rezeptAufListeOhneListe() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        c.post(c.anmelden(benutzer), "/einkauf/rezept", "rezept" to "r1", "portionen" to "4", "liste" to "")
        val e = eintraege(env, h).single()
        assertEquals("Einkauf", HaushaltsDaten(env.deps.db).fuer(h).listen.single().name)
        assertEquals(0, BigDecimal(e.payload.amount!!).compareTo(BigDecimal(400)))
        assertTrue(e.payload.manual && e.payload.ingredientId == "reis")
    }

    /** Review Focus 4. */
    @Test fun sonderzeichenEscaped() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, liste())
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        c.post(cookie, "/einkauf/eingabe", "liste" to "L", "text" to "<b>Brot</b>")
        val html = c.get("/einkauf?liste=L") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("&lt;b&gt;Brot&lt;/b&gt;" in html && "<b>Brot</b>" !in html)
        val zuLang = c.post(cookie, "/einkauf/eingabe", "liste" to "L", "text" to "x".repeat(201))
        assertTrue("Höchstens 200 Zeichen." in zuLang.bodyAsText())
        assertEquals(1, eintraege(env, h).size)
    }

    @Test fun groessen() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val quelle = { ShoppingItemPayload.Source(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), "Gemüsecurry mit Reis", "2026-10-12", "200", "GRAM") }
        val artikel = (1..75).map { artikel(UUID.randomUUID().toString(), "Zutat Nummer $it", menge = "250", notiz = "Notiz $it", quellen = listOf(quelle())) }
        env.push(h, liste(), *artikel.toTypedArray())
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val normal = c.get("/einkauf?liste=L") { header(HttpHeaders.Cookie, cookie) }.bodyAsBytes().size
        val bearbeiten = c.get("/einkauf?liste=L&bearbeiten=1") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue(normal < 30_000, "Einkaufsansicht: $normal Bytes")
        assertTrue(bearbeiten.toByteArray().size < 50_000, "Bearbeiten: ${bearbeiten.toByteArray().size} Bytes")
        assertTrue("Gemüsecurry mit Reis, Mo. 12.10." in bearbeiten, "Herkunft")
    }

    @Test fun abhakenUeberSeitenformular() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, liste(), artikel("e1", "Milch"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val html = c.get("/einkauf?liste=L") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        val wert = assertNotNull(Regex("value=\"(e1 1 [0-9]+)\"").find(html)?.groupValues?.get(1), html.take(3000))
        val token = c.csrf(cookie)
        val fragment = c.submitForm("/einkauf/abhaken", parameters { append("_csrf", token); append("a", wert) }) {
            header(HttpHeaders.Cookie, cookie); header("HX-Request", "true")
        }.bodyAsText()
        assertTrue(fragment.startsWith("<li") && "e1 0 " in fragment, fragment)
        assertTrue(eintraege(env, h).single().payload.checked)
    }

    @Test fun fremdeIdsSind404UndCsrf403() = webServer { env ->
        val (benutzer, _, h) = env.haushalt("Meins")
        val (_, _, fremd) = env.haushalt("Fremd")
        env.push(fremd, reis(), rezept("geheim", "Geheim"), liste("fl", "Fremd"), artikel("fe", "Fremdes", liste = "fl"))
        env.push(h, reis(), rezept("r1", "Curry"), liste())
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val faelle = listOf(
            "/einkauf/eingabe" to arrayOf("liste" to "fl", "text" to "Milch"),
            "/einkauf/fl/bearbeiten" to arrayOf("n_fe" to "x", "weg" to "fe"),
            "/einkauf/L/bearbeiten" to arrayOf("weg" to "fe"),
            "/einkauf/fl/loeschen" to arrayOf(),
            "/einkauf/rezept" to arrayOf("rezept" to "geheim", "portionen" to "2", "liste" to "L"),
            "/einkauf/rezept" to arrayOf("rezept" to "r1", "portionen" to "2", "liste" to "fl"),
            "/einkauf/abhaken" to arrayOf("a" to "fe 1 1"),
        )
        for ((pfad, felder) in faelle) {
            assertEquals(HttpStatusCode.NotFound, c.post(cookie, pfad, *felder).status, "$pfad ${felder.toList()}")
            assertEquals(HttpStatusCode.Forbidden, c.post(cookie, pfad, *felder, csrf = false).status, "$pfad ohne CSRF")
        }
        assertEquals(404, c.get("/einkauf/fl/loeschen") { header(HttpHeaders.Cookie, cookie) }.status.value)
        val d = HaushaltsDaten(env.deps.db).fuer(fremd)
        assertEquals(1, d.listen.size)
        assertEquals("Fremdes", d.eintraege.single().payload.name)
        assertFalse(d.eintraege.single().payload.checked)
        assertNull(d.eintraege.single().payload.note)
        assertTrue(eintraege(env, h).isEmpty())
    }

    @Test fun inzwischenGeloeschtGibtHinweis() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, liste(), artikel("e1", "Milch"), artikel("e2", "Brot"))
        env.push(h, SyncRecord(id = "e1", type = RecordType.SHOPPING_ITEM, deleted = true, updatedAt = 9))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val antwort = c.post(cookie, "/einkauf/L/bearbeiten", "weg" to "e1")
        val ziel = antwort.headers[HttpHeaders.Location]!!
        assertTrue("hinweis=weg" in ziel, ziel)
        assertTrue("Eintrag gibt es nicht mehr" in c.get(ziel) { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
        assertEquals(listOf("e2"), eintraege(env, h).map { it.id })
    }
}

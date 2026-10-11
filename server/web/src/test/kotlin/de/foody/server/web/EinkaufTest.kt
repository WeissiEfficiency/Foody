package de.foody.server.web

import de.foody.server.sync.RecordStore
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.ShoppingItemPayload
import de.foody.sync.protocol.ShoppingListPayload
import de.foody.sync.protocol.SyncRecord
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

class EinkaufTest {
    private fun liste(id: String, name: String, updatedAt: Long) =
        datensatz(id, RecordType.SHOPPING_LIST, ShoppingListPayload(name, version = 1), updatedAt)

    private fun artikel(id: String, liste: String, name: String, kategorie: String?, erledigt: Boolean = false, am: Long = 0) =
        datensatz(id, RecordType.SHOPPING_ITEM, ShoppingItemPayload(
            listId = liste, name = name, checked = erledigt, checkedChangedAt = am, manual = true, category = kategorie,
            sortOrder = 0, sources = emptyList(),
        ))

    private fun gespeichert(env: WebUmgebung, h: String, id: String): ShoppingItemPayload {
        val r = env.deps.db.tx { c -> RecordStore().get(c, h, RecordType.SHOPPING_ITEM, id)!! }
        return Protocol.json.decodeFromJsonElement(ShoppingItemPayload.serializer(), r.payload!!)
    }

    private fun rev(env: WebUmgebung, h: String, id: String) = env.deps.db.tx { c -> RecordStore().get(c, h, RecordType.SHOPPING_ITEM, id)!!.rev!! }

    @Test fun neuesteListeNachAbteilung() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, liste("alt", "Alte Liste", 1), liste("neu", "Wocheneinkauf", 50),
            artikel("m", "neu", "Milch", "Milch & Käse"), artikel("a", "neu", "Äpfel", "Obst & Gemüse", erledigt = true),
            artikel("x", "alt", "Zahnpasta", null))
        val c = ohneWeiterleitung()
        val html = c.get("/einkauf") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText()
        assertTrue("Wocheneinkauf" in html && "Milch" in html && "Zahnpasta" !in html, "neueste Liste")
        assertTrue(html.indexOf("Milch") < html.indexOf("Äpfel"), "offen vor erledigt")
    }

    @Test fun abhakenErzeugtNeueRevision() = webServer { env ->
        val (benutzer, appToken, h) = env.haushalt()
        env.push(h, liste("L", "Woche", 1), artikel("m", "L", "Milch", null))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val vorher = rev(env, h, "m")
        val token = c.csrf(cookie)
        val antwort = c.submitForm("/einkauf/m/abhaken", parameters {
            append("_csrf", token); append("erledigt", "1"); append("rev", vorher.toString())
        }) { header(HttpHeaders.Cookie, cookie); header("HX-Request", "true") }
        assertEquals(HttpStatusCode.OK, antwort.status)
        assertTrue("erledigt" in antwort.bodyAsText(), "Zeile kommt als erledigt zurück")
        assertTrue(rev(env, h, "m") > vorher)
        // Die App sieht es beim nächsten Abgleich
        val pull = c.get("/api/v1/sync/pull?since=$vorher&limit=10") {
            header(HttpHeaders.Authorization, "Bearer $appToken"); header(Protocol.HEADER, Protocol.VERSION.toString())
        }.bodyAsText()
        assertTrue(""""checked":true""" in pull, pull)
    }

    @Test fun abhakenOhneJavascriptLeitetZurueck() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, liste("L", "Woche", 1), artikel("m", "L", "Milch", null))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val token = c.csrf(cookie)
        val antwort = c.submitForm("/einkauf/m/abhaken", parameters {
            append("_csrf", token); append("erledigt", "1"); append("rev", rev(env, h, "m").toString())
        }) { header(HttpHeaders.Cookie, cookie) }
        assertEquals(HttpStatusCode.SeeOther, antwort.status)
        assertEquals("/einkauf?liste=L", antwort.headers[HttpHeaders.Location])
        assertTrue(gespeichert(env, h, "m").checked)
    }

    @Test fun gleichzeitigesAbhakenWirdZusammengefuehrt() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, liste("L", "Woche", 1), artikel("m", "L", "Milch", null))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val gesehen = rev(env, h, "m")
        // Das Handy hakt danach ab – mit späterem Zeitstempel als die Aktion im Browser
        val spaeter = env.uhr.millis() + 60_000
        val handy = gespeichert(env, h, "m").copy(checked = true, checkedChangedAt = spaeter)
        env.push(h, SyncRecord(id = "m", type = RecordType.SHOPPING_ITEM, updatedAt = spaeter, baseRev = gesehen,
            payload = Protocol.json.encodeToJsonElement(handy) as JsonObject))
        // Browser mit veralteter Seite will „nicht erledigt“
        val token = c.csrf(cookie)
        val antwort = c.submitForm("/einkauf/m/abhaken", parameters {
            append("_csrf", token); append("erledigt", "0"); append("rev", gesehen.toString())
        }) { header(HttpHeaders.Cookie, cookie); header("HX-Request", "true") }
        assertEquals(HttpStatusCode.OK, antwort.status)
        assertTrue(gespeichert(env, h, "m").checked, "späteres Abhaken vom Handy gewinnt")
        assertTrue("erledigt" in antwort.bodyAsText(), "Zeile zeigt den zusammengeführten Stand")
    }

    @Test fun abhakenOhneCsrfIst403() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, liste("L", "Woche", 1), artikel("m", "L", "Milch", null))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val antwort = c.submitForm("/einkauf/m/abhaken", parameters { append("erledigt", "1"); append("rev", "1") }) {
            header(HttpHeaders.Cookie, cookie)
        }
        assertEquals(HttpStatusCode.Forbidden, antwort.status)
        assertFalse(gespeichert(env, h, "m").checked)
    }

    @Test fun leererHaushaltOhneListe() = webServer { env ->
        val (benutzer, _, _) = env.haushalt()
        val c = ohneWeiterleitung()
        assertTrue("Keine Einkaufsliste" in c.get("/einkauf") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText())
    }

    /** Fremde Eintrags-ID: 404, und der Eintrag des anderen Haushalts bleibt unverändert. */
    @Test fun eintragAusFremdemHaushaltIst404() = webServer { env ->
        val (benutzer, _, _) = env.haushalt("Meins")
        val (_, _, fremd) = env.haushalt("Fremd")
        env.push(fremd, liste("L", "Fremde Liste", 1), artikel("m", "L", "Milch", null))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val token = c.csrf(cookie)
        val antwort = c.submitForm("/einkauf/m/abhaken", parameters { append("_csrf", token); append("erledigt", "1"); append("rev", "1") }) {
            header(HttpHeaders.Cookie, cookie)
        }
        assertEquals(HttpStatusCode.NotFound, antwort.status)
        assertFalse(gespeichert(env, fremd, "m").checked)
    }

    /** Abhaken behält Felder, die nur eine neuere App kennt. */
    @Test fun abhakenBehaeltUnbekannteFelder() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val mitZukunft = artikel("m", "L", "Milch", null).let { r ->
            r.copy(payload = kotlinx.serialization.json.JsonObject(r.payload!! + ("zukunft" to kotlinx.serialization.json.JsonPrimitive(1))))
        }
        env.push(h, liste("L", "Woche", 1), mitZukunft)
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val token = c.csrf(cookie)
        c.submitForm("/einkauf/m/abhaken", parameters { append("_csrf", token); append("erledigt", "1"); append("rev", rev(env, h, "m").toString()) }) {
            header(HttpHeaders.Cookie, cookie)
        }
        val roh = env.deps.db.tx { cn -> RecordStore().get(cn, h, RecordType.SHOPPING_ITEM, "m")!!.payload!! }
        assertEquals("1", roh["zukunft"].toString())
        assertEquals("true", roh["checked"].toString())
    }
}

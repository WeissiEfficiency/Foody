package de.foody.server.web

import de.foody.sync.protocol.MealSlotPayload
import de.foody.sync.protocol.RecordType
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Kleinigkeiten 1: Doppelklick (gleiche Vorgangsnummer zweimal) legt nichts doppelt an. Heute = 11.10.2026. */
class VorgangTest {
    private suspend fun HttpClient.zweimal(cookie: String, pfad: String, vararg felder: Pair<String, String>): Pair<HttpResponse, HttpResponse> {
        val token = csrf(cookie)
        suspend fun einmal() = submitForm(pfad, parameters {
            append("_csrf", token); append("vorgang", "vorgang-1")
            for ((k, v) in felder) append(k, v)
        }) { header(HttpHeaders.Cookie, cookie) }
        return einmal() to einmal()
    }

    private fun daten(env: WebUmgebung, h: String) = HaushaltsDaten(env.deps.db).fuer(h)

    private fun gleicheWeiterleitung(paar: Pair<HttpResponse, HttpResponse>) {
        assertEquals(HttpStatusCode.SeeOther, paar.first.status)
        assertEquals(HttpStatusCode.SeeOther, paar.second.status)
        assertEquals(paar.first.headers[HttpHeaders.Location]!!.substringBefore('#'), paar.second.headers[HttpHeaders.Location]!!.substringBefore('#'))
    }

    @Test fun formulareTragenVorgangsnummer() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), datensatz("s1", RecordType.MEAL_SLOT, MealSlotPayload("2026-10-12", "ABENDESSEN", "r1", 2)))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        for (pfad in listOf("/rezept/r1", "/plan/neu?tag=2026-10-12", "/einkauf/neu?von=2026-10-11&bis=2026-10-17", "/vorrat", "/tagebuch/neu?tag=2026-10-11&mahlzeit=SNACK")) {
            val html = c.get(pfad) { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
            assertTrue("name=\"vorgang\"" in html, pfad)
        }
    }

    @Test fun einplanenEinmal() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        gleicheWeiterleitung(c.zweimal(c.anmelden(benutzer), "/plan/einplanen", "tag" to "2026-10-12", "mahlzeit" to "ABENDESSEN", "rezept" to "r1", "portionen" to "2"))
        assertEquals(1, daten(env, h).plan.size)
    }

    @Test fun vorschlagEinmal() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        gleicheWeiterleitung(c.zweimal(c.anmelden(benutzer), "/plan/vorschlag", "mahlzeit" to "ABENDESSEN", "ab" to "2026-10-12", "tag_0" to "2026-10-13", "rezept_0" to "r1"))
        assertEquals(1, daten(env, h).plan.size)
    }

    @Test fun listeEinmal() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), datensatz("s1", RecordType.MEAL_SLOT, MealSlotPayload("2026-10-12", "ABENDESSEN", "r1", 2)))
        val c = ohneWeiterleitung()
        val (erste, zweite) = c.zweimal(c.anmelden(benutzer), "/einkauf/neu", "name" to "Einkauf", "von" to "2026-10-12", "bis" to "2026-10-18", "vorrat" to "1", "mit" to "reis")
        assertEquals(HttpStatusCode.SeeOther, erste.status)
        // Zweites Absenden: zur Einkaufsseite, die ohnehin die neueste (eben angelegte) Liste zeigt
        assertEquals("/einkauf", zweite.headers[HttpHeaders.Location])
        assertEquals(1, daten(env, h).listen.size)
    }

    @Test fun vorratEinmal() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis())
        val c = ohneWeiterleitung()
        gleicheWeiterleitung(c.zweimal(c.anmelden(benutzer), "/vorrat", "zutat" to "Reis", "menge" to "1", "einheit" to "kg", "mhd" to ""))
        assertEquals(1, daten(env, h).vorrat.size)
    }

    @Test fun tagebuchEinmal() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        gleicheWeiterleitung(c.zweimal(cookie, "/tagebuch/rezept", "tag" to "2026-10-11", "mahlzeit" to "SNACK", "rezept" to "r1", "portionen" to "1"))
        gleicheWeiterleitung(c.zweimal(cookie, "/tagebuch/frei", "tag" to "2026-10-11", "mahlzeit" to "SNACK", "name" to "Apfel", "kcal" to "50"))
        assertEquals(2, daten(env, h).tagebuch.size, "je ein Eintrag; gleiche Nummer gilt je Haushalt, aber der zweite Weg hat eine eigene")
    }

    @Test fun fehlerVerbrauchtDieNummerNicht() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis())
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val token = c.csrf(cookie)
        val falsch = c.submitForm("/vorrat", parameters {
            append("_csrf", token); append("vorgang", "v-2"); append("zutat", "Reis"); append("menge", "abc"); append("einheit", "g"); append("mhd", "")
        }) { header(HttpHeaders.Cookie, cookie) }
        assertEquals(HttpStatusCode.OK, falsch.status)
        val richtig = c.submitForm("/vorrat", parameters {
            append("_csrf", token); append("vorgang", "v-2"); append("zutat", "Reis"); append("menge", "1"); append("einheit", "g"); append("mhd", "")
        }) { header(HttpHeaders.Cookie, cookie) }
        assertEquals(HttpStatusCode.SeeOther, richtig.status)
        assertEquals(1, daten(env, h).vorrat.size, "korrigiertes Formular mit derselben Nummer wird gespeichert")
    }
}

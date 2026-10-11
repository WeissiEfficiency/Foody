package de.foody.server.web

import de.foody.domain.MeasureUnit
import de.foody.domain.Tagebuch
import de.foody.domain.TagebuchArt
import de.foody.sync.protocol.IngredientPayload
import de.foody.sync.protocol.MealSlotPayload
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import de.foody.sync.protocol.TagebuchPayload
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async

/** Heute ist laut Testuhr der 11.10.2026. Reis: 1464 kJ/100 g; „Curry“ = 200 g Reis für 2 Portionen = 1464 kJ je Portion. */
class TagebuchSchreibenTest {
    private val heute = LocalDate.of(2026, 10, 11)

    private fun slot(id: String, rezept: String = "r1", mahlzeit: String = "MITTAGESSEN") =
        datensatz(id, RecordType.MEAL_SLOT, MealSlotPayload(heute.toString(), mahlzeit, rezept, 2))

    private fun eintrag(id: String, art: String = "REZEPT", portionen: String? = "1", menge: String? = null, kj: String = "1000") =
        datensatz(id, RecordType.TAGEBUCH_EINTRAG, TagebuchPayload(
            datum = heute.toString(), mahlzeit = "ABENDESSEN", art = art, name = "Eintrag $id", rezeptId = if (art == "REZEPT") "r1" else null,
            portionen = portionen, zutatId = if (art == "ZUTAT") "reis" else null, menge = menge, einheit = if (art == "ZUTAT") "GRAM" else null,
            energieKj = kj, eiweiss = "10",
        ))

    private suspend fun HttpClient.post(cookie: String, pfad: String, vararg felder: Pair<String, String>, csrf: Boolean = true): HttpResponse {
        val token = if (csrf) csrf(cookie) else null
        return submitForm(pfad, parameters {
            token?.let { append("_csrf", it) }
            for ((k, v) in felder) append(k, v)
        }) { header(HttpHeaders.Cookie, cookie) }
    }

    private fun tagebuch(env: WebUmgebung, h: String) = HaushaltsDaten(env.deps.db).fuer(h).tagebuch

    @Test fun gegessenEinmal() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), slot("p1"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val seite = c.get("/tagebuch") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("Geplant, noch nicht eingetragen" in seite && "value=\"p1\"" in seite, seite.take(2000))
        val antwort = c.post(cookie, "/tagebuch/gegessen", "plan" to "p1")
        assertEquals("/tagebuch?tag=2026-10-11", antwort.headers[HttpHeaders.Location])
        c.post(cookie, "/tagebuch/gegessen", "plan" to "p1")
        val e = tagebuch(env, h).single()
        assertEquals("p1", e.planEintragId)
        assertEquals(TagebuchArt.REZEPT, e.art)
        assertEquals(0, e.portionen!!.compareTo(BigDecimal.ONE))
        val d = HaushaltsDaten(env.deps.db).fuer(h)
        assertEquals(0, Tagebuch.naehrwerteRezept(d.rezepte.getValue("r1").domain, d.zutaten, BigDecimal.ONE).energieKj!!.compareTo(e.werte.energieKj))
        assertTrue("value=\"p1\"" !in c.get("/tagebuch") { header(HttpHeaders.Cookie, cookie) }.bodyAsText(), "Vorschlag weg")
    }

    @Test fun gegessenGleichzeitig() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val token = c.csrf(cookie)
        for (runde in 1..3) {
            env.push(h, slot("p$runde"))
            kotlinx.coroutines.coroutineScope {
                (1..8).map {
                    async(kotlinx.coroutines.Dispatchers.IO) {
                        c.submitForm("/tagebuch/gegessen", parameters { append("_csrf", token); append("plan", "p$runde") }) { header(HttpHeaders.Cookie, cookie) }
                    }
                }.forEach { it.await() }
            }
            assertEquals(runde, tagebuch(env, h).size, "Runde $runde")
        }
    }

    /** Review Focus 5. */
    @Test fun gegessenOhneRezept() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), slot("p1"))
        env.push(h, SyncRecord(id = "r1", type = RecordType.RECIPE, deleted = true, updatedAt = 9))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        assertTrue("value=\"p1\"" !in c.get("/tagebuch") { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, c.post(cookie, "/tagebuch/gegessen", "plan" to "p1").status)
        assertTrue(tagebuch(env, h).isEmpty())
    }

    @Test fun rezeptZutatFreiEintragen() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        assertTrue("Curry" in c.get("/tagebuch/neu?tag=2026-10-11&mahlzeit=ABENDESSEN") { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
        c.post(cookie, "/tagebuch/rezept", "tag" to "2026-10-11", "mahlzeit" to "ABENDESSEN", "rezept" to "r1", "portionen" to "1,5")
        c.post(cookie, "/tagebuch/zutat", "tag" to "2026-10-11", "mahlzeit" to "SNACK", "zutat" to "Reis", "menge" to "50", "einheit" to "g")
        c.post(cookie, "/tagebuch/frei", "tag" to "2026-10-11", "mahlzeit" to "FRUEHSTUECK", "name" to "Croissant", "kcal" to "250", "eiweiss" to "5", "kh" to "", "fett" to "")
        val d = HaushaltsDaten(env.deps.db).fuer(h)
        val rezept = d.tagebuch.single { it.art == TagebuchArt.REZEPT }
        assertEquals(0, rezept.werte.energieKj!!.compareTo(Tagebuch.naehrwerteRezept(d.rezepte.getValue("r1").domain, d.zutaten, BigDecimal("1.5")).energieKj))
        val zutat = d.tagebuch.single { it.art == TagebuchArt.ZUTAT }
        assertEquals("reis", zutat.zutatId)
        assertEquals(MeasureUnit.GRAM, zutat.einheit)
        assertEquals(0, zutat.werte.energieKj!!.compareTo(BigDecimal("732")))
        val frei = d.tagebuch.single { it.art == TagebuchArt.FREI }
        assertEquals("Croissant", frei.name)
        assertEquals(250, frei.werte.kcal)
        assertEquals(0, frei.werte.eiweiss!!.compareTo(BigDecimal(5)))
        assertTrue("Croissant" in c.get("/tagebuch?tag=2026-10-11") { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
    }

    /** Review Focus 2. */
    @Test fun zutatNichtBerechenbar() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), datensatz("wasser", RecordType.INGREDIENT, IngredientPayload(name = "Mineralwasser")))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        for (felder in listOf(arrayOf("zutat" to "Mineralwasser", "menge" to "200", "einheit" to "ml"), arrayOf("zutat" to "Reis", "menge" to "1", "einheit" to "Stk."))) {
            val antwort = c.post(cookie, "/tagebuch/zutat", "tag" to "2026-10-11", "mahlzeit" to "SNACK", *felder)
            assertEquals(HttpStatusCode.OK, antwort.status)
            assertTrue("bitte frei eintragen" in antwort.bodyAsText(), felder.toList().toString())
        }
        val unbekannt = c.post(cookie, "/tagebuch/zutat", "tag" to "2026-10-11", "mahlzeit" to "SNACK", "zutat" to "Drachenfrucht", "menge" to "1", "einheit" to "g").bodyAsText()
        assertTrue("gibt es nicht im Zutatenkatalog" in unbekannt)
        assertTrue(tagebuch(env, h).isEmpty())
    }

    /** Review Focus 3: Werte bleiben festgehalten und werden skaliert, auch wenn das Rezept weg ist. */
    @Test fun aendernSkaliert() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), eintrag("t1", portionen = "1", kj = "1000"), eintrag("t2", art = "FREI", portionen = null, kj = "418.4"))
        env.push(h, SyncRecord(id = "r1", type = RecordType.RECIPE, deleted = true, updatedAt = 9))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        assertTrue("value=\"1\"" in c.get("/tagebuch/t1") { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
        val antwort = c.post(cookie, "/tagebuch/t1", "mahlzeit" to "MITTAGESSEN", "portionen" to "2")
        assertEquals("/tagebuch?tag=2026-10-11", antwort.headers[HttpHeaders.Location])
        val t1 = tagebuch(env, h).single { it.id == "t1" }
        assertEquals(0, t1.werte.energieKj!!.compareTo(BigDecimal(2000)))
        assertEquals(0, t1.werte.eiweiss!!.compareTo(BigDecimal(20)))
        assertEquals(de.foody.domain.Mahlzeit.MITTAGESSEN, t1.mahlzeit)
        c.post(cookie, "/tagebuch/t2", "mahlzeit" to "ABENDESSEN", "name" to "Kuchen", "kcal" to "300", "eiweiss" to "", "kh" to "", "fett" to "")
        val t2 = tagebuch(env, h).single { it.id == "t2" }
        assertEquals("Kuchen", t2.name)
        assertEquals(300, t2.werte.kcal)
    }

    @Test fun loeschen() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), eintrag("t1"))
        val c = ohneWeiterleitung()
        assertEquals("/tagebuch?tag=2026-10-11", c.post(c.anmelden(benutzer), "/tagebuch/t1/loeschen").headers[HttpHeaders.Location])
        assertTrue(tagebuch(env, h).isEmpty())
    }

    @Test fun ungueltigeEingaben() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val basis = arrayOf("tag" to "2026-10-11", "mahlzeit" to "ABENDESSEN")
        for (portionen in listOf("0", "100", "abc")) {
            val html = c.post(cookie, "/tagebuch/rezept", *basis, "rezept" to "r1", "portionen" to portionen).bodyAsText()
            assertTrue("Portionen: mehr als 0 und höchstens 99." in html, portionen)
        }
        assertTrue("kcal: ganze Zahl von 0 bis 10 000." in c.post(cookie, "/tagebuch/frei", *basis, "name" to "X", "kcal" to "10001").bodyAsText())
        assertTrue("Bitte einen Namen eingeben." in c.post(cookie, "/tagebuch/frei", *basis, "name" to " ", "kcal" to "100").bodyAsText())
        assertTrue("Bitte eine Mahlzeit wählen." in c.post(cookie, "/tagebuch/frei", "tag" to "2026-10-11", "mahlzeit" to "BRUNCH", "name" to "X", "kcal" to "100").bodyAsText())
        assertTrue("Datum höchstens ein Jahr" in c.post(cookie, "/tagebuch/frei", "tag" to "2028-10-11", "mahlzeit" to "SNACK", "name" to "X", "kcal" to "100").bodyAsText())
        assertTrue(tagebuch(env, h).isEmpty())
    }

    @Test fun fremdeIdsSind404UndCsrf403() = webServer { env ->
        val (benutzer, _, h) = env.haushalt("Meins")
        val (_, _, fremd) = env.haushalt("Fremd")
        env.push(fremd, reis(), rezept("r1", "Geheim"), slot("fp"), eintrag("ft"))
        env.push(h, reis(), rezept("r2", "Curry"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        assertEquals(HttpStatusCode.NotFound, c.get("/tagebuch/ft") { header(HttpHeaders.Cookie, cookie) }.status)
        assertEquals(HttpStatusCode.NotFound, c.post(cookie, "/tagebuch/ft", "mahlzeit" to "SNACK", "portionen" to "3").status)
        assertEquals(HttpStatusCode.NotFound, c.post(cookie, "/tagebuch/ft/loeschen").status)
        assertEquals(HttpStatusCode.NotFound, c.post(cookie, "/tagebuch/gegessen", "plan" to "fp").status)
        assertEquals(HttpStatusCode.NotFound, c.post(cookie, "/tagebuch/rezept", "tag" to "2026-10-11", "mahlzeit" to "SNACK", "rezept" to "r1", "portionen" to "1").status)
        for (pfad in listOf("/tagebuch/frei", "/tagebuch/gegessen", "/tagebuch/ft/loeschen")) {
            assertEquals(HttpStatusCode.Forbidden, c.post(cookie, pfad, "tag" to "2026-10-11", "mahlzeit" to "SNACK", "name" to "X", "kcal" to "1", csrf = false).status, pfad)
        }
        assertEquals(1, tagebuch(env, fremd).size)
        assertEquals(0, tagebuch(env, fremd).single().portionen!!.compareTo(BigDecimal.ONE))
        assertTrue(tagebuch(env, h).isEmpty())
    }

    @Test fun inzwischenGeloeschtGibtHinweis() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), eintrag("t1"))
        env.push(h, SyncRecord(id = "t1", type = RecordType.TAGEBUCH_EINTRAG, deleted = true, updatedAt = 9))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val ziel = c.post(cookie, "/tagebuch/t1", "mahlzeit" to "SNACK", "portionen" to "2").headers[HttpHeaders.Location]!!
        assertTrue("hinweis=weg" in ziel, ziel)
        assertTrue("Eintrag gibt es nicht mehr" in c.get(ziel) { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
        assertTrue(tagebuch(env, h).isEmpty())
    }
}

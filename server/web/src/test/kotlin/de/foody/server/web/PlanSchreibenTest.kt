package de.foody.server.web

import de.foody.sync.protocol.MealSlotPayload
import de.foody.sync.protocol.PantryItemPayload
import de.foody.sync.protocol.Protocol
import de.foody.sync.protocol.RecipePayload
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
import kotlin.test.Test
import kotlinx.coroutines.async
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Heute ist laut Testuhr der 11.10.2026. */
class PlanSchreibenTest {
    private fun slot(id: String, datum: String, mahlzeit: String = "ABENDESSEN", rezept: String = "r1", portionen: Int = 2, gekocht: Long? = null) =
        datensatz(id, RecordType.MEAL_SLOT, MealSlotPayload(datum, mahlzeit, rezept, portionen, gekocht))

    private suspend fun HttpClient.post(cookie: String, pfad: String, vararg felder: Pair<String, String>, csrf: Boolean = true): HttpResponse {
        val token = if (csrf) csrf(cookie) else null
        return submitForm(pfad, parameters {
            token?.let { append("_csrf", it) }
            for ((k, v) in felder) append(k, v)
        }) { header(HttpHeaders.Cookie, cookie) }
    }

    @Test fun einplanenUndAnzeigen() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val antwort = c.post(cookie, "/plan/einplanen", "tag" to "2026-10-13", "mahlzeit" to "ABENDESSEN", "rezept" to "r1", "portionen" to "3")
        assertEquals(HttpStatusCode.SeeOther, antwort.status, antwort.bodyAsText().take(400))
        assertEquals("/plan?ab=2026-10-13#tag-2026-10-13", antwort.headers[HttpHeaders.Location])
        val e = HaushaltsDaten(env.deps.db).fuer(h).plan.single()
        assertEquals(LocalDate.of(2026, 10, 13), e.datum)
        assertEquals("ABENDESSEN", e.mahlzeit)
        assertEquals(3, e.portionen)
        assertTrue("Curry" in c.get("/plan?ab=2026-10-13") { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
    }

    @Test fun portionenLeerHeisstWieImRezept() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry", portionen = 4))
        val c = ohneWeiterleitung()
        c.post(c.anmelden(benutzer), "/plan/einplanen", "tag" to "2026-10-13", "mahlzeit" to "ABENDESSEN", "rezept" to "r1", "portionen" to "")
        assertEquals(4, HaushaltsDaten(env.deps.db).fuer(h).plan.single().portionen)
    }

    @Test fun portionenVerschiebenLoeschen() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), slot("s1", "2026-10-12", portionen = 1))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val daten = HaushaltsDaten(env.deps.db)
        c.post(cookie, "/plan/s1", "aktion" to "minus", "ab" to "2026-10-12")
        assertEquals(1, daten.fuer(h).plan.single().portionen, "nicht unter 1")
        c.post(cookie, "/plan/s1", "aktion" to "plus", "ab" to "2026-10-12")
        c.post(cookie, "/plan/s1", "aktion" to "plus", "ab" to "2026-10-12")
        assertEquals(3, daten.fuer(h).plan.single().portionen)
        val spaeter = c.post(cookie, "/plan/s1", "aktion" to "spaeter", "ab" to "2026-10-12", "tage" to "14")
        assertEquals("/plan?ab=2026-10-12&tage=14#tag-2026-10-13", spaeter.headers[HttpHeaders.Location])
        assertEquals(LocalDate.of(2026, 10, 13), daten.fuer(h).plan.single().datum)
        c.post(cookie, "/plan/s1", "aktion" to "loeschen", "ab" to "2026-10-12")
        assertTrue(daten.fuer(h).plan.isEmpty())
    }

    /** Review Focus 2: zweites „gekocht“ (Doppelklick oder Handy war schneller) zieht nicht noch einmal ab. */
    @Test fun gekochtZiehtVorratAbEinmal() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), slot("s1", "2026-10-11"),
            datensatz("v1", RecordType.PANTRY_ITEM, PantryItemPayload("reis", "1000", "GRAM")))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val daten = HaushaltsDaten(env.deps.db)
        assertEquals(HttpStatusCode.SeeOther, c.post(cookie, "/plan/s1", "aktion" to "gekocht").status)
        assertEquals(0, daten.fuer(h).vorrat.single().menge.compareTo(BigDecimal(800)))
        assertNotNull(daten.fuer(h).plan.single().gekochtAm)
        c.post(cookie, "/plan/s1", "aktion" to "gekocht")
        assertEquals(0, daten.fuer(h).vorrat.single().menge.compareTo(BigDecimal(800)), "nur einmal abgezogen")
        val html = c.get("/plan?ab=2026-10-11&bearbeiten=1") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue(" · gekocht" in html && "s1 gekocht" !in html && "s1 loeschen" in html, "Knopf verschwindet, Status sichtbar")
    }

    /** Final-Review 2: gleichzeitige „gekocht“-Klicks (Doppelklick) ziehen den Vorrat nur einmal ab. */
    @Test fun gleichzeitigGekochtZiehtEinmalAb() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        // Vorrat reicht für 10 Durchgänge à 200 g; jeder Durchgang klickt 8× gleichzeitig „gekocht“
        env.push(h, reis(), rezept("r1", "Curry"), datensatz("v1", RecordType.PANTRY_ITEM, PantryItemPayload("reis", "2000", "GRAM")))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val token = c.csrf(cookie)
        for (runde in 1..5) {
            env.push(h, slot("s$runde", "2026-10-11"))
            kotlinx.coroutines.coroutineScope {
                (1..8).map {
                    async(kotlinx.coroutines.Dispatchers.IO) {
                        c.submitForm("/plan/s$runde", parameters { append("_csrf", token); append("aktion", "gekocht") }) { header(HttpHeaders.Cookie, cookie) }
                    }
                }.forEach { it.await() }
            }
            assertEquals(0, HaushaltsDaten(env.deps.db).fuer(h).vorrat.single().menge.compareTo(BigDecimal(2000 - 200 * runde)), "Runde $runde")
        }
    }

    @Test fun auswahlseiteFiltertNachMahlzeit() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        // Porridge auf Frühstück festgelegt
        val porridge = Protocol.json.decodeFromJsonElement(RecipePayload.serializer(), rezept("r2", "Porridge").payload!!).copy(mahlzeiten = "FRUEHSTUECK")
        env.push(h, reis(), rezept("r1", "Curry"), datensatz("r2", RecordType.RECIPE, porridge))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val abend = c.get("/plan/neu?tag=2026-10-12&mahlzeit=ABENDESSEN") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("Curry" in abend && "Porridge" !in abend, abend.take(1500))
        val alle = c.get("/plan/neu?tag=2026-10-12&mahlzeit=ABENDESSEN&alle=1") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("Porridge" in alle)
        val suche = c.get("/plan/neu/liste?tag=2026-10-12&mahlzeit=ABENDESSEN&alle=1&q=porr") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("Porridge" in suche && "Curry" !in suche && "<html" !in suche)
    }

    @Test fun auswahlVorgabeErsteFehlendeMahlzeit() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), slot("s1", "2026-10-12", "FRUEHSTUECK"))
        val c = ohneWeiterleitung()
        val html = c.get("/plan/neu?tag=2026-10-12") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText()
        assertTrue(Regex("<option[^>]*value=\"MITTAGESSEN\"[^>]*selected|<option[^>]*selected[^>]*value=\"MITTAGESSEN\"").containsMatchIn(html), html.take(2000))
    }

    @Test fun ungueltigeEingabenBehaltenFormular() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        for ((feld, wert, meldung) in listOf(
            Triple("portionen", "0", "Portionen: 1 bis 99"),
            Triple("tag", "2028-10-12", "Datum höchstens ein Jahr"),
            Triple("mahlzeit", "BRUNCH", "Bitte eine Mahlzeit wählen"),
        )) {
            val felder = mutableMapOf("tag" to "2026-10-13", "mahlzeit" to "ABENDESSEN", "rezept" to "r1", "portionen" to "2")
            felder[feld] = wert
            val antwort = c.post(cookie, "/plan/einplanen", *felder.toList().toTypedArray())
            assertEquals(HttpStatusCode.OK, antwort.status, feld)
            val html = antwort.bodyAsText()
            assertTrue(meldung in html, "$feld: ${html.take(1200)}")
            assertTrue("Curry" in html, "Formular zeigt das Rezept")
        }
        assertTrue(HaushaltsDaten(env.deps.db).fuer(h).plan.isEmpty())
    }

    @Test fun fremdeIdsSind404() = webServer { env ->
        val (benutzer, _, _) = env.haushalt("Meins")
        val (_, _, fremd) = env.haushalt("Fremd")
        env.push(fremd, reis(), rezept("geheim", "Geheimrezept"), slot("fs", "2026-10-12", rezept = "geheim"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        assertEquals(HttpStatusCode.NotFound, c.post(cookie, "/plan/fs", "aktion" to "plus").status)
        assertEquals(HttpStatusCode.NotFound, c.post(cookie, "/plan/fs", "aktion" to "gekocht").status)
        assertEquals(HttpStatusCode.NotFound, c.post(cookie, "/plan/einplanen", "tag" to "2026-10-12", "mahlzeit" to "ABENDESSEN", "rezept" to "geheim", "portionen" to "2").status)
        val fremderPlan = HaushaltsDaten(env.deps.db).fuer(fremd).plan.single()
        assertEquals(2, fremderPlan.portionen)
        assertNull(fremderPlan.gekochtAm)
    }

    @Test fun neuePostsOhneCsrfSind403() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), slot("s1", "2026-10-12"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        assertEquals(HttpStatusCode.Forbidden, c.post(cookie, "/plan/s1", "aktion" to "loeschen", csrf = false).status)
        assertEquals(HttpStatusCode.Forbidden, c.post(cookie, "/plan/einplanen", "tag" to "2026-10-12", "mahlzeit" to "ABENDESSEN", "rezept" to "r1", csrf = false).status)
        assertEquals(1, HaushaltsDaten(env.deps.db).fuer(h).plan.size)
    }

    @Test fun inzwischenGeloeschtGibtHinweis() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), slot("s1", "2026-10-12"))
        env.push(h, SyncRecord(id = "s1", type = RecordType.MEAL_SLOT, deleted = true, updatedAt = 9))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val antwort = c.post(cookie, "/plan/s1", "aktion" to "plus", "ab" to "2026-10-12")
        assertEquals(HttpStatusCode.SeeOther, antwort.status)
        val ziel = antwort.headers[HttpHeaders.Location]!!
        assertTrue("hinweis=weg" in ziel, ziel)
        assertTrue("Eintrag gibt es nicht mehr" in c.get(ziel) { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
        assertTrue(HaushaltsDaten(env.deps.db).fuer(h).plan.isEmpty(), "nicht wiederbelebt")
    }

    @Test fun rezeptSeiteHatEinplanen() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        val html = c.get("/rezept/r1") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText()
        assertTrue("action=\"/plan/einplanen\"" in html && "value=\"2026-10-11\"" in html)
    }

    @Test fun bearbeitenBleibtNachAktion() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), slot("s1", "2026-10-12"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        assertFalse("s1 plus" in c.get("/plan?ab=2026-10-12") { header(HttpHeaders.Cookie, cookie) }.bodyAsText(), "normale Ansicht ohne Knöpfe")
        val antwort = c.post(cookie, "/plan/aktion", "a" to "s1 plus", "ab" to "2026-10-12", "bearbeiten" to "1")
        assertEquals("/plan?ab=2026-10-12&bearbeiten=1#tag-2026-10-12", antwort.headers[HttpHeaders.Location])
        assertEquals(3, HaushaltsDaten(env.deps.db).fuer(h).plan.single().portionen)
    }

    /** Final-Review 4: Eintrag mit gelöschtem Rezept – nur Löschen geht (alles andere lehnt der Server ab). */
    @Test fun geloeschtesRezeptNurLoeschen() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), slot("s1", "2026-10-12"))
        env.push(h, SyncRecord(id = "r1", type = RecordType.RECIPE, deleted = true, updatedAt = 9))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val html = c.get("/plan?ab=2026-10-12&bearbeiten=1") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("Gelöschtes Rezept" in html && "s1 loeschen" in html, html.take(3000))
        assertFalse("s1 plus" in html || "s1 gekocht" in html || "s1 spaeter" in html)
        assertEquals(HttpStatusCode.SeeOther, c.post(cookie, "/plan/s1", "aktion" to "loeschen").status)
        assertTrue(HaushaltsDaten(env.deps.db).fuer(h).plan.isEmpty())
    }

    /** Kleinigkeiten 4: −/+ an der Grenze (1 bzw. 99 Portionen) schreibt keinen unveränderten Datensatz. */
    @Test fun grenzeSchreibtNichts() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), slot("s1", "2026-10-12", portionen = 1), slot("s2", "2026-10-12", portionen = 99))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val vorher = env.deps.db.tx { cn -> de.foody.server.sync.RecordStore().hoechsteRevision(cn, h) }
        assertEquals(HttpStatusCode.SeeOther, c.post(cookie, "/plan/s1", "aktion" to "minus").status)
        assertEquals(HttpStatusCode.SeeOther, c.post(cookie, "/plan/s2", "aktion" to "plus").status)
        assertEquals(vorher, env.deps.db.tx { cn -> de.foody.server.sync.RecordStore().hoechsteRevision(cn, h) }, "keine neue Revision")
    }

    @Test fun planVierzehnTageUnter30KB() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val slots = (0L until 14L).flatMap { d ->
            listOf("FRUEHSTUECK", "MITTAGESSEN", "ABENDESSEN").map { m -> slot(java.util.UUID.randomUUID().toString(), LocalDate.of(2026, 10, 12).plusDays(d).toString(), m) }
        }
        env.push(h, reis(), rezept("r1", "Ein Rezept mit einem recht langen Namen"), *slots.toTypedArray())
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val bytes = c.get("/plan?ab=2026-10-12&tage=14") { header(HttpHeaders.Cookie, cookie) }.bodyAsBytes().size
        assertTrue(bytes < 30_000, "$bytes Bytes")
        assertFalse(bytes < 1_000)
        val bearbeiten = c.get("/plan?ab=2026-10-12&tage=14&bearbeiten=1") { header(HttpHeaders.Cookie, cookie) }.bodyAsBytes().size
        assertTrue(bearbeiten < 50_000, "Bearbeiten: $bearbeiten Bytes")
    }
}

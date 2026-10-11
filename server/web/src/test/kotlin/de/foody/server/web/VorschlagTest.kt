package de.foody.server.web

import de.foody.domain.Mahlzeit
import de.foody.domain.PantryCoverage
import de.foody.domain.PlanVorschlag
import de.foody.sync.protocol.MealSlotPayload
import de.foody.sync.protocol.RecordType
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Heute ist laut Testuhr der 11.10.2026 (Sonntag); Plan ab Montag, 12.10. */
class VorschlagTest {
    private fun slot(id: String, datum: String, mahlzeit: String = "ABENDESSEN", rezept: String = "r1") =
        datensatz(id, RecordType.MEAL_SLOT, MealSlotPayload(datum, mahlzeit, rezept, 2))

    private fun rezepte(n: Int) = (1..n).map { rezept("r$it", "Rezept $it") }

    private val paar = Regex("name=\"tag_(\\d+)\" value=\"([0-9-]+)\".*?name=\"rezept_\\1\" value=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)

    private fun paare(html: String) = paar.findAll(html).map { LocalDate.parse(it.groupValues[2]) to it.groupValues[3] }.toList()

    @Test fun gleicherSeedGleicheSeite() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), *rezepte(10).toTypedArray(), slot("s1", "2026-10-14"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val url = "/plan/vorschlag?ab=2026-10-12&mahlzeit=ABENDESSEN&seed=7"
        val eins = c.get(url) { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        val zwei = c.get(url) { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        val p = paare(eins)
        assertEquals(6, p.size, eins.take(2000))
        assertEquals(p, paare(zwei))
        assertFalse(LocalDate.of(2026, 10, 14) in p.map { it.first }, "belegter Tag bleibt frei")
        assertTrue("seed=8" in eins, "Neu mischen")
        // Gleiche Rechnung wie die gemeinsame Regel
        val haushalt = HaushaltsDaten(env.deps.db).fuer(h)
        val tage = (0L until 7L).map { LocalDate.of(2026, 10, 12).plusDays(it) }
        val leer = PlanVorschlag.leereTage(tage, LocalDate.of(2026, 10, 11), mapOf(LocalDate.of(2026, 10, 14) to listOf("ABENDESSEN")), Mahlzeit.ABENDESSEN)
        val erwartet = PlanVorschlag.vorschlagen(
            haushalt.rezepte.values.map { PlanVorschlag.Rezept(it.id, it.payload.favorite, it.einordnung.mahlzeiten, listOf(PantryCoverage.Requirement(it.id, "reis", "Reis"))) },
            leer, emptySet(), listOf("r1" to LocalDate.of(2026, 10, 14)), tage.last(), 7, Mahlzeit.ABENDESSEN,
        )
        assertEquals(erwartet.toList().sortedBy { it.first }, p)
    }

    @Test fun uebernimmtGenauDieAngezeigtenPaare() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), *rezepte(10).toTypedArray())
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val p = paare(c.get("/plan/vorschlag?ab=2026-10-12&mahlzeit=MITTAGESSEN&seed=3") { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
        val token = c.csrf(cookie)
        val antwort = c.submitForm("/plan/vorschlag", parameters {
            append("_csrf", token); append("mahlzeit", "MITTAGESSEN"); append("ab", "2026-10-12")
            p.forEachIndexed { i, (tag, r) -> append("tag_$i", tag.toString()); append("rezept_$i", r) }
        }) { header(HttpHeaders.Cookie, cookie) }
        assertEquals(HttpStatusCode.SeeOther, antwort.status, antwort.bodyAsText().take(500))
        val plan = HaushaltsDaten(env.deps.db).fuer(h).plan
        assertEquals(p.toSet(), plan.map { it.datum to it.rezeptId }.toSet())
        assertTrue(plan.all { it.mahlzeit == "MITTAGESSEN" && it.portionen == 2 }, "Portionen des Rezepts")
    }

    @Test fun manipuliertesPaarAbgelehnt() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val (_, _, fremd) = env.haushalt("Fremd")
        env.push(h, reis(), rezept("r1", "Curry"), rezept("alt", "Alt", archiviert = true))
        env.push(fremd, reis(), rezept("geheim", "Geheim"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        for ((tag, r) in listOf("2026-10-12" to "geheim", "2026-10-12" to "alt", "2026-11-30" to "r1")) {
            val token = c.csrf(cookie)
            val antwort = c.submitForm("/plan/vorschlag", parameters {
                append("_csrf", token); append("mahlzeit", "ABENDESSEN"); append("ab", "2026-10-12")
                append("tag_0", "2026-10-13"); append("rezept_0", "r1")
                append("tag_1", tag); append("rezept_1", r)
            }) { header(HttpHeaders.Cookie, cookie) }
            assertEquals(HttpStatusCode.BadRequest, antwort.status, "$tag/$r")
        }
        assertTrue(HaushaltsDaten(env.deps.db).fuer(h).plan.isEmpty(), "nichts geschrieben, auch nicht das gültige Paar")
    }

    /** Review Focus 5. */
    @Test fun nichtsVorzuschlagen() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val ohneRezepte = c.get("/plan/vorschlag?ab=2026-10-12&mahlzeit=ABENDESSEN") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("Nichts vorzuschlagen" in ohneRezepte && "Übernehmen" !in ohneRezepte)
        env.push(h, reis(), rezept("r1", "Curry"), *(0L until 7L).map { slot("s$it", LocalDate.of(2026, 10, 12).plusDays(it).toString()) }.toTypedArray())
        val voll = c.get("/plan/vorschlag?ab=2026-10-12&mahlzeit=ABENDESSEN") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("Nichts vorzuschlagen" in voll && "Übernehmen" !in voll)
    }

    @Test fun vorschlagPostOhneCsrfIst403() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val antwort = c.submitForm("/plan/vorschlag", parameters {
            append("mahlzeit", "ABENDESSEN"); append("ab", "2026-10-12"); append("tag_0", "2026-10-13"); append("rezept_0", "r1")
        }) { header(HttpHeaders.Cookie, cookie) }
        assertEquals(HttpStatusCode.Forbidden, antwort.status)
    }

    @Test fun planSeiteHatVorschlagen() = webServer { env ->
        val (benutzer, _, _) = env.haushalt()
        val c = ohneWeiterleitung()
        val html = c.get("/plan?ab=2026-10-12") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText()
        assertTrue("action=\"/plan/vorschlag\"" in html)
    }
}

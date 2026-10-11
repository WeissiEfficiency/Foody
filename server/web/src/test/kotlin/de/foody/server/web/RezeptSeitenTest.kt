package de.foody.server.web

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RezeptSeitenTest {
    @Test fun listeZeigtRezepteMitKcal() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        val html = c.get("/") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText()
        assertTrue("Curry" in html)
        assertTrue("350 kcal" in html, "200 g Reis à 1464 kJ/100 g für 2 Portionen ≈ 350 kcal")
    }

    @Test fun sucheFiltertNachNameUndZutat() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"), rezept("r2", "Risotto"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val nachName = c.get("/rezepte/liste?q=cur") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("Curry" in nachName && "Risotto" !in nachName)
        val nachZutat = c.get("/?q=reis") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("Curry" in nachZutat && "Risotto" in nachZutat, "Zutat „Reis“ steckt in beiden")
    }

    @Test fun filterNachMahlzeit() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Gemüsecurry"), rezept("r2", "Porridge"))
        val c = ohneWeiterleitung()
        val html = c.get("/rezepte/liste?mahlzeit=FRUEHSTUECK") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText()
        assertTrue("Porridge" in html)
        assertFalse("Gemüsecurry" in html)
    }

    @Test fun archivierteAusgeblendet() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Altes Curry", archiviert = true))
        val c = ohneWeiterleitung()
        assertFalse("Altes Curry" in c.get("/") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText())
    }

    @Test fun rezeptSkaliertPortionen() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), rezept("r1", "Curry"))
        val c = ohneWeiterleitung()
        val cookie = c.anmelden(benutzer)
        val standard = c.get("/rezept/r1") { header(HttpHeaders.Cookie, cookie) }.bodyAsText()
        assertTrue("200 g" in standard && "Reis" in standard && "Reis kochen" in standard)
        assertTrue("350 kcal" in standard, "je Portion")
        assertTrue("400 g" in c.get("/rezept/r1?portionen=4") { header(HttpHeaders.Cookie, cookie) }.bodyAsText())
    }

    @Test fun rezeptAusFremdemHaushaltIst404() = webServer { env ->
        val (benutzer, _, _) = env.haushalt("Meins")
        val (_, _, fremd) = env.haushalt("Fremd")
        env.push(fremd, reis(), rezept("geheim", "Geheimrezept"))
        val c = ohneWeiterleitung()
        val antwort = c.get("/rezept/geheim") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }
        assertEquals(HttpStatusCode.NotFound, antwort.status)
        assertFalse("Geheimrezept" in antwort.bodyAsText())
    }

    @Test fun leererHaushaltZeigtHinweis() = webServer { env ->
        val (benutzer, _, _) = env.haushalt()
        val c = ohneWeiterleitung()
        val html = c.get("/") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText()
        assertTrue("Noch keine Rezepte" in html)
    }

    @Test fun seiteUnter30KB() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        env.push(h, reis(), *Array(100) { rezept("r$it", "Rezept Nummer $it mit langem Namen", tags = "Abend, schnell") })
        val c = ohneWeiterleitung()
        val bytes = c.get("/") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsBytes().size
        assertTrue(bytes < 30_000, "$bytes Bytes")
    }

    /** Wie die App: kcal nur bei verlässlichem Profil (≥ 80 % der Zutaten mit Energie), sonst „–“. */
    @Test fun unzuverlaessigeKcalWieInDerAppAusgeblendet() = webServer { env ->
        val (benutzer, _, h) = env.haushalt()
        val ohneWerte = (1..3).map { datensatz("z$it", de.foody.sync.protocol.RecordType.INGREDIENT, de.foody.sync.protocol.IngredientPayload(name = "Zutat $it")) }
        val r = datensatz("r1", de.foody.sync.protocol.RecordType.RECIPE, de.foody.sync.protocol.RecipePayload(
            name = "Eintopf", servings = 2, tags = "", favorite = false,
            lines = listOf(de.foody.sync.protocol.RecipePayload.Line("l0", "reis", "200", "GRAM", 0, null, false)) +
                (1..3).map { de.foody.sync.protocol.RecipePayload.Line("l$it", "z$it", "100", "GRAM", it, null, false) },
            steps = emptyList(),
        ))
        env.push(h, reis(), *ohneWerte.toTypedArray(), r)
        val c = ohneWeiterleitung()
        val html = c.get("/") { header(HttpHeaders.Cookie, c.anmelden(benutzer)) }.bodyAsText()
        assertTrue("Eintopf" in html)
        assertFalse("kcal" in html.substringAfter("Eintopf").substringBefore("</li>"), "nur 1 von 4 Zutaten mit Werten")
    }
}

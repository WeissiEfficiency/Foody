package de.foody.server.web.seiten

import de.foody.domain.NutritionResult
import de.foody.server.ServerDeps
import de.foody.server.web.Eingaben
import de.foody.server.web.Haushalt
import de.foody.server.web.HaushaltsDaten
import de.foody.server.web.Navi
import de.foody.server.web.Schreiben
import de.foody.server.web.Sitzung
import de.foody.server.web.ZutatFormular
import de.foody.server.web.ZutatSchreiber
import de.foody.server.web.ZutatSpeichern
import de.foody.server.web.abgelehnt
import de.foody.server.web.csrfAus
import de.foody.server.web.csrfGueltig
import de.foody.server.web.formular
import de.foody.server.web.nichtGefunden
import de.foody.server.web.seiteAntworten
import de.foody.server.web.sitzungMitHaushalt
import de.foody.server.web.weiterleiten
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

/** Zutatenkatalog: Liste mit Suche (nach Abteilung gruppiert, ohne Formulare je Zeile) und eine Seite je Zutat. */
internal object ZutatSeiten {
    private const val MAX_VERWENDUNGEN = 5
    private const val OHNE_ABTEILUNG = "Ohne Abteilung"

    suspend fun liste(call: ApplicationCall, s: Sitzung, h: Haushalt) {
        val q = call.request.queryParameters["q"].orEmpty().trim().take(100)
        call.seiteAntworten("Zutaten", s, Navi.VORRAT) {
            h1 { +"Zutatenkatalog" }
            div("zeile") { a(href = "/zutat/neu", classes = "knopf") { +"Neue Zutat" }; a(href = "/vorrat") { +"Zum Vorrat" } }
            if (call.request.queryParameters["hinweis"] == "weg") p("hinweis") { +"Eintrag gibt es nicht mehr." }
            form(action = "/zutaten", method = FormMethod.get, classes = "zeile karte") {
                attributes["hx-get"] = "/zutaten/liste"
                attributes["hx-target"] = "#liste"
                attributes["hx-trigger"] = "input changed delay:150ms from:input, submit"
                input(InputType.search, name = "q") { value = q; placeholder = "Zutat suchen"; attributes["aria-label"] = "Suche" }
            }
            div { id = "liste"; zutatenListe(h, q) }
        }
    }

    suspend fun listenTeil(call: ApplicationCall, h: Haushalt) {
        val q = call.request.queryParameters["q"].orEmpty().trim().take(100)
        call.respondText(createHTML(prettyPrint = false).div { zutatenListe(h, q) }.removePrefix("<div>").removeSuffix("</div>"), ContentType.Text.Html)
    }

    private fun FlowContent.zutatenListe(h: Haushalt, q: String) {
        val treffer = h.zutaten.values.filter { q.isEmpty() || q.lowercase() in it.name.lowercase() }
        if (treffer.isEmpty()) {
            p("leise") { +(if (h.zutaten.isEmpty()) "Noch keine Zutaten." else "Keine Zutaten gefunden.") }
            return
        }
        // Nach Abteilung gruppiert: die Abteilung steht einmal als Überschrift statt in jeder Zeile
        for ((abteilung, zutaten) in treffer.groupBy { it.category ?: OHNE_ABTEILUNG }.toSortedMap(compareBy { if (it == OHNE_ABTEILUNG) "￿" else it.lowercase() })) {
            h2 { +abteilung }
            ul("liste karte") {
                for (z in zutaten.sortedBy { it.name.lowercase() }) li {
                    a(href = "/zutat/${z.id}") { +z.name }
                    val kj = z.nutrients?.values?.get(de.foody.domain.Nutrient.ENERGY_KJ)
                    // Ohne eigenes <span>: spart bei 300 Zutaten rund 8 KB; die Zeile (Flex) setzt den Text trotzdem rechts
                    +(kj?.let { "${NutritionResult.kcal(it)} kcal" } ?: "keine Nährwerte")
                }
            }
        }
    }

    // --- Eine Zutat ---

    suspend fun neu(call: ApplicationCall, s: Sitzung) = formularSeite(call, s, ZutatFormular.leer(), emptyList())

    suspend fun bearbeiten(call: ApplicationCall, s: Sitzung, h: Haushalt, id: String) {
        val z = h.zutatenRoh[id] ?: return call.nichtGefunden(s, "Diese Zutat gibt es nicht (mehr).")
        formularSeite(call, s, ZutatFormular.aus(z), emptyList())
    }

    private suspend fun formularSeite(call: ApplicationCall, s: Sitzung, f: ZutatFormular, meldungen: List<String>, gibtEsSchon: ZutatSpeichern.GibtEsSchon? = null) =
        call.seiteAntworten(if (f.id == null) "Neue Zutat" else "Zutat bearbeiten", s, Navi.VORRAT) {
            h1 { +(if (f.id == null) "Neue Zutat" else f.name) }
            for (m in meldungen) p("hinweis fehler") { +m }
            gibtEsSchon?.let { g -> p("hinweis") { +"Gibt es schon: "; a(href = "/zutat/${g.id}") { +g.name } } }
            form(action = "/zutat/speichern", method = FormMethod.post, classes = "karte") {
                hiddenInput(name = "_csrf") { value = s.csrf }
                hiddenInput(name = "id") { value = f.id.orEmpty() }
                textFeld("Name", "name", f.name)
                textFeld("Abteilung", "kategorie", f.kategorie)
                div("zeile") { zahlFeld("Dichte (g/ml)", "dichte", f.dichte); zahlFeld("Stückgewicht (g)", "stueckgewicht", f.stueckgewicht) }
                label {
                    +"Nährwerte je"
                    select {
                        name = "basis"
                        option { value = ""; selected = f.basis.isEmpty(); +"– keine –" }
                        option { value = "PER_100_G"; selected = f.basis == "PER_100_G"; +"100 g" }
                        option { value = "PER_100_ML"; selected = f.basis == "PER_100_ML"; +"100 ml" }
                    }
                }
                div("zeile") {
                    zahlFeld("Energie (kJ)", "kj", f.kj)
                    // Nur über Eingaben.zahl (Grenzen, keine Exponenten): „1e999999999“ würde sonst minutenlang gerechnet
                    Eingaben.zahl(f.kj)?.let { span("leise") { +"≈ ${NutritionResult.kcal(it)} kcal" } }
                }
                div("zeile") { zahlFeld("Eiweiß (g)", "eiweiss", f.eiweiss); zahlFeld("Kohlenhydrate (g)", "kh", f.kh); zahlFeld("Fett (g)", "fett", f.fett) }
                div("zeile") { zahlFeld("Ballaststoffe (g)", "ballaststoffe", f.ballaststoffe); zahlFeld("Zucker (g)", "zucker", f.zucker); zahlFeld("Salz (g)", "salz", f.salz) }
                div("zeile") { button { +"Speichern" }; a(href = "/zutaten") { +"Zurück" } }
            }
            if (f.id != null) form(action = "/zutat/${f.id}/loeschen", method = FormMethod.post, classes = "zeile") {
                hiddenInput(name = "_csrf") { value = s.csrf }
                button(classes = "leise") { +"Löschen" }
            }
        }

    private fun FlowContent.textFeld(text: String, name: String, wert: String) =
        label { +text; input(InputType.text, name = name, classes = "breit") { value = wert } }

    private fun FlowContent.zahlFeld(text: String, name: String, wert: String) =
        label { +text; input(InputType.text, name = name, classes = "menge") { value = wert; attributes["inputmode"] = "decimal" } }

    suspend fun speichern(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten) {
        val s = call.sitzungMitHaushalt(deps) ?: return
        val form = call.formular()
        if (!s.csrfGueltig(call.csrfAus(form))) return call.respond(HttpStatusCode.Forbidden)
        val f = ZutatFormular.aus(form)
        when (val r = ZutatSchreiber(deps, daten).speichern(s.haushalt!!, f)) {
            is ZutatSpeichern.Ok -> call.weiterleiten("/zutat/${r.id}")
            is ZutatSpeichern.Fehler -> formularSeite(call, s, f, r.meldungen)
            is ZutatSpeichern.GibtEsSchon -> formularSeite(call, s, f, emptyList(), r)
            ZutatSpeichern.NichtGefunden -> call.nichtGefunden(s, "Diese Zutat gibt es nicht (mehr).")
            ZutatSpeichern.Weg -> call.weiterleiten("/zutaten?hinweis=weg")
            ZutatSpeichern.Abgelehnt -> call.abgelehnt(s)
        }
    }

    suspend fun loeschen(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, id: String) {
        val s = call.sitzungMitHaushalt(deps) ?: return
        val form = call.formular()
        if (!s.csrfGueltig(call.csrfAus(form))) return call.respond(HttpStatusCode.Forbidden)
        val household = s.haushalt!!
        val h = daten.fuer(household)
        val schreiber = ZutatSchreiber(deps, daten)
        val z = h.zutatenRoh[id]
        if (z != null) {
            val wo = schreiber.verwendungen(household, id)
            if (wo.isNotEmpty()) {
                val text = wo.take(MAX_VERWENDUNGEN).joinToString(", ") + if (wo.size > MAX_VERWENDUNGEN) " und ${wo.size - MAX_VERWENDUNGEN} weitere" else ""
                return formularSeite(call, s, ZutatFormular.aus(z), listOf("Wird noch verwendet in: $text. Erst dort entfernen, dann löschen."))
            }
        }
        when (schreiber.loeschen(household, id)) {
            Schreiben.Ok -> call.weiterleiten("/zutaten")
            Schreiben.Weg -> call.weiterleiten("/zutaten?hinweis=weg")
            Schreiben.NichtGefunden -> call.nichtGefunden(s, "Diese Zutat gibt es nicht (mehr).")
            Schreiben.Abgelehnt -> call.abgelehnt(s)
        }
    }
}

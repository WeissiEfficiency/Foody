package de.foody.server.web.seiten

import de.foody.domain.Gang
import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.server.ServerDeps
import de.foody.server.web.FormZeile
import de.foody.server.web.Haushalt
import de.foody.server.web.HaushaltsDaten
import de.foody.server.web.Navi
import de.foody.server.web.RezeptFormular
import de.foody.server.web.RezeptSchreiber
import de.foody.server.web.Sitzung
import de.foody.server.web.Speichern
import de.foody.server.web.csrfAus
import de.foody.server.web.csrfGueltig
import de.foody.server.web.formular
import de.foody.server.web.formularVeraltet
import de.foody.server.web.nichtGefunden
import de.foody.server.web.seiteAntworten
import de.foody.server.web.sitzungMitHaushalt
import de.foody.server.web.text
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.checkBoxInput
import kotlinx.html.dataList
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.radioInput
import kotlinx.html.select
import kotlinx.html.stream.createHTML
import kotlinx.html.textArea
import kotlinx.html.ul

/** Rezept-Editor (anlegen, bearbeiten), Konfliktseite und Archivieren – funktioniert auch ohne JavaScript. */
internal object EditorSeite {
    private const val LEERE_ZEILEN = 3

    private fun leer() = RezeptFormular(null, null, false, "", "2", "", "", "", "", "", "", false, emptySet(), emptySet(), emptyList())

    suspend fun neu(call: ApplicationCall, s: Sitzung) = zeigen(call, s, leer(), emptyMap(), konflikt = false)

    suspend fun bearbeiten(call: ApplicationCall, s: Sitzung, h: Haushalt, id: String) {
        val r = h.rezepte[id] ?: return call.nichtGefunden(s, "Dieses Rezept gibt es nicht (mehr).")
        zeigen(call, s, RezeptFormular.aus(r, h), emptyMap(), konflikt = false)
    }

    suspend fun speichern(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten) {
        val s = call.sitzungMitHaushalt(deps) ?: return
        val form = call.formular()
        if (!s.csrfGueltig(call.csrfAus(form))) return call.formularVeraltet()
        val f = RezeptFormular.aus(form)
        when (val e = RezeptSchreiber(deps, daten).speichern(s.haushalt!!, f)) {
            is Speichern.Ok -> {
                call.response.headers.append(HttpHeaders.Location, "/rezept/${e.id}")
                call.respond(HttpStatusCode.SeeOther)
            }
            is Speichern.Fehler -> zeigen(call, s, f, e.meldungen, konflikt = false)
            // Mit der neuen Revision: „Trotzdem speichern“ schickt dasselbe Formular erzwungen
            is Speichern.Konflikt -> zeigen(call, s, f.copy(rev = e.aktuelleRev, erzwingen = true), emptyMap(), konflikt = true)
            Speichern.NichtGefunden -> call.nichtGefunden(s, "Dieses Rezept gibt es nicht (mehr).")
        }
    }

    suspend fun archivieren(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, id: String, archiviert: Boolean) {
        val s = call.sitzungMitHaushalt(deps) ?: return
        val form = call.formular()
        if (!s.csrfGueltig(call.csrfAus(form))) return call.formularVeraltet()
        if (!RezeptSchreiber(deps, daten).archivieren(s.haushalt!!, id, archiviert)) return call.nichtGefunden(s, "Dieses Rezept gibt es nicht (mehr).")
        call.response.headers.append(HttpHeaders.Location, "/rezept/$id")
        call.respond(HttpStatusCode.SeeOther)
    }

    /** Eine leere Zutatenzeile für „Zeile hinzufügen“ (htmx hängt sie an die Liste an). */
    suspend fun zeile(call: ApplicationCall) =
        call.respondText(createHTML(prettyPrint = false).ul { li { zeilenFelder(FormZeile("", MeasureUnit.GRAM.symbol, "", "", false), null) } }
            .removePrefix("<ul>").removeSuffix("</ul>"), ContentType.Text.Html)

    /** Vorschläge für das Zutatenfeld – getrennt geladen, damit nicht jede Editorseite den ganzen Katalog trägt. */
    suspend fun zutatenliste(call: ApplicationCall, h: Haushalt) =
        call.respondText(createHTML(prettyPrint = false).dataList {
            id = "zutaten"
            for (n in h.zutaten.values.map { it.name }.sorted()) option { value = n }
        }, ContentType.Text.Html)

    private suspend fun zeigen(call: ApplicationCall, s: Sitzung, f: RezeptFormular, fehler: Map<String, String>, konflikt: Boolean) =
        call.seiteAntworten(if (f.id == null) "Neues Rezept" else "Rezept bearbeiten", s, Navi.REZEPTE) {
            h1 { +(if (f.id == null) "Neues Rezept" else "Rezept bearbeiten") }
            if (konflikt) div("hinweis") {
                p { +"Inzwischen geändert (z. B. am Handy). Deine Eingaben sind unten noch da." }
                div("zeile") {
                    a(href = "/rezept/${f.id}/bearbeiten", classes = "knopf") { +"Aktuellen Stand laden" }
                }
            }
            if (fehler.isNotEmpty()) div("hinweis fehler") { for (m in fehler.values) p { +m } }
            form(action = "/rezept/speichern", method = FormMethod.post) {
                hiddenInput(name = "_csrf") { value = s.csrf }
                hiddenInput(name = "id") { value = f.id.orEmpty() }
                hiddenInput(name = "rev") { value = f.rev?.toString().orEmpty() }
                if (konflikt) hiddenInput(name = "erzwingen") { value = "1" }
                div("karte") {
                    feld("Name", "name", f.name, fehler)
                    div("zeile") {
                        feld("Portionen", "portionen", f.portionen, fehler, InputType.number)
                        feld("Vorbereitung (Min.)", "vorbereitung", f.vorbereitung, fehler, InputType.number)
                        feld("Kochzeit (Min.)", "kochzeit", f.kochzeit, fehler, InputType.number)
                    }
                    feld("Tags (mit Komma getrennt)", "tags", f.tags, fehler)
                    feld("Quelle (Link)", "quelle", f.quelle, fehler)
                }
                h2 { +"Zutaten"; a(href = "/zutaten", classes = "leise") { +"  Zutatenkatalog" } }
                // Einheiten einmal je Seite statt einer Auswahlliste je Zeile (spart ~350 Bytes je Zeile)
                dataList { id = "einheiten"; for (u in MeasureUnit.entries) option { value = u.symbol } }
                // Vorschläge aus dem Katalog: per htmx nachgeladen
                div { attributes["hx-get"] = "/rezept/zutatenliste"; attributes["hx-trigger"] = "load"; attributes["hx-swap"] = "outerHTML" }
                ul("liste karte") {
                    id = "zeilen"
                    f.zeilen.forEachIndexed { i, z -> li { zeilenFelder(z, fehler["zeile_$i"]) } }
                    repeat(LEERE_ZEILEN) { li { zeilenFelder(FormZeile("", MeasureUnit.GRAM.symbol, "", "", false), null) } }
                }
                p("leise") { +"Zum Entfernen den Zutatennamen leeren. Menge leer = nach Bedarf." }
                button(classes = "leise") {
                    type = kotlinx.html.ButtonType.button
                    attributes["hx-get"] = "/rezept/zeile"; attributes["hx-target"] = "#zeilen"; attributes["hx-swap"] = "beforeend"
                    +"Zeile hinzufügen"
                }
                h2 { +"Zubereitung" }
                textArea(rows = "8", classes = "breit") { name = "schritte"; attributes["aria-label"] = "Zubereitung, ein Schritt je Zeile"; +f.schritte }
                h2 { +"Einordnung" }
                fieldSet("karte") {
                    legend { +"Mahlzeit und Gang" }
                    label { radioInput(name = "einordnung") { value = "vermuten"; checked = !f.einordnungFest }; +" automatisch vermuten" }
                    label { radioInput(name = "einordnung") { value = "fest"; checked = f.einordnungFest }; +" festlegen:" }
                    div("zeile") {
                        for (m in Mahlzeit.entries) label { checkBoxInput(name = "mahlzeit") { value = m.name; checked = m in f.mahlzeiten }; +" ${m.text()}" }
                    }
                    div("zeile") {
                        for (g in Gang.entries) label { checkBoxInput(name = "gang") { value = g.name; checked = g in f.gaenge }; +" ${g.text()}" }
                    }
                }
                h2 { +"Notizen" }
                textArea(rows = "4", classes = "breit") { name = "notizen"; attributes["aria-label"] = "Notizen"; +f.notizen }
                div("zeile") {
                    button { +(if (konflikt) "Trotzdem speichern" else "Speichern") }
                    a(href = if (f.id == null) "/" else "/rezept/${f.id}") { +"Abbrechen" }
                }
            }
        }

    private fun FlowContent.feld(titel: String, name: String, wert: String, fehler: Map<String, String>, typ: InputType = InputType.text) {
        label {
            +titel
            input(typ, name = name) {
                value = wert
                if (name in fehler) attributes["aria-invalid"] = "true"
            }
        }
    }

    private fun kotlinx.html.LI.zeilenFelder(z: FormZeile, fehler: String?) {
        div("zeile") {
            input(InputType.text, name = "zeile_menge", classes = "menge") { value = z.menge; placeholder = "Menge"; attributes["aria-label"] = "Menge"; attributes["inputmode"] = "decimal" }
            input(InputType.text, name = "zeile_einheit", classes = "einheit") { value = z.einheit; attributes["list"] = "einheiten"; attributes["aria-label"] = "Einheit" }
            input(InputType.text, name = "zeile_zutat") { value = z.zutat; placeholder = "Zutat"; attributes["list"] = "zutaten"; attributes["aria-label"] = "Zutat" }
            input(InputType.text, name = "zeile_hinweis") { value = z.hinweis; placeholder = "Hinweis"; attributes["aria-label"] = "Hinweis" }
            select { name = "zeile_optional"; attributes["aria-label"] = "Optional"
                option { value = "0"; selected = !z.optional; +"nötig" }
                option { value = "1"; selected = z.optional; +"optional" }
            }
            if (fehler != null) p("fehler") { +fehler }
        }
    }

}

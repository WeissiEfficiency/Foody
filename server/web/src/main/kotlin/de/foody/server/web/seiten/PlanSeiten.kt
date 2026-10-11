package de.foody.server.web.seiten

import de.foody.domain.Mahlzeit
import de.foody.domain.PantryCoverage
import de.foody.domain.PlanAuswahl
import de.foody.domain.PlanVorschlag
import de.foody.server.ServerDeps
import de.foody.server.web.Haushalt
import de.foody.server.web.HaushaltsDaten
import de.foody.server.web.Navi
import de.foody.server.web.PlanSchreiber
import de.foody.server.web.Schreiben
import de.foody.server.web.Sitzung
import de.foody.server.web.WebRezept
import de.foody.server.web.abgelehnt
import de.foody.server.web.csrfAus
import de.foody.server.web.csrfGueltig
import de.foody.server.web.formular
import de.foody.server.web.formularVeraltet
import de.foody.server.web.nichtGefunden
import de.foody.server.web.seiteAntworten
import de.foody.server.web.sitzungMitHaushalt
import de.foody.server.web.text
import de.foody.server.web.weiterleiten
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import kotlinx.html.stream.createHTML
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.ul

/** Einplanen und Planeinträge ändern; Anzeige des Plans in [PlanTagebuchSeiten]. */
internal object PlanSeiten {
    private val TAG = DateTimeFormatter.ofPattern("EE, dd.MM.", Locale.GERMAN)
    const val MAX_PORTIONEN = 99

    /** Erlaubter Zeitraum für Datumsangaben: ein Jahr um heute. */
    fun datumErlaubt(d: LocalDate, heute: LocalDate) = !d.isBefore(heute.minusYears(1)) && !d.isAfter(heute.plusYears(1))

    fun planUrl(ab: LocalDate, tage: Int, anker: LocalDate? = null, hinweis: String? = null, bearbeiten: Boolean = false) = buildString {
        append("/plan?ab=").append(ab)
        if (tage == 14) append("&tage=14")
        if (bearbeiten) append("&bearbeiten=1")
        if (hinweis != null) append("&hinweis=").append(hinweis)
        if (anker != null) append("#tag-").append(anker)
    }

    // --- Aktionen auf Planeinträgen ---

    /** `POST /plan/aktion` (Feld `a` = „<id> <aktion>“, ein Formular für die ganze Seite) oder `POST /plan/{id}` (Feld `aktion`). */
    suspend fun aktion(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, heute: LocalDate, pfadId: String?) {
        val s = call.sitzungMitHaushalt(deps) ?: return
        val form = call.formular()
        if (!s.csrfGueltig(call.csrfAus(form))) return call.formularVeraltet()
        val household = s.haushalt!!
        val (id, aktion) = if (pfadId != null) pfadId to form["aktion"].orEmpty()
        else form["a"].orEmpty().split(' ', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val vorher = daten.fuer(household).plan.firstOrNull { it.id == id }
        val tage = if (form["tage"] == "14") 14 else 7
        val bearbeiten = form["bearbeiten"] == "1"
        val ab = form["ab"]?.let(::datumOderNull) ?: vorher?.datum ?: heute
        val schreiber = PlanSchreiber(deps, daten)
        val ergebnis = if (aktion == "gekocht") schreiber.gekocht(household, id) else schreiber.aendern(household, id, aktion)
        when (ergebnis) {
            Schreiben.NichtGefunden -> call.nichtGefunden(s, "Diesen Planeintrag gibt es nicht.")
            Schreiben.Weg -> call.weiterleiten(planUrl(ab, tage, hinweis = "weg", bearbeiten = bearbeiten))
            Schreiben.Abgelehnt -> call.abgelehnt(s)
            Schreiben.Ok -> {
                val anker = when (aktion) {
                    "loeschen" -> null
                    "frueher" -> vorher?.datum?.minusDays(1)
                    "spaeter" -> vorher?.datum?.plusDays(1)
                    else -> vorher?.datum
                }
                call.weiterleiten(planUrl(ab, tage, anker, bearbeiten = bearbeiten))
            }
        }
    }

    // --- Einplanen ---

    private data class Eingabe(val tag: String, val mahlzeit: String, val portionen: String)

    suspend fun einplanen(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, heute: LocalDate) {
        val s = call.sitzungMitHaushalt(deps) ?: return
        val form = call.formular()
        if (!s.csrfGueltig(call.csrfAus(form))) return call.formularVeraltet()
        val household = s.haushalt!!
        // Keine Auswahl (alter Browser, `required` umgangen): Hinweis auf der Auswahlseite statt „nicht gefunden“
        if (form["rezept"].isNullOrBlank()) return neu(call, s, daten.fuer(household), heute, form, "Bitte ein Rezept auswählen.")
        val rezept = daten.fuer(household).rezepte[form["rezept"].orEmpty()]
            ?: return call.nichtGefunden(s, "Dieses Rezept gibt es nicht (mehr).")
        val e = Eingabe(form["tag"].orEmpty().trim(), form["mahlzeit"].orEmpty(), form["portionen"].orEmpty().trim())
        val fehler = mutableListOf<String>()
        val tag = datumOderNull(e.tag)
        if (tag == null) fehler += "Bitte ein gültiges Datum wählen." else if (!datumErlaubt(tag, heute)) fehler += "Datum höchstens ein Jahr vor oder nach heute."
        val mahlzeit = Mahlzeit.entries.firstOrNull { it.name == e.mahlzeit }
        if (mahlzeit == null) fehler += "Bitte eine Mahlzeit wählen."
        val portionen = if (e.portionen.isEmpty()) rezept.domain.defaultServings else e.portionen.toIntOrNull()
        if (portionen == null || portionen !in 1..MAX_PORTIONEN) fehler += "Portionen: 1 bis $MAX_PORTIONEN."
        if (fehler.isNotEmpty()) return einplanenSeite(call, s, rezept, e, fehler)
        when (PlanSchreiber(deps, daten).einplanen(household, tag!!, mahlzeit!!, rezept.id, portionen!!)) {
            Schreiben.Ok -> call.weiterleiten(planUrl(tag, 7, tag))
            Schreiben.NichtGefunden -> call.nichtGefunden(s, "Dieses Rezept gibt es nicht (mehr).")
            Schreiben.Weg -> einplanenSeite(call, s, rezept, e, listOf("Das Rezept ist archiviert."))
            Schreiben.Abgelehnt -> call.abgelehnt(s)
        }
    }

    private suspend fun einplanenSeite(call: ApplicationCall, s: Sitzung, r: WebRezept, e: Eingabe, fehler: List<String>) =
        call.seiteAntworten("Einplanen", s, Navi.PLAN) {
            h1 { +"Einplanen: ${r.payload.name}" }
            for (f in fehler) p("hinweis fehler") { +f }
            einplanenFormular(s, r, e.tag, Mahlzeit.entries.firstOrNull { it.name == e.mahlzeit }, e.portionen)
        }

    /** Kleines Formular „Einplanen“ für ein Rezept (Rezeptseite und Fehlerseite). */
    fun FlowContent.einplanenFormular(s: Sitzung, r: WebRezept, tag: String, mahlzeit: Mahlzeit?, portionen: String) {
        form(action = "/plan/einplanen", method = FormMethod.post, classes = "karte zeile") {
            hiddenInput(name = "_csrf") { value = s.csrf }
            hiddenInput(name = "rezept") { value = r.id }
            label { +"Tag"; input(InputType.date, name = "tag") { value = tag; required = true } }
            mahlzeitAuswahl(mahlzeit ?: Mahlzeit.ABENDESSEN)
            label { +"Portionen"; input(InputType.number, name = "portionen", classes = "menge") {
                value = portionen; placeholder = r.domain.defaultServings.toString(); min = "1"; max = "$MAX_PORTIONEN"
            } }
            button { +"Einplanen" }
        }
    }

    private fun FlowContent.mahlzeitAuswahl(gewaehlt: Mahlzeit) {
        select {
            name = "mahlzeit"; attributes["aria-label"] = "Mahlzeit"
            for (m in Mahlzeit.entries) option { value = m.name; selected = m == gewaehlt; +m.text() }
        }
    }

    // --- Auswahlseite /plan/neu ---

    private data class Auswahl(val tag: LocalDate, val mahlzeit: Mahlzeit, val alle: Boolean, val q: String)

    private fun ApplicationCall.auswahl(h: Haushalt, heute: LocalDate, q: io.ktor.http.Parameters = request.queryParameters): Auswahl {
        val tag = q["tag"]?.let(::datumOderNull)?.takeIf { datumErlaubt(it, heute) } ?: heute
        val belegt = h.plan.filter { it.datum == tag }.mapNotNull { Mahlzeit.ausText(it.mahlzeit) }.toSet()
        val mahlzeit = Mahlzeit.entries.firstOrNull { it.name == q["mahlzeit"] }
            ?: Mahlzeit.entries.firstOrNull { it !in belegt } ?: Mahlzeit.ABENDESSEN
        return Auswahl(tag, mahlzeit, q["alle"] == "1", q["q"].orEmpty().trim().take(100))
    }

    suspend fun neu(call: ApplicationCall, s: Sitzung, h: Haushalt, heute: LocalDate, werte: io.ktor.http.Parameters? = null, fehler: String? = null) {
        val a = if (werte == null) call.auswahl(h, heute) else call.auswahl(h, heute, werte)
        call.seiteAntworten("Einplanen", s, Navi.PLAN) {
            fehler?.let { p("hinweis fehler") { +it } }
            h1 { +"Einplanen · ${a.tag.format(TAG)}" }
            // Filter per GET (ohne JS „Anzeigen“); mit htmx wird nur die Liste ersetzt
            form(action = "/plan/neu", method = FormMethod.get, classes = "zeile karte") {
                attributes["hx-get"] = "/plan/neu/liste"
                attributes["hx-target"] = "#liste"
                attributes["hx-trigger"] = "input changed delay:150ms from:input, change from:select, submit"
                hiddenInput(name = "tag") { value = a.tag.toString() }
                if (a.alle) hiddenInput(name = "alle") { value = "1" }
                mahlzeitAuswahl(a.mahlzeit)
                input(InputType.search, name = "q") { value = a.q; placeholder = "Name, Tag oder Zutat"; attributes["aria-label"] = "Suche" }
                button(classes = "leise") { +"Anzeigen" }
            }
            form(action = "/plan/einplanen", method = FormMethod.post) {
                hiddenInput(name = "_csrf") { value = s.csrf }
                hiddenInput(name = "tag") { value = a.tag.toString() }
                div("zeile") {
                    label { +"Portionen"; input(InputType.number, name = "portionen", classes = "menge") {
                        placeholder = "wie im Rezept"; min = "1"; max = "$MAX_PORTIONEN"
                    } }
                    button { +"Einplanen" }
                    a(href = "/plan/neu?tag=${a.tag}&mahlzeit=${a.mahlzeit.name}${if (a.alle) "" else "&alle=1"}") {
                        +(if (a.alle) "Nur passende" else "Alle zeigen")
                    }
                }
                div { id = "liste"; auswahlListe(h, a) }
            }
        }
    }

    suspend fun neuListe(call: ApplicationCall, h: Haushalt, heute: LocalDate) {
        val a = call.auswahl(h, heute)
        call.respondText(createHTML(prettyPrint = false).div { auswahlListe(h, a) }.removePrefix("<div>").removeSuffix("</div>"), ContentType.Text.Html)
    }

    private fun FlowContent.auswahlListe(h: Haushalt, a: Auswahl) {
        // Mahlzeit gehört zum Einplanen-Formular, wechselt aber mit dem Filter – deshalb hier im ersetzbaren Teil
        hiddenInput(name = "mahlzeit") { value = a.mahlzeit.name }
        val aktive = h.rezepte.values.filter { it.payload.archivedAt == null }
        val treffer = PlanAuswahl.filtern(aktive, { it.einordnung }, a.mahlzeit, emptySet(), a.alle)
            .filter { a.q.isEmpty() || RezeptSeiten.trifft(it, a.q, h) }
            .sortedBy { it.payload.name.lowercase() }
        if (treffer.isEmpty()) {
            p("leise") { +(if (aktive.isEmpty()) "Noch keine Rezepte." else "Keine passenden Rezepte.") }
            return
        }
        ul("liste karte") {
            for (r in treffer) li {
                label {
                    input(InputType.radio, name = "rezept") { value = r.id; required = true }
                    +" ${r.payload.name}"
                }
            }
        }
    }

    // --- Vorschläge ---

    private const val MAX_PAARE = 31

    suspend fun vorschlag(call: ApplicationCall, deps: ServerDeps, s: Sitzung, h: Haushalt, heute: LocalDate) {
        val q = call.request.queryParameters
        val ab = q["ab"]?.let(::datumOderNull)?.takeIf { datumErlaubt(it, heute) } ?: heute
        val tage = if (q["tage"] == "14") 14 else 7
        val mahlzeit = Mahlzeit.entries.firstOrNull { it.name == q["mahlzeit"] } ?: Mahlzeit.ABENDESSEN
        val seed = q["seed"]?.toLongOrNull() ?: deps.clock.millis()
        val zeitraum = (0L until tage.toLong()).map { ab.plusDays(it) }
        val belegt = h.plan.groupBy({ it.datum }, { it.mahlzeit })
        val leer = PlanVorschlag.leereTage(zeitraum, heute, belegt, mahlzeit)
        val aktive = h.rezepte.values.filter { it.payload.archivedAt == null }
        val rezepte = aktive.map { r ->
            val pflicht = r.domain.ingredients.filter { !it.optional }.map {
                PantryCoverage.Requirement(r.id, it.ingredientId, h.zutaten[it.ingredientId]?.name.orEmpty())
            }
            PlanVorschlag.Rezept(r.id, r.payload.favorite, r.einordnung.mahlzeiten, pflicht)
        }
        val imVorrat = h.vorrat.filter { it.menge.signum() > 0 }.map { it.zutatId }.toSet()
        val paare = PlanVorschlag.vorschlagen(rezepte, leer, imVorrat, h.plan.map { it.rezeptId to it.datum }, zeitraum.last(), seed, mahlzeit)
            .toList().sortedBy { it.first }
        val zurueck = planUrl(ab, tage)
        call.seiteAntworten("Vorschlag", s, Navi.PLAN) {
            h1 { +"Vorschlag: ${mahlzeit.text()}" }
            if (paare.isEmpty()) {
                p("hinweis") {
                    +(if (aktive.isEmpty()) "Nichts vorzuschlagen – es gibt noch keine Rezepte."
                    else "Nichts vorzuschlagen – ab heute ist ${mahlzeit.text()} an allen Tagen geplant oder es passt kein Rezept.")
                }
                a(href = zurueck) { +"Zurück zum Plan" }
                return@seiteAntworten
            }
            form(action = "/plan/vorschlag", method = FormMethod.post) {
                hiddenInput(name = "_csrf") { value = s.csrf }
                hiddenInput(name = "mahlzeit") { value = mahlzeit.name }
                hiddenInput(name = "ab") { value = ab.toString() }
                if (tage == 14) hiddenInput(name = "tage") { value = "14" }
                ul("liste karte") {
                    paare.forEachIndexed { i, (tag, id) ->
                        li {
                            hiddenInput(name = "tag_$i") { value = tag.toString() }
                            hiddenInput(name = "rezept_$i") { value = id }
                            span { +tag.format(TAG) }
                            a(href = "/rezept/$id") { +h.rezepte.getValue(id).payload.name }
                        }
                    }
                }
                div("zeile") {
                    button { +"Übernehmen" }
                    a(href = "/plan/vorschlag?ab=$ab${if (tage == 14) "&tage=14" else ""}&mahlzeit=${mahlzeit.name}&seed=${seed + 1}", classes = "knopf") { +"Neu mischen" }
                    a(href = zurueck) { +"Abbrechen" }
                }
            }
        }
    }

    suspend fun vorschlagUebernehmen(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, heute: LocalDate) {
        val s = call.sitzungMitHaushalt(deps) ?: return
        val form = call.formular()
        if (!s.csrfGueltig(call.csrfAus(form))) return call.formularVeraltet()
        val ab = form["ab"]?.let(::datumOderNull)
        val tage = if (form["tage"] == "14") 14 else 7
        val mahlzeit = Mahlzeit.entries.firstOrNull { it.name == form["mahlzeit"] }
        val paare = (0 until MAX_PAARE).mapNotNull { i ->
            val tag = form["tag_$i"] ?: return@mapNotNull null
            (datumOderNull(tag) ?: LocalDate.MIN) to form["rezept_$i"].orEmpty()
        }
        val ergebnis = if (ab == null || mahlzeit == null || paare.isEmpty() || !datumErlaubt(ab, heute)) Schreiben.NichtGefunden
        else PlanSchreiber(deps, daten).vorschlagUebernehmen(s.haushalt!!, mahlzeit, paare, ab, ab.plusDays(tage - 1L))
        when (ergebnis) {
            Schreiben.Ok -> call.weiterleiten(planUrl(ab!!, tage))
            Schreiben.Abgelehnt -> call.abgelehnt(s)
            else -> call.seiteAntworten("Vorschlag", s, Navi.PLAN, HttpStatusCode.BadRequest) {
                h1 { +"Vorschlag passt nicht mehr" }
                p { +"Ein Rezept oder Tag aus dem Vorschlag gibt es so nicht mehr. Es wurde nichts eingeplant – bitte neu vorschlagen." }
                a(href = ab?.let { planUrl(it, tage) } ?: "/plan") { +"Zurück zum Plan" }
            }
        }
    }

    private fun datumOderNull(text: String): LocalDate? = try { LocalDate.parse(text) } catch (_: DateTimeParseException) { null }
}

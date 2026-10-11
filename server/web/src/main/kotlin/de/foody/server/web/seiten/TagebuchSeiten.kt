package de.foody.server.web.seiten

import de.foody.domain.Mahlzeit
import de.foody.domain.MeasureUnit
import de.foody.domain.Tagebuch
import de.foody.domain.TagebuchArt
import de.foody.server.ServerDeps
import de.foody.server.web.Eingaben
import de.foody.server.web.Haushalt
import de.foody.server.web.HaushaltsDaten
import de.foody.server.web.Navi
import de.foody.server.web.Schreiben
import de.foody.server.web.Sitzung
import de.foody.server.web.TagebuchSchreiber
import de.foody.server.web.WebTagebuch
import de.foody.server.web.ZutatEintragen
import de.foody.server.web.abgelehnt
import de.foody.server.web.csrfAus
import de.foody.server.web.csrfGueltig
import de.foody.server.web.formular
import de.foody.server.web.nichtGefunden
import de.foody.server.web.seiteAntworten
import de.foody.server.web.sitzungMitHaushalt
import de.foody.server.web.text
import de.foody.server.web.weiterleiten
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.dataList
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
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

/** Tagebuch-Einträge eintragen, ändern und löschen; die Tagesansicht steht in [PlanTagebuchSeiten]. */
internal object TagebuchSeiten {
    private val TAG = DateTimeFormatter.ofPattern("EE, dd.MM.", Locale.GERMAN)
    private const val MAX_PORTIONEN = 99
    private const val MAX_KCAL = 10_000
    private const val MAX_NAME = 200

    private fun datum(text: String?): LocalDate? = text?.let { try { LocalDate.parse(it.trim()) } catch (_: DateTimeParseException) { null } }

    fun tagUrl(tag: LocalDate) = "/tagebuch?tag=$tag"

    // --- Prüfen ---

    private class Pruefung {
        val fehler = mutableListOf<String>()

        fun tag(text: String?, heute: LocalDate): LocalDate? {
            val d = datum(text)
            if (d == null) fehler += "Bitte ein gültiges Datum wählen." else if (!PlanSeiten.datumErlaubt(d, heute)) fehler += "Datum höchstens ein Jahr vor oder nach heute."
            return d
        }

        fun mahlzeit(text: String?): Mahlzeit? = Mahlzeit.entries.firstOrNull { it.name == text }.also { if (it == null) fehler += "Bitte eine Mahlzeit wählen." }

        fun portionen(text: String?): BigDecimal? = Eingaben.zahl(text.orEmpty())?.takeIf { it.signum() > 0 && it <= BigDecimal(MAX_PORTIONEN) }
            .also { if (it == null) fehler += "Portionen: mehr als 0 und höchstens $MAX_PORTIONEN." }

        fun menge(text: String?): BigDecimal? = Eingaben.zahl(text.orEmpty())?.takeIf { it.signum() > 0 }
            .also { if (it == null) fehler += "Bitte eine Menge größer 0 eingeben." }

        fun einheit(text: String?): MeasureUnit? = Eingaben.einheit(text.orEmpty()).also { if (it == null) fehler += "Unbekannte Einheit „${text.orEmpty().trim()}“." }

        fun name(text: String?): String? {
            val n = text.orEmpty().trim()
            if (n.isEmpty()) fehler += "Bitte einen Namen eingeben." else if (n.length > MAX_NAME) fehler += "Der Name ist zu lang (höchstens $MAX_NAME Zeichen)."
            return n.takeIf { it.isNotEmpty() && it.length <= MAX_NAME }
        }

        fun kcal(text: String?): Int? = text.orEmpty().trim().toIntOrNull()?.takeIf { it in 0..MAX_KCAL }
            .also { if (it == null) fehler += "kcal: ganze Zahl von 0 bis 10 000." }

        /** Optionaler Nährwert in g: leer = unbekannt. */
        fun gramm(bezeichnung: String, text: String?): BigDecimal? {
            if (text.isNullOrBlank()) return null
            return Eingaben.zahl(text).also { if (it == null) fehler += "$bezeichnung: bitte eine Zahl ≥ 0." }
        }
    }

    // --- Eintragen ---

    suspend fun neu(call: ApplicationCall, s: Sitzung, h: Haushalt, heute: LocalDate, werte: Parameters = call.request.queryParameters, fehler: List<String> = emptyList()) {
        val tag = datum(werte["tag"])?.takeIf { PlanSeiten.datumErlaubt(it, heute) } ?: heute
        val mahlzeit = Mahlzeit.entries.firstOrNull { it.name == werte["mahlzeit"] } ?: Mahlzeit.ABENDESSEN
        val q = call.request.queryParameters["q"].orEmpty().trim().take(100)
        call.seiteAntworten("Eintragen", s, Navi.TAGEBUCH) {
            h1 { +"Eintragen · ${mahlzeit.text()} · ${tag.format(TAG)}" }
            for (f in fehler) p("hinweis fehler") { +f }
            h2 { +"Rezept" }
            form(action = "/tagebuch/neu", method = FormMethod.get, classes = "zeile") {
                attributes["hx-get"] = "/tagebuch/neu/liste"
                attributes["hx-target"] = "#liste"
                attributes["hx-trigger"] = "input changed delay:150ms from:input, submit"
                hiddenInput(name = "tag") { value = tag.toString() }
                hiddenInput(name = "mahlzeit") { value = mahlzeit.name }
                input(InputType.search, name = "q") { value = q; placeholder = "Rezept suchen"; attributes["aria-label"] = "Rezept suchen" }
            }
            form(action = "/tagebuch/rezept", method = FormMethod.post, classes = "karte") {
                kopf(s, tag, mahlzeit)
                div("zeile") {
                    label { +"Portionen"; input(InputType.text, name = "portionen", classes = "menge") { value = werte["portionen"] ?: "1"; attributes["inputmode"] = "decimal" } }
                    button { +"Eintragen" }
                }
                div { id = "liste"; rezeptListe(h, q) }
            }
            h2 { +"Zutat" }
            form(action = "/tagebuch/zutat", method = FormMethod.post, classes = "karte zeile") {
                kopf(s, tag, mahlzeit)
                input(InputType.text, name = "zutat") { value = werte["zutat"].orEmpty(); placeholder = "Zutat"; attributes["list"] = "zutaten"; attributes["aria-label"] = "Zutat" }
                input(InputType.text, name = "menge", classes = "menge") { value = werte["menge"].orEmpty(); placeholder = "Menge"; attributes["inputmode"] = "decimal"; attributes["aria-label"] = "Menge" }
                input(InputType.text, name = "einheit", classes = "einheit") { value = werte["einheit"] ?: MeasureUnit.GRAM.symbol; attributes["list"] = "einheiten"; attributes["aria-label"] = "Einheit" }
                button { +"Eintragen" }
            }
            dataList { id = "einheiten"; for (u in MeasureUnit.entries) option { value = u.symbol } }
            div { attributes["hx-get"] = "/rezept/zutatenliste"; attributes["hx-trigger"] = "load"; attributes["hx-swap"] = "outerHTML" }
            h2 { +"Frei" }
            form(action = "/tagebuch/frei", method = FormMethod.post, classes = "karte") {
                kopf(s, tag, mahlzeit)
                freiFelder(werte["name"].orEmpty(), werte["kcal"].orEmpty(), werte["eiweiss"].orEmpty(), werte["kh"].orEmpty(), werte["fett"].orEmpty())
                div("zeile") { button { +"Eintragen" }; a(href = tagUrl(tag)) { +"Zurück" } }
            }
        }
    }

    private fun FlowContent.kopf(s: Sitzung, tag: LocalDate, mahlzeit: Mahlzeit) {
        hiddenInput(name = "_csrf") { value = s.csrf }
        hiddenInput(name = "tag") { value = tag.toString() }
        hiddenInput(name = "mahlzeit") { value = mahlzeit.name }
    }

    private fun FlowContent.freiFelder(name: String, kcal: String, eiweiss: String, kh: String, fett: String) {
        label { +"Name"; input(InputType.text, name = "name", classes = "breit") { value = name } }
        div("zeile") {
            label { +"kcal"; input(InputType.text, name = "kcal", classes = "menge") { value = kcal; attributes["inputmode"] = "numeric" } }
            label { +"Eiweiß (g)"; input(InputType.text, name = "eiweiss", classes = "menge") { value = eiweiss; attributes["inputmode"] = "decimal" } }
            label { +"KH (g)"; input(InputType.text, name = "kh", classes = "menge") { value = kh; attributes["inputmode"] = "decimal" } }
            label { +"Fett (g)"; input(InputType.text, name = "fett", classes = "menge") { value = fett; attributes["inputmode"] = "decimal" } }
        }
    }

    private fun FlowContent.rezeptListe(h: Haushalt, q: String) {
        val treffer = h.rezepte.values.filter { it.payload.archivedAt == null && (q.isEmpty() || RezeptSeiten.trifft(it, q, h)) }.sortedBy { it.payload.name.lowercase() }
        if (treffer.isEmpty()) {
            p("leise") { +"Keine Rezepte gefunden." }
            return
        }
        ul("liste") {
            for (r in treffer) li { label { input(InputType.radio, name = "rezept") { value = r.id; required = true }; +" ${r.payload.name}" } }
        }
    }

    suspend fun neuListe(call: ApplicationCall, h: Haushalt) {
        val q = call.request.queryParameters["q"].orEmpty().trim().take(100)
        call.respondText(createHTML(prettyPrint = false).div { rezeptListe(h, q) }.removePrefix("<div>").removeSuffix("</div>"), ContentType.Text.Html)
    }

    private suspend fun ApplicationCall.formularMitCsrf(deps: ServerDeps): Pair<Sitzung, Parameters>? {
        val s = sitzungMitHaushalt(deps) ?: return null
        val form = formular()
        if (!s.csrfGueltig(csrfAus(form))) { respond(HttpStatusCode.Forbidden); return null }
        return s to form
    }

    private suspend fun antwort(call: ApplicationCall, s: Sitzung, r: Schreiben, tag: LocalDate?, text: String = "Diesen Eintrag gibt es nicht (mehr).") = when (r) {
        Schreiben.Ok -> call.weiterleiten(tag?.let(::tagUrl) ?: "/tagebuch")
        Schreiben.Weg -> call.weiterleiten("/tagebuch?${tag?.let { "tag=$it&" }.orEmpty()}hinweis=weg")
        Schreiben.NichtGefunden -> call.nichtGefunden(s, text)
        Schreiben.Abgelehnt -> call.abgelehnt(s)
    }

    suspend fun gegessen(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten) {
        val (s, form) = call.formularMitCsrf(deps) ?: return
        val planId = form["plan"].orEmpty()
        val tag = daten.fuer(s.haushalt!!).plan.firstOrNull { it.id == planId }?.datum
        antwort(call, s, TagebuchSchreiber(deps, daten).gegessen(s.haushalt!!, planId), tag, "Diesen Planeintrag (oder sein Rezept) gibt es nicht (mehr).")
    }

    suspend fun rezept(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, heute: LocalDate) {
        val (s, form) = call.formularMitCsrf(deps) ?: return
        val h = daten.fuer(s.haushalt!!)
        if (h.rezepte[form["rezept"].orEmpty()] == null) return call.nichtGefunden(s, "Dieses Rezept gibt es nicht (mehr).")
        val p = Pruefung()
        val tag = p.tag(form["tag"], heute)
        val mahlzeit = p.mahlzeit(form["mahlzeit"])
        val portionen = p.portionen(form["portionen"])
        if (p.fehler.isNotEmpty()) return neu(call, s, h, heute, form, p.fehler)
        antwort(call, s, TagebuchSchreiber(deps, daten).rezept(s.haushalt!!, tag!!, mahlzeit!!, form["rezept"]!!, portionen!!), tag)
    }

    suspend fun zutat(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, heute: LocalDate) {
        val (s, form) = call.formularMitCsrf(deps) ?: return
        val h = daten.fuer(s.haushalt!!)
        val p = Pruefung()
        val tag = p.tag(form["tag"], heute)
        val mahlzeit = p.mahlzeit(form["mahlzeit"])
        val name = p.name(form["zutat"])
        val menge = p.menge(form["menge"])
        val einheit = p.einheit(form["einheit"])
        if (p.fehler.isNotEmpty()) return neu(call, s, h, heute, form, p.fehler)
        when (val r = TagebuchSchreiber(deps, daten).zutat(s.haushalt!!, tag!!, mahlzeit!!, name!!, menge!!, einheit!!)) {
            is ZutatEintragen.Ergebnis -> antwort(call, s, r.schreiben, tag)
            ZutatEintragen.Unbekannt -> neu(call, s, h, heute, form, listOf("„$name“ gibt es nicht im Zutatenkatalog – bitte dort anlegen oder frei eintragen."))
            ZutatEintragen.NichtBerechenbar -> neu(call, s, h, heute, form, listOf("Keine Nährwerte oder Einheit nicht umrechenbar – bitte frei eintragen."))
        }
    }

    suspend fun frei(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, heute: LocalDate) {
        val (s, form) = call.formularMitCsrf(deps) ?: return
        val p = Pruefung()
        val tag = p.tag(form["tag"], heute)
        val mahlzeit = p.mahlzeit(form["mahlzeit"])
        val name = p.name(form["name"])
        val kcal = p.kcal(form["kcal"])
        val eiweiss = p.gramm("Eiweiß", form["eiweiss"])
        val kh = p.gramm("Kohlenhydrate", form["kh"])
        val fett = p.gramm("Fett", form["fett"])
        if (p.fehler.isNotEmpty()) return neu(call, s, daten.fuer(s.haushalt!!), heute, form, p.fehler)
        antwort(call, s, TagebuchSchreiber(deps, daten).frei(s.haushalt!!, tag!!, mahlzeit!!, name!!, kcal!!, eiweiss, kh, fett), tag)
    }

    // --- Ändern ---

    suspend fun eintrag(call: ApplicationCall, s: Sitzung, h: Haushalt, id: String, werte: Parameters? = null, fehler: List<String> = emptyList()) {
        val e = h.tagebuch.firstOrNull { it.id == id } ?: return call.nichtGefunden(s, "Diesen Eintrag gibt es nicht (mehr).")
        fun wert(feld: String, vorgabe: String) = werte?.get(feld) ?: vorgabe
        call.seiteAntworten(e.name, s, Navi.TAGEBUCH) {
            h1 { +e.name }
            p("leise") { +"${e.datum.format(TAG)} · ${e.werte.kcal?.let { "$it kcal" } ?: "–"}" }
            for (f in fehler) p("hinweis fehler") { +f }
            form(action = "/tagebuch/${e.id}", method = FormMethod.post, classes = "karte") {
                hiddenInput(name = "_csrf") { value = s.csrf }
                label {
                    +"Mahlzeit"
                    select { name = "mahlzeit"; for (m in Mahlzeit.entries) option { value = m.name; selected = m.name == wert("mahlzeit", e.mahlzeit.name); +m.text() } }
                }
                when (e.art) {
                    TagebuchArt.REZEPT -> label { +"Portionen"; input(InputType.text, name = "portionen", classes = "menge") { value = wert("portionen", e.portionen?.let(Eingaben::anzeigen).orEmpty()) } }
                    TagebuchArt.ZUTAT -> label {
                        +"Menge (${e.einheit?.symbol.orEmpty()})"
                        input(InputType.text, name = "menge", classes = "menge") { value = wert("menge", e.menge?.let(Eingaben::anzeigen).orEmpty()) }
                    }
                    TagebuchArt.FREI -> freiFelder(
                        wert("name", e.name), wert("kcal", e.werte.kcal?.toString().orEmpty()), wert("eiweiss", e.werte.eiweiss?.let(Eingaben::anzeigen).orEmpty()),
                        wert("kh", e.werte.kohlenhydrate?.let(Eingaben::anzeigen).orEmpty()), wert("fett", e.werte.fett?.let(Eingaben::anzeigen).orEmpty()),
                    )
                }
                div("zeile") { button { +"Speichern" }; a(href = tagUrl(e.datum)) { +"Zurück" } }
            }
            form(action = "/tagebuch/${e.id}/loeschen", method = FormMethod.post, classes = "zeile") {
                hiddenInput(name = "_csrf") { value = s.csrf }
                button(classes = "leise") { +"Löschen" }
            }
        }
    }

    suspend fun aendern(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, id: String) {
        val (s, form) = call.formularMitCsrf(deps) ?: return
        val h = daten.fuer(s.haushalt!!)
        val schreiber = TagebuchSchreiber(deps, daten)
        // Nicht (mehr) lesbar: der Schreiber entscheidet zwischen 404 und „gibt es nicht mehr“, ohne zu schreiben
        val e: WebTagebuch = h.tagebuch.firstOrNull { it.id == id }
            ?: return antwort(call, s, schreiber.aendern(s.haushalt!!, id, Mahlzeit.ABENDESSEN, null, null, null, null), null)
        val p = Pruefung()
        val mahlzeit = p.mahlzeit(form["mahlzeit"])
        var portionen: BigDecimal? = null
        var menge: BigDecimal? = null
        var name: String? = null
        var frei: de.foody.domain.Naehrwerte? = null
        when (e.art) {
            TagebuchArt.REZEPT -> portionen = p.portionen(form["portionen"])
            TagebuchArt.ZUTAT -> menge = p.menge(form["menge"])
            TagebuchArt.FREI -> {
                name = p.name(form["name"])
                val kcal = p.kcal(form["kcal"])
                val eiweiss = p.gramm("Eiweiß", form["eiweiss"])
                val kh = p.gramm("Kohlenhydrate", form["kh"])
                val fett = p.gramm("Fett", form["fett"])
                if (kcal != null) frei = Tagebuch.freiWerte(kcal, eiweiss, kh, fett)
            }
        }
        if (p.fehler.isNotEmpty()) return eintrag(call, s, h, id, form, p.fehler)
        antwort(call, s, schreiber.aendern(s.haushalt!!, id, mahlzeit!!, portionen, menge, name, frei), e.datum)
    }

    suspend fun loeschen(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, id: String) {
        val (s, _) = call.formularMitCsrf(deps) ?: return
        val tag = daten.fuer(s.haushalt!!).tagebuch.firstOrNull { it.id == id }?.datum
        antwort(call, s, TagebuchSchreiber(deps, daten).loeschen(s.haushalt!!, id), tag)
    }
}

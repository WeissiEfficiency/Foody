package de.foody.server.web.seiten

import de.foody.domain.DiffType
import de.foody.domain.EinkaufsAbgleich
import de.foody.domain.QuantityFormatter
import de.foody.domain.ShoppingNeed
import de.foody.server.ServerDeps
import de.foody.server.web.EinkaufSchreiber
import de.foody.server.web.Haushalt
import de.foody.server.web.HaushaltsDaten
import de.foody.server.web.Navi
import de.foody.server.web.Schreiben
import de.foody.server.web.Sitzung
import de.foody.server.web.abgelehnt
import de.foody.server.web.csrfAus
import de.foody.server.web.csrfGueltig
import de.foody.server.web.formular
import de.foody.server.web.formularVeraltet
import de.foody.server.web.nichtGefunden
import de.foody.server.web.seiteAntworten
import de.foody.server.web.sitzungMitHaushalt
import de.foody.server.web.weiterleiten
import io.ktor.server.application.ApplicationCall
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
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
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.ul

/** Einkaufsliste aus dem Wochenplan erzeugen und neu berechnen – Rechnung wie in der App. */
internal object ListeAusPlanSeiten {
    private const val MAX_TAGE = 31L
    private const val MAX_NAME = 200
    private val TAG = DateTimeFormatter.ofPattern("d.")
    private val TAG_MONAT = DateTimeFormatter.ofPattern("d.M.")

    fun vorgabeName(von: LocalDate, bis: LocalDate) = "Einkauf ${von.format(TAG)}–${bis.format(TAG_MONAT)}"

    private fun datum(text: String?): LocalDate? = text?.let { try { LocalDate.parse(it) } catch (_: DateTimeParseException) { null } }

    private fun zeitraumFehler(von: LocalDate?, bis: LocalDate?, heute: LocalDate): List<String> = when {
        von == null || bis == null -> listOf("Bitte gültige Daten wählen.")
        bis.isBefore(von) -> listOf("„Bis“ darf nicht vor „Von“ liegen.")
        ChronoUnit.DAYS.between(von, bis) + 1 > MAX_TAGE -> listOf("Höchstens $MAX_TAGE Tage.")
        !PlanSeiten.datumErlaubt(von, heute) || !PlanSeiten.datumErlaubt(bis, heute) -> listOf("Datum höchstens ein Jahr vor oder nach heute.")
        else -> emptyList()
    }

    private fun Haushalt.zutatName(id: String) = zutaten[id]?.name ?: id

    // --- Neue Liste ---

    private data class Eingabe(val von: String, val bis: String, val vorrat: Boolean, val name: String?)

    suspend fun neu(call: ApplicationCall, deps: ServerDeps, s: Sitzung, daten: HaushaltsDaten, heute: LocalDate) {
        val q = call.request.queryParameters
        val e = Eingabe(q["von"] ?: heute.toString(), q["bis"] ?: heute.plusDays(6).toString(), q["vorrat"] != "0", null)
        neuSeite(call, deps, s, daten, heute, e, emptyList())
    }

    private suspend fun neuSeite(call: ApplicationCall, deps: ServerDeps, s: Sitzung, daten: HaushaltsDaten, heute: LocalDate, e: Eingabe, fehler: List<String>) {
        val von = datum(e.von)
        val bis = datum(e.bis)
        val alleFehler = (zeitraumFehler(von, bis, heute) + fehler).distinct()
        val household = s.haushalt!!
        val h = daten.fuer(household)
        val bedarf = if (zeitraumFehler(von, bis, heute).isEmpty()) EinkaufSchreiber(deps, daten).bedarf(household, von!!, bis!!, e.vorrat) else null
        call.seiteAntworten("Neue Liste", s, Navi.EINKAUF) {
            h1 { +"Neue Liste aus dem Plan" }
            for (f in alleFehler) p("hinweis fehler") { +f }
            form(action = "/einkauf/neu", method = FormMethod.get, classes = "karte zeile") {
                label { +"Von"; input(InputType.date, name = "von") { value = e.von } }
                label { +"Bis"; input(InputType.date, name = "bis") { value = e.bis } }
                vorratAuswahl(e.vorrat)
                button(classes = "leise") { +"Vorschau" }
            }
            if (bedarf == null) return@seiteAntworten
            if (bedarf.isEmpty()) {
                p("hinweis") { +"Im Zeitraum ist nichts geplant." }
                return@seiteAntworten
            }
            form(action = "/einkauf/neu", method = FormMethod.post) {
                hiddenInput(name = "_csrf") { value = s.csrf }
                hiddenInput(name = "von") { value = e.von }
                hiddenInput(name = "bis") { value = e.bis }
                hiddenInput(name = "vorrat") { value = if (e.vorrat) "1" else "0" }
                label { +"Name"; input(InputType.text, name = "name", classes = "breit") { value = e.name ?: vorgabeName(von!!, bis!!); required = true } }
                ul("liste karte") { for (n in bedarf.sortedBy { h.zutatName(it.ingredientId).lowercase() }) bedarfZeile(h, n) }
                div("zeile") { button { +"Liste anlegen" }; a(href = "/einkauf") { +"Abbrechen" } }
            }
        }
    }

    private fun FlowContent.vorratAuswahl(vorrat: Boolean) {
        select {
            name = "vorrat"; attributes["aria-label"] = "Vorrat abziehen"
            option { value = "1"; selected = vorrat; +"Vorrat abziehen" }
            option { value = "0"; selected = !vorrat; +"Vorrat nicht abziehen" }
        }
    }

    private fun kotlinx.html.UL.bedarfZeile(h: Haushalt, n: ShoppingNeed) = li {
        val name = h.zutatName(n.ingredientId)
        if (n.toBuy.isZero()) {
            span("leise") { +"$name · aus Vorrat gedeckt" }
            return@li
        }
        label {
            input(InputType.checkBox, name = "mit") { value = n.ingredientId; checked = true }
            +" $name"
        }
        span {
            +QuantityFormatter.format(n.toBuy)
            if (!n.fromPantry.isZero()) span("leise") { +" (Bedarf ${QuantityFormatter.format(n.required)}, Vorrat −${QuantityFormatter.format(n.fromPantry)})" }
        }
    }

    suspend fun anlegen(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, heute: LocalDate) {
        val s = call.sitzungMitHaushalt(deps) ?: return
        val form = call.formular()
        if (!s.csrfGueltig(call.csrfAus(form))) return call.formularVeraltet()
        val e = Eingabe(form["von"].orEmpty(), form["bis"].orEmpty(), form["vorrat"] != "0", form["name"].orEmpty().trim())
        val von = datum(e.von)
        val bis = datum(e.bis)
        val fehler = zeitraumFehler(von, bis, heute).toMutableList()
        if (e.name.isNullOrEmpty()) fehler += "Bitte einen Namen eingeben." else if (e.name.length > MAX_NAME) fehler += "Name höchstens $MAX_NAME Zeichen."
        if (fehler.isNotEmpty()) return neuSeite(call, deps, s, daten, heute, e, fehler)
        val household = s.haushalt!!
        val schreiber = EinkaufSchreiber(deps, daten)
        val mit = form.getAll("mit").orEmpty().toSet()
        val ausgeschlossen = schreiber.bedarf(household, von!!, bis!!, e.vorrat).map { it.ingredientId }.toSet() - mit
        val (ergebnis, id) = schreiber.anlegen(household, e.name!!, von, bis, e.vorrat, ausgeschlossen)
        if (ergebnis != Schreiben.Ok) return call.abgelehnt(s)
        if (id == null) return call.seiteAntworten("Neue Liste", s, Navi.EINKAUF) {
            h1 { +"Nichts zu kaufen" }
            p { +"Für den Zeitraum ist nichts zu kaufen (nichts geplant, alles abgewählt oder aus dem Vorrat gedeckt)." }
            a(href = "/einkauf/neu?von=${e.von}&bis=${e.bis}") { +"Zurück" }
        }
        call.weiterleiten("/einkauf?liste=$id")
    }

    // --- Neu berechnen ---

    suspend fun abgleich(call: ApplicationCall, deps: ServerDeps, s: Sitzung, daten: HaushaltsDaten, listId: String) {
        val household = s.haushalt!!
        val h = daten.fuer(household)
        val vorrat = call.request.queryParameters["vorrat"] != "0"
        val eintraege = EinkaufSchreiber(deps, daten).abgleich(household, listId, vorrat)
            ?: return call.nichtGefunden(s, if (h.listen.any { it.id == listId }) "Diese Liste wurde nicht aus dem Plan erzeugt." else "Diese Liste gibt es nicht (mehr).")
        val liste = h.listen.first { it.id == listId }
        val neu = eintraege.filter { it.type == DiffType.ADDED }
        val geaendert = eintraege.filter { it.type == DiffType.CHANGED }
        val entfaellt = eintraege.filter { it.type == DiffType.REMOVED }
        call.seiteAntworten("Neu berechnen", s, Navi.EINKAUF) {
            h1 { +"Neu berechnen: ${liste.name}" }
            form(action = "/einkauf/$listId/abgleich", method = FormMethod.get, classes = "zeile") {
                vorratAuswahl(vorrat)
                button(classes = "leise") { +"Anzeigen" }
            }
            if (neu.isEmpty() && geaendert.isEmpty() && entfaellt.isEmpty()) {
                p("hinweis") { +"Liste ist aktuell." }
                a(href = "/einkauf?liste=$listId") { +"Zurück zur Liste" }
                return@seiteAntworten
            }
            if (neu.isNotEmpty()) {
                h2 { +"Neu" }
                ul("liste karte") { for (e in neu) li { span { +h.zutatName(e.new!!.ingredientId) }; span { +QuantityFormatter.format(e.new!!.toBuy) } } }
            }
            if (geaendert.isNotEmpty()) {
                h2 { +"Geändert" }
                ul("liste karte") {
                    for (e in geaendert) li {
                        val alt = EinkaufSchreiber.menge(e.old!!)
                        span {
                            +e.old!!.payload.name
                            if (e.old!!.payload.checked) span("leise") {
                                +(if (EinkaufsAbgleich.bleibtAbgehakt(true, alt, e.new!!.toBuy)) " · schon gekauft, bleibt abgehakt" else " · schon gekauft, kommt wieder auf die Liste")
                            }
                        }
                        span { +"${QuantityFormatter.format(alt)} → ${QuantityFormatter.format(e.new!!.toBuy)}" }
                    }
                }
            }
            if (entfaellt.isNotEmpty()) {
                h2 { +"Entfällt" }
                ul("liste karte") {
                    for (e in entfaellt) li {
                        span { +e.old!!.payload.name }
                        span("leise") { +(if (EinkaufsAbgleich.entfaelltLoeschen(e.old!!.payload.checked)) "wird entfernt" else "bleibt, schon gekauft") }
                    }
                }
            }
            form(action = "/einkauf/$listId/abgleich", method = FormMethod.post, classes = "zeile") {
                hiddenInput(name = "_csrf") { value = s.csrf }
                hiddenInput(name = "vorrat") { value = if (vorrat) "1" else "0" }
                button { +"Übernehmen" }
                a(href = "/einkauf?liste=$listId") { +"Abbrechen" }
            }
        }
    }

    suspend fun abgleichUebernehmen(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, listId: String) {
        val s = call.sitzungMitHaushalt(deps) ?: return
        val form = call.formular()
        if (!s.csrfGueltig(call.csrfAus(form))) return call.formularVeraltet()
        when (EinkaufSchreiber(deps, daten).abgleichAnwenden(s.haushalt!!, listId, form["vorrat"] != "0")) {
            Schreiben.Ok -> call.weiterleiten("/einkauf?liste=$listId")
            Schreiben.NichtGefunden -> call.nichtGefunden(s, "Diese Liste gibt es nicht (mehr).")
            Schreiben.Weg -> call.weiterleiten("/einkauf?hinweis=weg")
            Schreiben.Abgelehnt -> call.abgelehnt(s)
        }
    }
}

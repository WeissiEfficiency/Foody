package de.foody.server.web.seiten

import de.foody.domain.MeasureUnit
import de.foody.server.ServerDeps
import de.foody.server.web.Eingaben
import de.foody.server.web.Haushalt
import de.foody.server.web.HaushaltsDaten
import de.foody.server.web.Navi
import de.foody.server.web.Schreiben
import de.foody.server.web.Sitzung
import de.foody.server.web.VorratSchreiber
import de.foody.server.web.WebVorrat
import de.foody.server.web.abgelehnt
import de.foody.server.web.csrfAus
import de.foody.server.web.csrfGueltig
import de.foody.server.web.formular
import de.foody.server.web.formularVeraltet
import de.foody.server.web.menge
import de.foody.server.web.nichtGefunden
import de.foody.server.web.seiteAntworten
import de.foody.server.web.sitzungMitHaushalt
import de.foody.server.web.weiterleiten
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.dataList
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
import kotlinx.html.span
import kotlinx.html.ul

/** Vorrat: Liste ohne Formulare je Zeile (klein), Hinzufügen oben, Ändern/Löschen auf einer eigenen Seite. */
internal object VorratSeiten {
    private const val MAX_NAME = 200
    private val DATUM = DateTimeFormatter.ofPattern("dd.MM.yy")

    /** Formularwerte als Text (bleiben bei Fehlern stehen). */
    data class Eingabe(val zutat: String, val menge: String, val einheit: String, val mhd: String)

    private data class Geprueft(val menge: BigDecimal, val einheit: MeasureUnit, val mhd: LocalDate?)

    private fun Parameters.eingabe() = Eingabe(this["zutat"].orEmpty(), this["menge"].orEmpty(), this["einheit"].orEmpty(), this["mhd"].orEmpty())

    private fun pruefen(e: Eingabe, heute: LocalDate, mitZutat: Boolean): Pair<Geprueft?, List<String>> {
        val fehler = mutableListOf<String>()
        if (mitZutat) {
            if (e.zutat.isBlank()) fehler += "Bitte eine Zutat eingeben." else if (e.zutat.trim().length > MAX_NAME) fehler += "Zutat höchstens $MAX_NAME Zeichen."
        }
        val menge = Eingaben.zahl(e.menge)
        if (menge == null) fehler += "Bitte eine gültige Menge eingeben (Zahl ≥ 0)."
        val einheit = Eingaben.einheit(e.einheit)
        if (einheit == null) fehler += "Unbekannte Einheit „${e.einheit.trim()}“."
        val mhd = if (e.mhd.isBlank()) null else try { LocalDate.parse(e.mhd.trim()) } catch (_: DateTimeParseException) { null }
        if (e.mhd.isNotBlank() && mhd == null) fehler += "Bitte ein gültiges Datum wählen."
        else if (mhd != null && !PlanSeiten.datumErlaubt(mhd, heute)) fehler += "Datum höchstens ein Jahr vor oder nach heute."
        return (if (fehler.isEmpty()) Geprueft(menge!!, einheit!!, mhd) else null) to fehler
    }

    // --- Liste ---

    suspend fun liste(call: ApplicationCall, s: Sitzung, h: Haushalt, heute: LocalDate, eingabe: Eingabe? = null, fehler: List<String> = emptyList()) =
        call.seiteAntworten("Vorrat", s, Navi.VORRAT) {
            h1 { +"Vorrat" }
            div("zeile") { a(href = "/zutaten") { +"Zutatenkatalog" } }
            if (call.request.queryParameters["hinweis"] == "weg") p("hinweis") { +"Eintrag gibt es nicht mehr." }
            for (f in fehler) p("hinweis fehler") { attributes["role"] = "alert"; +f }
            val e = eingabe ?: Eingabe("", "", MeasureUnit.GRAM.symbol, "")
            form(action = "/vorrat", method = FormMethod.post, classes = "karte zeile") {
                hiddenInput(name = "_csrf") { value = s.csrf }
                input(InputType.text, name = "zutat") { value = e.zutat; placeholder = "Zutat"; attributes["list"] = "zutaten"; attributes["aria-label"] = "Zutat" }
                mengeFelder(e.menge, e.einheit)
                input(InputType.date, name = "mhd") { value = e.mhd; attributes["aria-label"] = "Haltbar bis" }
                button { +"Hinzufügen" }
            }
            einheitenListe()
            // Vorschläge aus dem Katalog wie im Rezept-Editor: per htmx nachgeladen
            div { attributes["hx-get"] = "/rezept/zutatenliste"; attributes["hx-trigger"] = "load"; attributes["hx-swap"] = "outerHTML" }
            val eintraege = h.vorrat.sortedBy { (h.zutaten[it.zutatId]?.name ?: "").lowercase() }
            if (eintraege.isEmpty()) p("leise") { +"Noch nichts im Vorrat." }
            else ul("liste karte") { for (v in eintraege) li { zeile(h, v, heute) } }
        }

    private fun FlowContent.zeile(h: Haushalt, v: WebVorrat, heute: LocalDate) {
        a(href = "/vorrat/${v.id}") { +(h.zutaten[v.zutatId]?.name ?: "Unbekannte Zutat") }
        span {
            +menge(v.menge, v.einheit)
            v.haltbarBis?.let { d ->
                span(if (d.isBefore(heute)) "fehler" else "leise") { +" · bis ${d.format(DATUM)}${if (d.isBefore(heute)) " · abgelaufen" else ""}" }
            }
        }
    }

    private fun FlowContent.mengeFelder(menge: String, einheit: String) {
        input(InputType.text, name = "menge", classes = "menge") { value = menge; placeholder = "Menge"; attributes["inputmode"] = "decimal"; attributes["aria-label"] = "Menge" }
        input(InputType.text, name = "einheit", classes = "einheit") { value = einheit; attributes["list"] = "einheiten"; attributes["aria-label"] = "Einheit" }
    }

    private fun FlowContent.einheitenListe() = dataList { id = "einheiten"; for (u in MeasureUnit.entries) option { value = u.symbol } }

    suspend fun anlegen(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, heute: LocalDate) {
        val s = call.sitzungMitHaushalt(deps) ?: return
        val form = call.formular()
        if (!s.csrfGueltig(call.csrfAus(form))) return call.formularVeraltet()
        val e = form.eingabe()
        val (g, fehler) = pruefen(e, heute, mitZutat = true)
        if (g == null) return liste(call, s, daten.fuer(s.haushalt!!), heute, e, fehler)
        when (VorratSchreiber(deps, daten).anlegen(s.haushalt!!, e.zutat.trim(), g.menge, g.einheit, g.mhd)) {
            Schreiben.Ok -> call.weiterleiten("/vorrat")
            else -> call.abgelehnt(s)
        }
    }

    // --- Ein Eintrag ---

    suspend fun eintrag(call: ApplicationCall, s: Sitzung, h: Haushalt, id: String, eingabe: Eingabe? = null, fehler: List<String> = emptyList()) {
        val v = h.vorrat.firstOrNull { it.id == id } ?: return call.nichtGefunden(s, "Diesen Vorratseintrag gibt es nicht (mehr).")
        val e = eingabe ?: Eingabe("", Eingaben.anzeigen(v.menge), v.einheit.symbol, v.haltbarBis?.toString().orEmpty())
        call.seiteAntworten("Vorrat", s, Navi.VORRAT) {
            h1 { +(h.zutaten[v.zutatId]?.name ?: "Unbekannte Zutat") }
            for (f in fehler) p("hinweis fehler") { attributes["role"] = "alert"; +f }
            form(action = "/vorrat/$id", method = FormMethod.post, classes = "karte zeile") {
                hiddenInput(name = "_csrf") { value = s.csrf }
                mengeFelder(e.menge, e.einheit)
                label { +"Haltbar bis"; input(InputType.date, name = "mhd") { value = e.mhd } }
                button { +"Speichern" }
            }
            einheitenListe()
            form(action = "/vorrat/$id/loeschen", method = FormMethod.post, classes = "zeile") {
                hiddenInput(name = "_csrf") { value = s.csrf }
                button(classes = "leise") { +"Löschen" }
                a(href = "/vorrat") { +"Zurück" }
            }
        }
    }

    suspend fun aendern(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, heute: LocalDate, id: String) {
        val s = call.sitzungMitHaushalt(deps) ?: return
        val form = call.formular()
        if (!s.csrfGueltig(call.csrfAus(form))) return call.formularVeraltet()
        val e = form.eingabe()
        val (g, fehler) = pruefen(e, heute, mitZutat = false)
        // Ungültige Eingabe: Formular mit Meldung (bzw. 404, wenn es den Eintrag nicht gibt)
        if (g == null) return eintrag(call, s, daten.fuer(s.haushalt!!), id, e, fehler)
        antworten(call, s, VorratSchreiber(deps, daten).aendern(s.haushalt!!, id, g.menge, g.einheit, g.mhd))
    }

    suspend fun loeschen(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, id: String) {
        val s = call.sitzungMitHaushalt(deps) ?: return
        val form = call.formular()
        if (!s.csrfGueltig(call.csrfAus(form))) return call.formularVeraltet()
        antworten(call, s, VorratSchreiber(deps, daten).loeschen(s.haushalt!!, id))
    }

    private suspend fun antworten(call: ApplicationCall, s: Sitzung, ergebnis: Schreiben) = when (ergebnis) {
        Schreiben.Ok -> call.weiterleiten("/vorrat")
        Schreiben.Weg -> call.weiterleiten("/vorrat?hinweis=weg")
        Schreiben.NichtGefunden -> call.nichtGefunden(s, "Diesen Vorratseintrag gibt es nicht (mehr).")
        Schreiben.Abgelehnt -> call.abgelehnt(s)
    }
}

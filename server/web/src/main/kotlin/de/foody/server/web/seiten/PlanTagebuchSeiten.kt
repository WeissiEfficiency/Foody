package de.foody.server.web.seiten

import de.foody.domain.DayNutrition
import de.foody.domain.EintragWerte
import de.foody.domain.Mahlzeit
import de.foody.domain.PlanDurchschnitt
import de.foody.domain.PlanTag
import de.foody.domain.Tagebuch
import de.foody.server.web.Haushalt
import de.foody.server.web.Navi
import de.foody.server.web.Sitzung
import de.foody.server.web.seiteAntworten
import de.foody.server.web.text
import io.ktor.server.application.ApplicationCall
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.hiddenInput
import kotlinx.html.select
import kotlinx.html.option
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.ul

/** Wochenplan und Tagebuch – Summen und Schnitt aus `domain` wie in der App. */
internal object PlanTagebuchSeiten {
    private val TAG = DateTimeFormatter.ofPattern("EE, dd.MM.", Locale.GERMAN)
    private val KURZ = DateTimeFormatter.ofPattern("EE", Locale.GERMAN)

    private fun ApplicationCall.datum(name: String, heute: LocalDate): LocalDate =
        request.queryParameters[name]?.let { t -> try { LocalDate.parse(t) } catch (_: DateTimeParseException) { null } } ?: heute

    suspend fun plan(call: ApplicationCall, s: Sitzung, h: Haushalt, heute: LocalDate) {
        val ab = call.datum("ab", heute)
        val anzahl = if (call.request.queryParameters["tage"] == "14") 14 else 7
        val tage = (0L until anzahl.toLong()).map { ab.plusDays(it) }
        val t = if (anzahl == 14) "&tage=14" else ""
        // Knöpfe je Eintrag nur im Bearbeiten-Modus – so bleibt die normale Ansicht auch mit 14 vollen Tagen klein
        val bearbeiten = call.request.queryParameters["bearbeiten"] == "1"
        val jeTag = h.plan.filter { it.datum in tage }.groupBy { it.datum }
        val naehrwerte = jeTag.mapNotNull { (tag, eintraege) ->
            DayNutrition.of(eintraege.mapNotNull { h.rezepte[it.rezeptId]?.domain }, h.zutaten)?.let { tag to it }
        }.toMap()
        val schnitt = PlanDurchschnitt.kcal(naehrwerte.map { (tag, n) -> PlanTag(jeTag[tag].orEmpty().map { it.mahlzeit }, n) })
        call.seiteAntworten("Plan", s, Navi.PLAN) {
            h1 { +"Wochenplan" }
            nav("zeile") {
                a(href = "/plan?ab=${ab.minusDays(7)}$t", classes = "knopf") { attributes["aria-label"] = "Vorige Woche"; +"‹" }
                a(href = "/plan?ab=$heute$t", classes = "knopf") { +"Heute" }
                a(href = "/plan?ab=${ab.plusDays(7)}$t", classes = "knopf") { attributes["aria-label"] = "Nächste Woche"; +"›" }
                a(href = "/plan?ab=$ab${if (anzahl == 14) "" else "&tage=14"}${if (bearbeiten) "&bearbeiten=1" else ""}") { +(if (anzahl == 14) "7 Tage" else "14 Tage") }
                a(href = "/plan?ab=$ab$t${if (bearbeiten) "" else "&bearbeiten=1"}") { +(if (bearbeiten) "Fertig" else "Bearbeiten") }
                schnitt?.let { d -> span("leise") { +"${if (d.vollstaendig) "" else "≥ "}Ø ${d.kcal} kcal pro Tag" } }
            }
            form(action = "/plan/vorschlag", method = FormMethod.get, classes = "zeile") {
                hiddenInput(name = "ab") { value = ab.toString() }
                if (anzahl == 14) hiddenInput(name = "tage") { value = "14" }
                select {
                    name = "mahlzeit"; attributes["aria-label"] = "Mahlzeit für den Vorschlag"
                    for (m in Mahlzeit.entries) option { value = m.name; selected = m == Mahlzeit.ABENDESSEN; +m.text() }
                }
                button(classes = "leise") { +"Vorschlagen" }
            }
            if (call.request.queryParameters["hinweis"] == "weg") p("hinweis") { +"Eintrag gibt es nicht mehr." }
            if (jeTag.isEmpty()) p("hinweis") { +"Nichts geplant in diesem Zeitraum." }
            // Ein Formular für alle Einträge: CSRF und Zeitraum nur einmal statt je Eintrag (Seite bleibt klein)
            form(action = "/plan/aktion", method = FormMethod.post) {
                hiddenInput(name = "_csrf") { value = s.csrf }
                hiddenInput(name = "ab") { value = ab.toString() }
                if (anzahl == 14) hiddenInput(name = "tage") { value = "14" }
                if (bearbeiten) hiddenInput(name = "bearbeiten") { value = "1" }
                for (tag in tage) {
                    div("karte") {
                        id = "tag-$tag"
                        h2 {
                            +tag.format(TAG)
                            naehrwerte[tag]?.let { n -> span("leise") { +"  ${if (n.complete) "" else "≥ "}${n.kcal} kcal" } }
                        }
                        val eintraege = jeTag[tag].orEmpty().sortedBy { Mahlzeit.reihenfolge(it.mahlzeit) }
                        if (eintraege.isNotEmpty()) ul("liste") {
                            for (e in eintraege) li {
                                span {
                                    val r = h.rezepte[e.rezeptId]
                                    if (r != null) a(href = "/rezept/${r.id}?portionen=${e.portionen}") { +r.payload.name } else +"Gelöschtes Rezept"
                                    span("leise") { +" ${(Mahlzeit.ausText(e.mahlzeit)?.text() ?: e.mahlzeit)} · ${e.portionen} P.${if (e.gekochtAm != null) " · gekocht" else ""}" }
                                }
                                if (bearbeiten) span("knoepfe") {
                                    // Ohne Rezept lehnt der Server jede Änderung ab (fehlende Referenz) – nur Löschen geht
                                    if (e.rezeptId in h.rezepte) {
                                        knopf(e.id, "minus", "Portion weniger", "−")
                                        knopf(e.id, "plus", "Portion mehr", "+")
                                        knopf(e.id, "frueher", "Einen Tag früher", "‹")
                                        knopf(e.id, "spaeter", "Einen Tag später", "›")
                                        if (e.gekochtAm == null) knopf(e.id, "gekocht", "Gekocht", "✓")
                                    }
                                    knopf(e.id, "loeschen", "Löschen", "✕")
                                }
                            }
                        }
                        a(href = "/plan/neu?tag=$tag", classes = "leise") { +"+ Hinzufügen" }
                    }
                }
            }
        }
    }

    private fun FlowContent.knopf(id: String, aktion: String, beschreibung: String, zeichen: String) =
        button { name = "a"; value = "$id $aktion"; attributes["aria-label"] = beschreibung; +zeichen }

    suspend fun tagebuch(call: ApplicationCall, s: Sitzung, h: Haushalt, heute: LocalDate) {
        val tag = call.datum("tag", heute)
        val woche = (6L downTo 0L).map { tag.minusDays(it) }
        fun summe(d: LocalDate) = Tagebuch.bilanz(h.tagebuch.filter { it.datum == d }.map { EintragWerte(it.mahlzeit, it.werte) })
        val eintraege = h.tagebuch.filter { it.datum == tag }
        val bilanz = summe(tag)
        call.seiteAntworten("Tagebuch", s, Navi.TAGEBUCH) {
            h1 { +"Tagebuch · ${tag.format(TAG)}" }
            nav("wochenleiste") {
                for (d in woche) a(href = "/tagebuch?tag=$d") {
                    if (d == tag) attributes["aria-current"] = "date"
                    +d.format(KURZ)
                    div("leise") { +"${summe(d).tag.kcal}" }
                }
            }
            p("karte") { +"${if (bilanz.tag.vollstaendig) "" else "≥ "}${bilanz.tag.kcal} kcal · Eiweiß ${bilanz.tag.eiweiss} g · KH ${bilanz.tag.kohlenhydrate} g · Fett ${bilanz.tag.fett} g" }
            if (call.request.queryParameters["hinweis"] == "weg") p("hinweis") { +"Eintrag gibt es nicht mehr." }
            if (eintraege.isEmpty()) p("hinweis") { +"Noch nichts eingetragen." }
            // Wie die App: geplante Gerichte des Tages, die noch nicht als gegessen übernommen sind (nur mit Rezept)
            val uebernommen = h.tagebuch.mapNotNull { it.planEintragId }.toSet()
            val vorschlaege = Tagebuch.offeneVorschlaege(h.plan.filter { it.datum == tag && it.rezeptId in h.rezepte }, { it.id }, uebernommen)
            if (vorschlaege.isNotEmpty()) div("karte") {
                h2 { +"Geplant, noch nicht eingetragen" }
                form(action = "/tagebuch/gegessen", method = FormMethod.post) {
                    hiddenInput(name = "_csrf") { value = s.csrf }
                    ul("liste") {
                        for (v in vorschlaege.sortedBy { Mahlzeit.reihenfolge(it.mahlzeit) }) li {
                            span { +h.rezepte.getValue(v.rezeptId).payload.name; span("leise") { +" · ${Mahlzeit.ausText(v.mahlzeit)?.text() ?: v.mahlzeit}" } }
                            button { name = "plan"; value = v.id; +"Gegessen" }
                        }
                    }
                }
            }
            for (m in Mahlzeit.entries) {
                val liste = eintraege.filter { it.mahlzeit == m }
                div("karte") {
                    h2 { +m.text(); bilanz.jeMahlzeit[m]?.let { span("leise") { +"  ${it.kcal} kcal" } } }
                    if (liste.isNotEmpty()) ul("liste") {
                        for (e in liste) li { a(href = "/tagebuch/${e.id}") { +e.name }; span("leise") { +(e.werte.kcal?.let { "$it kcal" } ?: "–") } }
                    }
                    a(href = "/tagebuch/neu?tag=$tag&mahlzeit=${m.name}", classes = "leise") { +"+ Eintragen" }
                }
            }
        }
    }
}

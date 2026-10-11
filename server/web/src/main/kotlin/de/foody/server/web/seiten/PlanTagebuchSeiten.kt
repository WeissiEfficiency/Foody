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
                a(href = "/plan?ab=$ab${if (anzahl == 14) "" else "&tage=14"}") { +(if (anzahl == 14) "7 Tage" else "14 Tage") }
                schnitt?.let { d -> span("leise") { +"${if (d.vollstaendig) "" else "≥ "}Ø ${d.kcal} kcal pro Tag" } }
            }
            if (jeTag.isEmpty()) p("hinweis") { +"Nichts geplant in diesem Zeitraum." }
            for (tag in tage) {
                div("karte") {
                    id = "tag-$tag"
                    h2 {
                        +tag.format(TAG)
                        naehrwerte[tag]?.let { n -> span("leise") { +"  ${if (n.complete) "" else "≥ "}${n.kcal} kcal" } }
                    }
                    val eintraege = jeTag[tag].orEmpty().sortedBy { Mahlzeit.reihenfolge(it.mahlzeit) }
                    if (eintraege.isEmpty()) p("leise") { +"—" } else ul("liste") {
                        for (e in eintraege) li {
                            span {
                                val r = h.rezepte[e.rezeptId]
                                if (r != null) a(href = "/rezept/${r.id}?portionen=${e.portionen}") { +r.payload.name } else +"Gelöschtes Rezept"
                            }
                            span("leise") { +"${(Mahlzeit.ausText(e.mahlzeit)?.text() ?: e.mahlzeit)} · ${e.portionen} P." }
                        }
                    }
                }
            }
        }
    }

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
            if (eintraege.isEmpty()) p("hinweis") { +"Noch nichts eingetragen. Einträge legst du in der App an – im Browser folgt das später." }
            for (m in Mahlzeit.entries) {
                val liste = eintraege.filter { it.mahlzeit == m }
                if (liste.isEmpty()) continue
                div("karte") {
                    h2 { +m.text(); bilanz.jeMahlzeit[m]?.let { span("leise") { +"  ${it.kcal} kcal" } } }
                    ul("liste") {
                        for (e in liste) li { span { +e.name }; span("leise") { +(e.werte.kcal?.let { "$it kcal" } ?: "–") } }
                    }
                }
            }
        }
    }
}

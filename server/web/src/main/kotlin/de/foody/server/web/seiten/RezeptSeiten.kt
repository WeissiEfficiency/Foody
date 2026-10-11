package de.foody.server.web.seiten

import de.foody.domain.Gang
import de.foody.domain.Mahlzeit
import de.foody.domain.Nutrient
import de.foody.domain.NutritionCalculator
import de.foody.domain.NutritionResult
import de.foody.domain.RecipeProfiles
import de.foody.domain.RecipeScaler
import de.foody.server.web.Haushalt
import de.foody.server.web.Navi
import de.foody.server.web.Sitzung
import de.foody.server.web.WebRezept
import de.foody.server.web.menge
import de.foody.server.web.nichtGefunden
import de.foody.server.web.seiteAntworten
import de.foody.server.web.text
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.hiddenInput
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.ul

/** Rezeptliste mit Suche und Filter sowie die Rezeptansicht – Zahlen aus `domain` wie in der App. */
internal object RezeptSeiten {
    private data class Filter(val q: String, val mahlzeit: Mahlzeit?, val gang: Gang?, val archiv: Boolean)

    private fun ApplicationCall.filter() = Filter(
        q = request.queryParameters["q"].orEmpty().trim().take(100),
        mahlzeit = request.queryParameters["mahlzeit"]?.let { m -> Mahlzeit.entries.firstOrNull { it.name == m } },
        gang = request.queryParameters["gang"]?.let { g -> Gang.entries.firstOrNull { it.name == g } },
        archiv = request.queryParameters["archiv"] == "1",
    )

    suspend fun liste(call: ApplicationCall, s: Sitzung, h: Haushalt) {
        val f = call.filter()
        call.seiteAntworten("Rezepte", s, Navi.REZEPTE) {
            h1 { +(if (f.archiv) "Archiv" else "Rezepte") }
            div("zeile") {
                a(href = "/rezept/neu", classes = "knopf") { +"Neues Rezept" }
                a(href = "/import", classes = "knopf") { +"Import" }
                a(href = if (f.archiv) "/" else "/?archiv=1") { +(if (f.archiv) "Zu den Rezepten" else "Archiv") }
            }
            // Ohne JavaScript ein normales GET-Formular; mit htmx wird nur die Liste ersetzt
            form(action = "/", method = FormMethod.get, classes = "zeile karte") {
                attributes["hx-get"] = "/rezepte/liste"
                attributes["hx-target"] = "#liste"
                attributes["hx-trigger"] = "input changed delay:150ms from:input, change from:select, submit"
                input(InputType.search, name = "q") { value = f.q; placeholder = "Name, Tag oder Zutat"; attributes["aria-label"] = "Suche" }
                auswahl("mahlzeit", "Alle Mahlzeiten", Mahlzeit.entries.map { it.name to it.text() }, f.mahlzeit?.name)
                auswahl("gang", "Alle Gänge", Gang.entries.map { it.name to it.text() }, f.gang?.name)
                if (f.archiv) hiddenInput(name = "archiv") { value = "1" }
            }
            div { id = "liste"; rezeptListe(h, f) }
        }
    }

    suspend fun listenTeil(call: ApplicationCall, h: Haushalt) {
        val f = call.filter()
        call.respondHtml { body { rezeptListe(h, f) } }
    }

    private fun FlowContent.auswahl(name: String, alle: String, werte: List<Pair<String, String>>, gewaehlt: String?) {
        select { this.name = name; attributes["aria-label"] = alle
            option { value = ""; +alle }
            for ((wert, text) in werte) option { value = wert; selected = wert == gewaehlt; +text }
        }
    }

    private fun FlowContent.rezeptListe(h: Haushalt, f: Filter) {
        val aktive = h.rezepte.values.filter { (it.payload.archivedAt != null) == f.archiv }
        if (aktive.isEmpty()) {
            p("hinweis") { +(if (f.archiv) "Keine archivierten Rezepte." else "Noch keine Rezepte. Lege eines an oder importiere Markdown-Dateien.") }
            return
        }
        val treffer = aktive.filter { passt(it, f, h) }.sortedBy { it.payload.name.lowercase() }
        if (treffer.isEmpty()) {
            p("leise") { +"Keine Rezepte gefunden." }
            return
        }
        ul("liste karte") {
            for (r in treffer) {
                // Wie die App: nur bei verlässlichem Profil (≥ 80 % der Zutaten mit Energie), sonst „–“
                val kcal = RecipeProfiles.profile(r.domain, h.zutaten).takeIf { it.reliable }?.kcalPerServing
                li {
                    a(href = "/rezept/${r.id}") { +r.payload.name }
                    span("leise") { +(kcal?.let { "$it kcal" } ?: "–") }
                }
            }
        }
    }

    private fun passt(r: WebRezept, f: Filter, h: Haushalt): Boolean {
        if (f.mahlzeit != null && !r.einordnung.passtZu(f.mahlzeit)) return false
        if (f.gang != null && f.gang !in r.einordnung.gaenge) return false
        return f.q.isEmpty() || trifft(r, f.q, h)
    }

    /** Suchtext in Name, Tags oder Zutatennamen (auch für die Auswahl beim Einplanen). */
    fun trifft(r: WebRezept, suche: String, h: Haushalt): Boolean {
        val q = suche.lowercase()
        return q in r.payload.name.lowercase() || q in r.payload.tags.lowercase() ||
            r.domain.ingredients.any { z -> h.zutaten[z.ingredientId]?.name?.lowercase()?.contains(q) == true }
    }

    suspend fun rezept(call: ApplicationCall, s: Sitzung, h: Haushalt, id: String, heute: java.time.LocalDate) {
        val r = h.rezepte[id] ?: return call.nichtGefunden(s, "Dieses Rezept gibt es nicht (mehr).")
        val standard = r.domain.defaultServings
        val portionen = call.request.queryParameters["portionen"]?.toIntOrNull()?.coerceIn(1, 99) ?: standard
        val ergebnis = NutritionCalculator.calculate(r.domain, h.zutaten)
        call.seiteAntworten(r.payload.name, s, Navi.REZEPTE) {
            h1 { +r.payload.name }
            div("zeile") {
                a(href = "/rezept/$id/bearbeiten", classes = "knopf") { +"Bearbeiten" }
                val archiviert = r.payload.archivedAt != null
                form(action = "/rezept/$id/${if (archiviert) "wiederherstellen" else "archivieren"}", method = FormMethod.post) {
                    hiddenInput(name = "_csrf") { value = s.csrf }
                    button(classes = "leise") { +(if (archiviert) "Wiederherstellen" else "Archivieren") }
                }
                if (archiviert) span("leise") { +"archiviert" }
            }
            div("karte zeile") {
                a(href = "/rezept/$id?portionen=${(portionen - 1).coerceAtLeast(1)}", classes = "knopf") { attributes["aria-label"] = "Eine Portion weniger"; +"−" }
                span { +"$portionen ${if (portionen == 1) "Portion" else "Portionen"}" }
                a(href = "/rezept/$id?portionen=${portionen + 1}", classes = "knopf") { attributes["aria-label"] = "Eine Portion mehr"; +"+" }
                ergebnis.perServing(Nutrient.ENERGY_KJ)?.let { kj ->
                    span("leise") {
                        +((if (ergebnis.isComplete(Nutrient.ENERGY_KJ)) "" else "≥ ") + "${NutritionResult.kcal(kj)} kcal je Portion")
                    }
                }
            }
            if (r.payload.archivedAt == null) with(PlanSeiten) { einplanenFormular(s, r, heute.toString(), null, "") }
            h2 { +"Zutaten" }
            ul("liste karte") {
                for (z in r.domain.ingredients) {
                    val skaliert = RecipeScaler.scale(z.amount, standard, portionen)
                    li {
                        span {
                            +(h.zutaten[z.ingredientId]?.name ?: "Unbekannte Zutat")
                            z.note?.let { span("leise") { +" · $it" } }
                            if (z.optional) span("leise") { +" (optional)" }
                        }
                        span { +(if (skaliert.signum() == 0) "n. B." else menge(skaliert, z.unit)) }
                    }
                }
            }
            val schritte = r.payload.steps.sortedBy { it.position }
            if (schritte.isNotEmpty()) {
                h2 { +"Zubereitung" }
                ol("karte") { for (st in schritte) li { +st.text } }
            }
            r.payload.notes?.takeIf { it.isNotBlank() }?.let { n -> h2 { +"Notizen" }; p("karte") { +n } }
        }
    }


}

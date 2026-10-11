package de.foody.server.web.seiten

import de.foody.domain.MeasureUnit
import de.foody.domain.ShoppingCatalog
import de.foody.server.ServerDeps
import de.foody.server.sync.RecordStore
import de.foody.server.web.Haushalt
import de.foody.server.web.HaushaltsDaten
import de.foody.server.web.Navi
import de.foody.server.web.Sitzung
import de.foody.server.web.WebEintrag
import de.foody.server.web.csrfAus
import de.foody.server.web.csrfGueltig
import de.foody.server.web.formular
import de.foody.server.web.menge
import de.foody.server.web.nichtGefunden
import de.foody.server.web.seiteAntworten
import de.foody.server.web.sitzungMitHaushalt
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import java.math.BigDecimal
import kotlinx.html.FormMethod
import kotlinx.html.TagConsumer
import kotlinx.html.UL
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.ul
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Einkaufsliste nach Abteilungen; Abhaken schreibt über `SyncService.push` wie ein Handy. */
internal object EinkaufSeite {
    private const val ERLEDIGT = "Erledigt"

    suspend fun zeigen(call: ApplicationCall, s: Sitzung, h: Haushalt) {
        val listen = h.listen.sortedByDescending { it.geaendert }
        val gewaehlt = call.request.queryParameters["liste"]?.let { id -> listen.firstOrNull { it.id == id } } ?: listen.firstOrNull()
        call.seiteAntworten("Einkauf", s, Navi.EINKAUF) {
            h1 { +"Einkauf" }
            if (gewaehlt == null) {
                p("hinweis") { +"Keine Einkaufsliste. Erzeuge sie in der App aus dem Wochenplan." }
                return@seiteAntworten
            }
            if (listen.size > 1) nav("zeile") {
                for (l in listen) a(href = "/einkauf?liste=${l.id}", classes = if (l == gewaehlt) "knopf" else null) { +l.name }
            }
            h2 { +gewaehlt.name }
            val eintraege = h.eintraege.filter { it.listId == gewaehlt.id }
            if (eintraege.isEmpty()) p("leise") { +"Die Liste ist leer." }
            // Offene Einträge nach Abteilung in Laufreihenfolge, erledigte gesammelt am Ende
            val (erledigt, offen) = eintraege.partition { it.payload.checked }
            val nachAbteilung = offen.groupBy { ShoppingCatalog.sectionFor(it.payload.category, it.payload.name) }
            for (abteilung in ShoppingCatalog.sectionOrder.filter { it in nachAbteilung } + listOfNotNull(ERLEDIGT.takeIf { erledigt.isNotEmpty() })) {
                val zeilen = if (abteilung == ERLEDIGT) erledigt else nachAbteilung.getValue(abteilung)
                div("karte") {
                    h2 { +abteilung }
                    ul("liste") { for (e in zeilen.sortedBy { it.payload.sortOrder }) zeile(e, s) }
                }
            }
        }
    }

    /** Eine Zeile mit Abhak-Knopf; ohne JavaScript ein normales Formular, mit htmx wird nur die Zeile ersetzt. */
    private fun <T> TagConsumer<T>.zeileAlsFragment(e: WebEintrag, s: Sitzung): T = ul { zeile(e, s) }

    private fun UL.zeile(e: WebEintrag, s: Sitzung) {
        val p = e.payload
        li {
            id = "eintrag-${e.id}"
            span(if (p.checked) "erledigt" else null) {
                +p.name
                val menge = p.amount?.let { a -> runCatching { menge(BigDecimal(a), MeasureUnit.valueOf(p.unit ?: "PIECE")) }.getOrNull() }
                menge?.let { span("leise") { +" · $it" } }
                p.note?.let { span("leise") { +" · $it" } }
            }
            form(action = "/einkauf/${e.id}/abhaken", method = FormMethod.post) {
                attributes["hx-post"] = "/einkauf/${e.id}/abhaken"
                attributes["hx-target"] = "#eintrag-${e.id}"
                attributes["hx-swap"] = "outerHTML"
                hiddenInput(name = "_csrf") { value = s.csrf }
                hiddenInput(name = "erledigt") { value = if (p.checked) "0" else "1" }
                hiddenInput(name = "rev") { value = e.rev.toString() }
                button(classes = if (p.checked) "leise" else null) {
                    attributes["aria-label"] = if (p.checked) "${p.name} wieder offen" else "${p.name} abhaken"
                    +(if (p.checked) "↺" else "✓")
                }
            }
        }
    }

    suspend fun abhaken(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, id: String) {
        val form = call.formular()
        val s = call.sitzungMitHaushalt(deps) ?: return
        if (!s.csrfGueltig(call.csrfAus(form))) return call.respond(HttpStatusCode.Forbidden)
        val haushalt = s.haushalt!!
        val erledigt = form["erledigt"] == "1"
        val gesehen = form["rev"]?.toLongOrNull()
        val store = RecordStore()
        val gespeichert = deps.db.tx { c -> store.get(c, haushalt, RecordType.SHOPPING_ITEM, id) }
        if (gespeichert == null || gespeichert.deleted || gespeichert.payload == null) {
            return call.nichtGefunden(s, "Diesen Eintrag gibt es nicht (mehr).")
        }
        val jetzt = deps.clock.millis()
        // Nur Häkchen und Zeitpunkt ersetzen – unbekannte Felder (neuere App-Version) bleiben erhalten
        val payload = JsonObject(gespeichert.payload!! + mapOf("checked" to JsonPrimitive(erledigt), "checkedChangedAt" to JsonPrimitive(jetzt)))
        deps.sync.push(haushalt, listOf(
            SyncRecord(id = id, type = RecordType.SHOPPING_ITEM, updatedAt = jetzt, baseRev = gesehen ?: gespeichert.rev, payload = payload),
        ))
        val neu = daten.fuer(haushalt).eintraege.firstOrNull { it.id == id }
            ?: return call.nichtGefunden(s, "Diesen Eintrag gibt es nicht (mehr).")
        if (call.request.headers["HX-Request"] == "true") {
            // Nur das <li> der Zeile (htmx tauscht es mit outerHTML aus)
            val html = createHTML(prettyPrint = false).zeileAlsFragment(neu, s)
            call.respondText(html.removePrefix("<ul>").removeSuffix("</ul>"), ContentType.Text.Html)
        } else {
            // 303: nach dem POST die Liste per GET neu laden (kein erneutes Absenden beim Aktualisieren)
            call.response.headers.append(HttpHeaders.Location, "/einkauf?liste=${neu.listId}")
            call.respond(HttpStatusCode.SeeOther)
        }
    }
}

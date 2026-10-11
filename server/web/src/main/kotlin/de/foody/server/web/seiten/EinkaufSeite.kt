package de.foody.server.web.seiten

import de.foody.domain.MeasureUnit
import de.foody.domain.ShoppingCatalog
import de.foody.server.ServerDeps
import de.foody.server.sync.RecordStore
import de.foody.server.web.EinkaufSchreiber
import de.foody.server.web.Haushalt
import de.foody.server.web.HaushaltsDaten
import de.foody.server.web.Navi
import de.foody.server.web.Schreiben
import de.foody.server.web.Sitzung
import de.foody.server.web.WebEintrag
import de.foody.server.web.WebRezept
import de.foody.server.web.abgelehnt
import de.foody.server.web.csrfAus
import de.foody.server.web.csrfGueltig
import de.foody.server.web.formular
import de.foody.server.web.menge
import de.foody.server.web.nichtGefunden
import de.foody.server.web.seiteAntworten
import de.foody.server.web.sitzungMitHaushalt
import de.foody.server.web.weiterleiten
import de.foody.sync.protocol.RecordType
import de.foody.sync.protocol.SyncRecord
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.TagConsumer
import kotlinx.html.UL
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
import kotlinx.html.nav
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.ul
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Einkaufsliste nach Abteilungen. Ein Formular für die ganze Liste (CSRF nur einmal): Abhaken schickt `a` =
 * „<id> <erledigt> <rev>“; im Bearbeiten-Modus Notizen (`n_<id>`) und Löschen (`weg`). Geschrieben wird über
 * `SyncService.push` wie von einem Handy.
 */
internal object EinkaufSeite {
    private const val ERLEDIGT = "Erledigt"
    private const val MAX_TEXT = 200
    private const val MAX_NOTIZ = 1000
    private val DATUM = DateTimeFormatter.ofPattern("EE dd.MM.", Locale.GERMAN)

    suspend fun zeigen(call: ApplicationCall, s: Sitzung, h: Haushalt, fehler: String? = null, bearbeitenErzwingen: Boolean = false) {
        val q = call.request.queryParameters
        val listen = h.listen.sortedByDescending { it.geaendert }
        val gewaehlt = q["liste"]?.let { id -> listen.firstOrNull { it.id == id } } ?: listen.firstOrNull()
        val bearbeiten = bearbeitenErzwingen || q["bearbeiten"] == "1"
        call.seiteAntworten("Einkauf", s, Navi.EINKAUF) {
            h1 { +"Einkauf" }
            fehler?.let { p("hinweis fehler") { +it } }
            if (q["hinweis"] == "weg") p("hinweis") { +"Eintrag gibt es nicht mehr." }
            q["hinzu"]?.toIntOrNull()?.let { n ->
                p("hinweis") { +(when (n) { 0 -> "Nichts hinzugefügt (nur Zutaten nach Bedarf)."; 1 -> "1 Zutat hinzugefügt."; else -> "$n Zutaten hinzugefügt." }) }
            }
            form(action = "/einkauf/eingabe", method = FormMethod.post, classes = "zeile") {
                hiddenInput(name = "_csrf") { value = s.csrf }
                hiddenInput(name = "liste") { value = gewaehlt?.id.orEmpty() }
                input(InputType.text, name = "text") { placeholder = "Was willst du einkaufen?"; attributes["aria-label"] = "Was willst du einkaufen?"; maxLength = "$MAX_TEXT" }
                button { +"Hinzufügen" }
            }
            if (gewaehlt == null) {
                p("hinweis") { +"Keine Einkaufsliste." }
                a(href = "/einkauf/neu", classes = "knopf") { +"Neue Liste aus Plan" }
                return@seiteAntworten
            }
            if (listen.size > 1) nav("zeile") {
                for (l in listen) a(href = "/einkauf?liste=${l.id}", classes = if (l == gewaehlt) "knopf" else null) { +l.name }
            }
            h2 { +gewaehlt.name }
            div("zeile") {
                if (gewaehlt.von != null) a(href = "/einkauf/${gewaehlt.id}/abgleich", classes = "knopf") { +"Neu berechnen" }
                a(href = "/einkauf/neu") { +"Neue Liste aus Plan" }
                a(href = "/einkauf?liste=${gewaehlt.id}${if (bearbeiten) "" else "&bearbeiten=1"}") { +(if (bearbeiten) "Fertig" else "Bearbeiten") }
            }
            val eintraege = h.eintraege.filter { it.listId == gewaehlt.id }
            if (eintraege.isEmpty()) p("leise") { +"Die Liste ist leer." }
            val ziel = if (bearbeiten) "/einkauf/${gewaehlt.id}/bearbeiten" else "/einkauf/abhaken"
            form(action = ziel, method = FormMethod.post) {
                if (!bearbeiten) {
                    // htmx: der geklickte Knopf schickt die Zeile, die Antwort ersetzt nur sein <li>
                    attributes["hx-target"] = "closest li"
                    attributes["hx-swap"] = "outerHTML"
                }
                hiddenInput(name = "_csrf") { value = s.csrf }
                // Erster Knopf = Standard beim Drücken von Enter in einem Notizfeld
                if (bearbeiten && eintraege.isNotEmpty()) div("zeile") { button { +"Notizen speichern" } }
                // Offene Einträge nach Abteilung in Laufreihenfolge, erledigte gesammelt am Ende
                val (erledigt, offen) = eintraege.partition { it.payload.checked }
                val nachAbteilung = offen.groupBy { ShoppingCatalog.sectionFor(it.payload.category, it.payload.name) }
                for (abteilung in ShoppingCatalog.sectionOrder.filter { it in nachAbteilung } + listOfNotNull(ERLEDIGT.takeIf { erledigt.isNotEmpty() })) {
                    val zeilen = if (abteilung == ERLEDIGT) erledigt else nachAbteilung.getValue(abteilung)
                    div("karte") {
                        h2 { +abteilung }
                        ul("liste") {
                            for (e in zeilen.sortedBy { it.payload.sortOrder }) if (bearbeiten) bearbeitenZeile(e) else zeile(e)
                        }
                    }
                }
            }
            if (bearbeiten) a(href = "/einkauf/${gewaehlt.id}/loeschen", classes = "leise") { +"Liste löschen" }
        }
    }

    private fun FlowContent.mengeUndNotiz(e: WebEintrag) {
        val p = e.payload
        p.amount?.let { a -> runCatching { menge(BigDecimal(a), MeasureUnit.valueOf(p.unit ?: "PIECE")) }.getOrNull() }?.let { span("leise") { +" · $it" } }
    }

    /** Zeile der Einkaufsansicht: nur Name, Menge, Notiz und der Abhak-Knopf – so bleibt die Seite klein. */
    private fun UL.zeile(e: WebEintrag) {
        val p = e.payload
        li {
            span(if (p.checked) "erledigt" else null) {
                +p.name
                mengeUndNotiz(e)
                p.note?.let { span("leise") { +" · $it" } }
            }
            button(classes = if (p.checked) "leise" else null) {
                name = "a"; value = "${e.id} ${if (p.checked) 0 else 1} ${e.rev}"
                attributes["hx-post"] = "/einkauf/abhaken"
                attributes["aria-label"] = if (p.checked) "${p.name} wieder offen" else "${p.name} abhaken"
                +(if (p.checked) "↺" else "✓")
            }
        }
    }

    private fun UL.bearbeitenZeile(e: WebEintrag) {
        val p = e.payload
        li {
            span {
                +p.name
                mengeUndNotiz(e)
                if (p.sources.isNotEmpty()) {
                    val herkunft = p.sources.joinToString("; ") { q ->
                        "${q.recipeName}, ${runCatching { LocalDate.parse(q.date).format(DATUM) }.getOrDefault(q.date)}"
                    }
                    span("leise") { +" · $herkunft" }
                }
            }
            span("knoepfe") {
                input(InputType.text, name = "n_${e.id}") { value = p.note.orEmpty(); placeholder = "Notiz"; attributes["aria-label"] = "Notiz zu ${p.name}" }
                button { name = "weg"; value = e.id; attributes["aria-label"] = "${p.name} löschen"; +"✕" }
            }
        }
    }

    private fun <T> TagConsumer<T>.zeileAlsFragment(e: WebEintrag): T = ul { zeile(e) }

    /** `POST /einkauf/abhaken` (Feld `a`) oder `POST /einkauf/{id}/abhaken` (Felder `erledigt`, `rev`). */
    suspend fun abhaken(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, pfadId: String?) {
        val s = call.sitzungMitHaushalt(deps) ?: return
        val form = call.formular()
        if (!s.csrfGueltig(call.csrfAus(form))) return call.respond(HttpStatusCode.Forbidden)
        val haushalt = s.haushalt!!
        val teile = form["a"]?.split(' ')
        val id = pfadId ?: teile?.getOrNull(0).orEmpty()
        val erledigt = (if (pfadId != null) form["erledigt"] else teile?.getOrNull(1)) == "1"
        // Die beim Anzeigen gesehene Revision: so erkennt der Server gleichzeitiges Abhaken am Handy und führt zusammen
        val gesehen = (if (pfadId != null) form["rev"] else teile?.getOrNull(2))?.toLongOrNull()
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
            val html = createHTML(prettyPrint = false).zeileAlsFragment(neu)
            call.respondText(html.removePrefix("<ul>").removeSuffix("</ul>"), ContentType.Text.Html)
        } else {
            call.weiterleiten("/einkauf?liste=${neu.listId}")
        }
    }

    // --- Schreiben aus Formularen ---

    private suspend fun ApplicationCall.formMitCsrf(deps: ServerDeps): Pair<Sitzung, Parameters>? {
        val s = sitzungMitHaushalt(deps) ?: return null
        val form = formular()
        if (!s.csrfGueltig(csrfAus(form))) { respond(HttpStatusCode.Forbidden); return null }
        return s to form
    }

    suspend fun eingabe(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten) {
        val (s, form) = call.formMitCsrf(deps) ?: return
        val text = form["text"].orEmpty().trim()
        val listId = form["liste"].orEmpty()
        if (text.isEmpty()) return call.weiterleiten(if (listId.isEmpty()) "/einkauf" else "/einkauf?liste=$listId")
        if (text.length > MAX_TEXT) return zeigen(call, s, daten.fuer(s.haushalt!!), "Höchstens $MAX_TEXT Zeichen.")
        val (ergebnis, ziel) = EinkaufSchreiber(deps, daten).eingabe(s.haushalt!!, listId, text)
        when (ergebnis) {
            Schreiben.Ok -> call.weiterleiten("/einkauf?liste=$ziel")
            Schreiben.Weg -> call.weiterleiten("/einkauf?liste=$ziel&hinweis=weg")
            Schreiben.NichtGefunden -> call.nichtGefunden(s, "Diese Liste gibt es nicht (mehr).")
            Schreiben.Abgelehnt -> call.abgelehnt(s)
        }
    }

    suspend fun bearbeitenSpeichern(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, listId: String) {
        val (s, form) = call.formMitCsrf(deps) ?: return
        val notizen = form.names().filter { it.startsWith("n_") }.associate { it.removePrefix("n_") to form[it].orEmpty() }
        if (notizen.values.any { it.trim().length > MAX_NOTIZ }) {
            return zeigen(call, s, daten.fuer(s.haushalt!!), "Notiz höchstens $MAX_NOTIZ Zeichen.", bearbeitenErzwingen = true)
        }
        val zurueck = "/einkauf?liste=$listId&bearbeiten=1"
        when (EinkaufSchreiber(deps, daten).bearbeiten(s.haushalt!!, listId, notizen, form["weg"]?.takeIf { it.isNotEmpty() })) {
            Schreiben.Ok -> call.weiterleiten(zurueck)
            Schreiben.Weg -> call.weiterleiten("$zurueck&hinweis=weg")
            Schreiben.NichtGefunden -> call.nichtGefunden(s, "Diesen Eintrag gibt es nicht.")
            Schreiben.Abgelehnt -> call.abgelehnt(s)
        }
    }

    suspend fun loeschenBestaetigen(call: ApplicationCall, s: Sitzung, h: Haushalt, listId: String) {
        val liste = h.listen.firstOrNull { it.id == listId } ?: return call.nichtGefunden(s, "Diese Liste gibt es nicht (mehr).")
        val anzahl = h.eintraege.count { it.listId == listId }
        call.seiteAntworten("Liste löschen", s, Navi.EINKAUF) {
            h1 { +"Liste löschen?" }
            p { +"„${liste.name}“ mit $anzahl ${if (anzahl == 1) "Eintrag" else "Einträgen"} wird auf allen Geräten gelöscht." }
            form(action = "/einkauf/$listId/loeschen", method = FormMethod.post, classes = "zeile") {
                hiddenInput(name = "_csrf") { value = s.csrf }
                button { +"Liste löschen" }
                a(href = "/einkauf?liste=$listId&bearbeiten=1") { +"Abbrechen" }
            }
        }
    }

    suspend fun listeLoeschen(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, listId: String) {
        val (s, _) = call.formMitCsrf(deps) ?: return
        when (EinkaufSchreiber(deps, daten).listeLoeschen(s.haushalt!!, listId)) {
            Schreiben.Ok, Schreiben.Weg -> call.weiterleiten("/einkauf")
            Schreiben.NichtGefunden -> call.nichtGefunden(s, "Diese Liste gibt es nicht (mehr).")
            Schreiben.Abgelehnt -> call.abgelehnt(s)
        }
    }

    suspend fun rezeptAufListe(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten, heute: LocalDate) {
        val (s, form) = call.formMitCsrf(deps) ?: return
        val household = s.haushalt!!
        val rezept = daten.fuer(household).rezepte[form["rezept"].orEmpty()] ?: return call.nichtGefunden(s, "Dieses Rezept gibt es nicht (mehr).")
        val text = form["portionen"].orEmpty().trim()
        val portionen = if (text.isEmpty()) rezept.domain.defaultServings else text.toIntOrNull()
        if (portionen == null || portionen !in 1..PlanSeiten.MAX_PORTIONEN) {
            return call.seiteAntworten("Auf die Einkaufsliste", s, Navi.REZEPTE, HttpStatusCode.BadRequest) {
                h1 { +"Auf die Einkaufsliste" }
                p("hinweis fehler") { +"Portionen: 1 bis ${PlanSeiten.MAX_PORTIONEN}." }
                a(href = "/rezept/${rezept.id}") { +"Zurück zum Rezept" }
            }
        }
        val (anzahl, ziel) = EinkaufSchreiber(deps, daten).rezeptAufListe(household, rezept.id, portionen, form["liste"], heute)
            ?: return call.nichtGefunden(s, "Diese Liste gibt es nicht (mehr).")
        call.weiterleiten("/einkauf?liste=$ziel&hinzu=$anzahl")
    }

    /** Formular „Auf die Einkaufsliste“ für die Rezeptseite; Vorgabe ist die zuletzt geänderte Liste (höchste Revision). */
    fun FlowContent.aufListeFormular(s: Sitzung, h: Haushalt, r: WebRezept) {
        val listen = h.listen.sortedByDescending { it.rev }
        form(action = "/einkauf/rezept", method = FormMethod.post, classes = "karte zeile") {
            hiddenInput(name = "_csrf") { value = s.csrf }
            hiddenInput(name = "rezept") { value = r.id }
            label { +"Portionen"; input(InputType.number, name = "portionen", classes = "menge") {
                placeholder = r.domain.defaultServings.toString(); min = "1"; max = "${PlanSeiten.MAX_PORTIONEN}"
            } }
            select {
                name = "liste"; attributes["aria-label"] = "Einkaufsliste"
                listen.forEachIndexed { i, l -> option { value = l.id; selected = i == 0; +l.name } }
                option { value = ""; selected = listen.isEmpty(); +"Neue Liste" }
            }
            button { +"Auf die Einkaufsliste" }
        }
    }
}

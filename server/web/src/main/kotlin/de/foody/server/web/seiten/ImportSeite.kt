package de.foody.server.web.seiten

import de.foody.domain.MarkdownRecipeImporter
import de.foody.server.ServerDeps
import de.foody.server.web.HaushaltsDaten
import de.foody.server.web.Navi
import de.foody.server.web.RezeptSchreiber
import de.foody.server.web.Sitzung
import de.foody.server.web.csrfGueltig
import de.foody.server.web.seiteAntworten
import de.foody.server.web.sitzungMitHaushalt
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.utils.io.readRemaining
import kotlinx.html.FormEncType
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
import kotlinx.html.p
import kotlinx.html.ul
import kotlinx.io.readByteArray

/** Markdown-Import per Upload – gleiche Grenzen und gleiche Rechnung wie in der App. */
internal object ImportSeite {
    private const val MAX_DATEIEN = 50
    private const val MAX_BYTES = 1L shl 20 // wie MAX_FILE_BYTES der App
    private val ENDUNGEN = setOf("md", "markdown", "txt")

    suspend fun zeigen(call: ApplicationCall, s: Sitzung, fehler: String? = null) = call.seiteAntworten("Import", s, Navi.REZEPTE) {
        h1 { +"Rezepte importieren" }
        p("leise") { +"Markdown-Dateien (z. B. von Web-Clippern): höchstens $MAX_DATEIEN Dateien, je bis 1 MB. Rezepte mit schon vorhandener Quelle werden übersprungen." }
        if (fehler != null) p("hinweis fehler") { +fehler }
        form(action = "/import", method = FormMethod.post, encType = FormEncType.multipartFormData, classes = "karte") {
            // CSRF zuerst: der Server liest Dateien erst nach gültigem Token
            hiddenInput(name = "_csrf") { value = s.csrf }
            label { +"Tag"; input(InputType.text, name = "tag") { value = "Import" } }
            label { +"Dateien"; input(InputType.file, name = "dateien") { multiple = true; accept = ".md,.markdown,.txt" } }
            div("zeile") { button { +"Importieren" } }
        }
    }

    private data class Ergebnis(val importiert: List<Pair<String, String>>, val uebersprungen: List<String>, val nichtErkannt: List<String>)

    suspend fun importieren(call: ApplicationCall, deps: ServerDeps, daten: HaushaltsDaten) {
        val s = call.sitzungMitHaushalt(deps) ?: return
        val household = s.haushalt!!
        var csrfOk = call.request.headers["X-Foody-CSRF"]?.let { s.csrfGueltig(it) } ?: false
        var verweigert = false
        var tag = "Import"
        var fehler: String? = null
        var anzahl = 0
        val dateien = mutableListOf<Pair<String, ByteArray>>()
        // Teile einzeln und gezählt lesen: nie mehr als 1 MB je Datei im Speicher
        call.receiveMultipart(formFieldLimit = MAX_BYTES * 2).forEachPart { part ->
            when (part) {
                is PartData.FormItem -> when (part.name) {
                    "_csrf" -> if (s.csrfGueltig(part.value)) csrfOk = true
                    "tag" -> tag = part.value.trim().take(50).ifBlank { "Import" }
                }
                is PartData.FileItem -> when {
                    !csrfOk -> verweigert = true
                    fehler != null -> Unit
                    ++anzahl > MAX_DATEIEN -> fehler = "Höchstens $MAX_DATEIEN Dateien je Import."
                    else -> {
                        val name = part.originalFileName.orEmpty().substringAfterLast('/').substringAfterLast('\\').take(100)
                        val bytes = part.provider().readRemaining(MAX_BYTES + 1).readByteArray()
                        if (bytes.size > MAX_BYTES) fehler = "„$name“ ist größer als 1 MB." else dateien += name to bytes
                    }
                }
                else -> Unit
            }
            part.release()
        }
        if (verweigert || !csrfOk) return call.respond(HttpStatusCode.Forbidden)
        if (fehler != null) return zeigen(call, s, "$fehler Es wurde nichts importiert.")
        if (dateien.isEmpty()) return zeigen(call, s, "Bitte mindestens eine Datei auswählen.")

        val schreiber = RezeptSchreiber(deps, daten)
        val quellen = daten.fuer(household).rezepte.values.mapNotNull { it.payload.sourceUrl }.toMutableSet()
        val importiert = mutableListOf<Pair<String, String>>()
        val uebersprungen = mutableListOf<String>()
        val nichtErkannt = mutableListOf<String>()
        for ((name, bytes) in dateien) {
            val rezept = if (name.substringAfterLast('.', "").lowercase() in ENDUNGEN) {
                runCatching { MarkdownRecipeImporter.parse(bytes.decodeToString()) }.getOrNull()
            } else null
            when {
                rezept == null || rezept.name.isBlank() || rezept.ingredients.isEmpty() -> nichtErkannt += name
                rezept.sourceUrl != null && rezept.sourceUrl in quellen -> uebersprungen += rezept.name
                else -> {
                    val id = schreiber.importieren(household, rezept, tag)
                    if (id == null) nichtErkannt += name else {
                        importiert += id to rezept.name
                        rezept.sourceUrl?.let { quellen += it }
                    }
                }
            }
        }
        zeigeErgebnis(call, s, Ergebnis(importiert, uebersprungen, nichtErkannt))
    }

    private suspend fun zeigeErgebnis(call: ApplicationCall, s: Sitzung, e: Ergebnis) = call.seiteAntworten("Import", s, Navi.REZEPTE) {
        h1 { +"Import abgeschlossen" }
        p { +"${e.importiert.size} importiert · ${e.uebersprungen.size} übersprungen · ${e.nichtErkannt.size} nicht erkannt" }
        if (e.importiert.isNotEmpty()) { h2 { +"Importiert" }; ul("liste karte") { for ((id, name) in e.importiert) li { a(href = "/rezept/$id") { +name } } } }
        if (e.uebersprungen.isNotEmpty()) { h2 { +"Übersprungen (Quelle schon vorhanden)" }; ul("liste karte") { for (n in e.uebersprungen) li { +n } } }
        if (e.nichtErkannt.isNotEmpty()) { h2 { +"Nicht erkannt" }; ul("liste karte") { for (n in e.nichtErkannt) li { +n } } }
        div("zeile") { a(href = "/import", classes = "knopf") { +"Weitere importieren" }; a(href = "/") { +"Zu den Rezepten" } }
    }
}

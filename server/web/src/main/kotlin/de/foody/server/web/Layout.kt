package de.foody.server.web

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.response.respondBytes
import java.security.MessageDigest
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.MAIN
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.hiddenInput
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.script
import kotlinx.html.title

internal enum class Navi(val pfad: String, val text: String) {
    REZEPTE("/", "Rezepte"), PLAN("/plan", "Plan"), EINKAUF("/einkauf", "Einkauf"), VORRAT("/vorrat", "Vorrat"), TAGEBUCH("/tagebuch", "Tagebuch")
}

/** Statische Dateien: Stylesheet aus den Ressourcen, htmx aus dem WebJar. Version = Inhalts-Prüfsumme (Cache-Bust). */
internal object Statisch {
    private const val HTMX_PFAD = "META-INF/resources/webjars/htmx.org/2.0.4/dist/htmx.min.js"
    val css: ByteArray = laden("web/foody.css")
    val htmx: ByteArray = laden(HTMX_PFAD)
    val version: String = MessageDigest.getInstance("SHA-256").digest(css + htmx).take(4).joinToString("") { "%02x".format(it) }

    private fun laden(pfad: String): ByteArray =
        checkNotNull(Statisch::class.java.classLoader.getResourceAsStream(pfad)) { "Ressource fehlt: $pfad" }.use { it.readBytes() }

    suspend fun ApplicationCall.liefere(bytes: ByteArray, typ: ContentType) {
        response.headers.append(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        respondBytes(bytes, typ)
    }
}

/** Grundgerüst jeder Seite. Mit [sitzung] gibt es Navigation, Abmelden und das CSRF-Token für htmx. */
internal fun HTML.seite(titel: String, sitzung: Sitzung?, aktiv: Navi?, inhalt: MAIN.() -> Unit) {
    lang = "de"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title("$titel · Foody")
        link(rel = "stylesheet", href = "/web/foody.css?v=${Statisch.version}")
        if (sitzung != null) {
            meta(name = "csrf", content = sitzung.csrf)
            script(src = "/web/htmx.min.js?v=${Statisch.version}") { defer = true }
        }
    }
    body {
        if (sitzung != null) {
            // htmx schickt das CSRF-Token bei jeder Anfrage als Header mit
            attributes["hx-headers"] = """{"X-Foody-CSRF":"${sitzung.csrf}"}"""
            header {
                a(href = "/", classes = "marke") { +"Foody" }
                nav {
                    for (n in Navi.entries) {
                        a(href = n.pfad) {
                            if (n == aktiv) attributes["aria-current"] = "page"
                            +n.text
                        }
                    }
                }
                abmeldeKnopf(sitzung)
            }
        }
        main { inhalt() }
    }
}

private fun FlowContent.abmeldeKnopf(sitzung: Sitzung) {
    form(action = "/logout", method = kotlinx.html.FormMethod.post) {
        hiddenInput(name = "_csrf") { value = sitzung.csrf }
        button(classes = "leise") { +"Abmelden" }
    }
}

internal suspend fun ApplicationCall.seiteAntworten(
    titel: String,
    sitzung: Sitzung?,
    aktiv: Navi?,
    status: HttpStatusCode = HttpStatusCode.OK,
    inhalt: MAIN.() -> Unit,
) = respondHtml(status) { seite(titel, sitzung, aktiv, inhalt) }

package de.foody.server.web

import de.foody.domain.MeasureUnit
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.html.h1
import kotlinx.html.p

/** Wie `formatAmount` der App: Zahl mit Komma (höchstens zwei Stellen) und Einheitszeichen, z. B. „1,5 EL“. */
internal fun menge(wert: BigDecimal, einheit: MeasureUnit): String = "${zahl(wert)} ${einheit.symbol}"

internal fun zahl(wert: BigDecimal, stellen: Int = 2): String =
    wert.setScale(stellen, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

/** 404 im Layout – ohne Hinweis, ob es den Datensatz in einem anderen Haushalt gibt. */
internal suspend fun ApplicationCall.nichtGefunden(s: Sitzung, text: String) =
    seiteAntworten("Nicht gefunden", s, null, HttpStatusCode.NotFound) {
        h1 { +"Nicht gefunden" }
        p { +text }
    }

/** Anzeigetexte der Mahlzeiten (wie die String-Ressourcen der App). */
internal fun de.foody.domain.Mahlzeit.text(): String = when (this) {
    de.foody.domain.Mahlzeit.FRUEHSTUECK -> "Frühstück"
    de.foody.domain.Mahlzeit.MITTAGESSEN -> "Mittagessen"
    de.foody.domain.Mahlzeit.ABENDESSEN -> "Abendessen"
    de.foody.domain.Mahlzeit.SNACK -> "Snack"
}

internal fun de.foody.domain.Gang.text(): String = name.lowercase().replaceFirstChar { it.uppercase() }

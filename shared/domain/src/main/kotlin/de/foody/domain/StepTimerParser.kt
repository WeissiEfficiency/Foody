package de.foody.domain

import java.math.BigDecimal
import java.time.Duration

/** Eine im Arbeitsschritt erkannte Zeitangabe, z. B. „20 Minuten“. */
data class StepTimer(val duration: Duration, val label: String)

/**
 * Erkennt Zeitangaben in Arbeitsschritten („20 Minuten“, „ca. 1,5 Std.“, „10 - 15 Min.“) für Timer im Kochmodus.
 * Bei Spannen gilt die untere Grenze: lieber früher nachsehen als etwas anbrennen lassen.
 */
object StepTimerParser {
    private const val NUM = """\d+(?:[.,]\d+)?"""
    // (?!\p{L}) = Wortende, damit „5 Minzblätter“ kein Timer wird
    private const val HOURS = """(?:Stunden|Stunde|Std\.?|h)(?!\p{L})"""
    private const val MINUTES = """(?:Minuten|Minute|Min\.?)(?!\p{L})"""
    private const val SECONDS = """(?:Sekunden|Sekunde|Sek\.?)(?!\p{L})"""

    // Zahl oder Spanne („10 - 15“, „4–5“, „10 bis 15“), dann Einheit;
    // optional „1 Stunde (und) 30 Minuten“ als Kombination
    private val pattern = Regex(
        """($NUM)(?:\s*[-–]\s*$NUM|\s+bis\s+$NUM)?\s*($HOURS|$MINUTES|$SECONDS)(?:\s+(?:und\s+)?($NUM)\s*($MINUTES))?""",
        RegexOption.IGNORE_CASE,
    )

    fun find(text: String): List<StepTimer> = pattern.findAll(text).mapNotNull { m ->
        val amount = m.groupValues[1].replace(',', '.').toBigDecimalOrNull() ?: return@mapNotNull null
        var seconds = amount * unitSeconds(m.groupValues[2])
        if (m.groupValues[3].isNotEmpty()) {
            seconds += (m.groupValues[3].replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO) * BigDecimal(60)
        }
        val total = seconds.toLong()
        if (total <= 0) null else StepTimer(Duration.ofSeconds(total), m.value.trim())
    }.toList()

    private fun unitSeconds(unit: String): BigDecimal {
        val u = unit.lowercase()
        return when {
            u.startsWith("std") || u.startsWith("stunde") || u == "h" -> BigDecimal(3600)
            u.startsWith("sek") -> BigDecimal.ONE
            else -> BigDecimal(60)
        }
    }
}

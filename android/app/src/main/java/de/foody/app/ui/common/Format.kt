package de.foody.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.foody.app.R
import de.foody.domain.MeasureUnit
import de.foody.domain.Nutrient
import de.foody.domain.Quantity
import de.foody.domain.QuantityFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun BigDecimal.display(scale: Int = 2): String =
    setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

/** Akzeptiert „1,5“ und „1.5“. null bei ungültiger Eingabe. */
fun parseDecimal(text: String): BigDecimal? =
    text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

/**
 * Wie [parseDecimal], aber nur Zahlen, die auch der Sync-Server annimmt: nicht negativ, ohne Exponent,
 * höchstens 20 Nachkommastellen und 30 Stellen insgesamt. Sonst würde ein lokal gespeicherter Wert zum dauerhaften Sync-Problem.
 */
fun parseNichtNegativ(text: String): BigDecimal? {
    if (text.contains('e', ignoreCase = true)) return null
    return parseDecimal(text)?.takeIf { it.signum() >= 0 && de.foody.sync.protocol.ZahlGrenzen.imRahmen(it) }
}

fun formatAmount(amount: BigDecimal, unit: MeasureUnit): String = "${amount.display()} ${unit.symbol}"

fun formatQuantity(q: Quantity): String = QuantityFormatter.format(q)

private val dateFmt = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)
fun LocalDate.pretty(): String = format(dateFmt)

private val dayMonthFmt = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)

/** Kurzer Zeitraum für Überschriften: „3.–9. Okt.“, über Monatsgrenzen „28. Sep. – 4. Okt.“. */
fun compactRange(start: LocalDate, end: LocalDate): String = when {
    start == end -> start.format(dayMonthFmt)
    start.year == end.year && start.month == end.month -> "${start.dayOfMonth}.–${end.format(dayMonthFmt)}"
    else -> "${start.format(dayMonthFmt)} – ${end.format(dayMonthFmt)}"
}
fun LocalDate.medium(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@Composable
fun Nutrient.label(): String = stringResource(
    when (this) {
        Nutrient.ENERGY_KJ -> R.string.nutrient_energy
        Nutrient.PROTEIN_G -> R.string.nutrient_protein
        Nutrient.CARBS_G -> R.string.nutrient_carbs
        Nutrient.FAT_G -> R.string.nutrient_fat
        Nutrient.FIBER_G -> R.string.nutrient_fiber
        Nutrient.SUGAR_G -> R.string.nutrient_sugar
        Nutrient.SALT_G -> R.string.nutrient_salt
    },
)

package de.foody.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Nicht beliebig mischbare Mengendimensionen. Packungen und Dosen sind eigene Dimensionen: „1 Pck. Hefe“
 * (7 g Trockenhefe) ist etwas anderes als „1 Stück Hefe“ (42-g-Würfel), und auf der Einkaufsliste soll
 * „2 Dosen“ stehen, nicht „2 Stk.“.
 */
enum class Dimension(val countable: Boolean) { MASS(false), VOLUME(false), COUNT(true), PACKAGE(true), CAN(true) }

/**
 * Einheiten mit Faktor zur Basiseinheit der Dimension.
 * Basis: Masse = g, Volumen = ml, Anzahl = Stück.
 */
enum class MeasureUnit(val symbol: String, val dimension: Dimension, val factorToBase: BigDecimal) {
    MILLIGRAM("mg", Dimension.MASS, BigDecimal("0.001")),
    GRAM("g", Dimension.MASS, BigDecimal.ONE),
    KILOGRAM("kg", Dimension.MASS, BigDecimal("1000")),
    MILLILITER("ml", Dimension.VOLUME, BigDecimal.ONE),
    CENTILITER("cl", Dimension.VOLUME, BigDecimal("10")),
    LITER("l", Dimension.VOLUME, BigDecimal("1000")),
    TEASPOON("TL", Dimension.VOLUME, BigDecimal("5")),
    TABLESPOON("EL", Dimension.VOLUME, BigDecimal("15")),
    PIECE("Stk.", Dimension.COUNT, BigDecimal.ONE),
    PACKAGE("Pck.", Dimension.PACKAGE, BigDecimal.ONE),
    CAN("Dose", Dimension.CAN, BigDecimal.ONE),
    ;

    companion object {
        fun baseOf(dimension: Dimension): MeasureUnit = when (dimension) {
            Dimension.MASS -> GRAM
            Dimension.VOLUME -> MILLILITER
            Dimension.COUNT -> PIECE
            Dimension.PACKAGE -> PACKAGE
            Dimension.CAN -> CAN
        }
    }
}

internal val MATH_SCALE = 6

/** Menge in einer Dimension, intern immer in der Basiseinheit. */
data class Quantity(val baseAmount: BigDecimal, val dimension: Dimension) {
    operator fun plus(other: Quantity): Quantity {
        require(dimension == other.dimension) { "Dimensionen $dimension und ${other.dimension} sind nicht addierbar" }
        return Quantity(baseAmount + other.baseAmount, dimension)
    }

    fun minusClamped(other: Quantity): Quantity {
        require(dimension == other.dimension)
        return Quantity((baseAmount - other.baseAmount).max(BigDecimal.ZERO), dimension)
    }

    fun isZero(): Boolean = baseAmount.signum() == 0

    /**
     * Auf ganze Basiseinheiten aufgerundet. Vorher auf zwei Stellen gerundet, damit Rechenrauschen aus der
     * Skalierung (2,000001 Stk.) kein zusätzliches Stück auslöst.
     */
    fun roundedUpToWhole(): Quantity =
        copy(baseAmount = baseAmount.setScale(2, RoundingMode.HALF_UP).setScale(0, RoundingMode.CEILING))

    fun amountIn(unit: MeasureUnit): BigDecimal {
        require(unit.dimension == dimension)
        return baseAmount.divide(unit.factorToBase, MATH_SCALE, RoundingMode.HALF_UP).stripTrailingZeros()
    }

    companion object {
        fun of(amount: BigDecimal, unit: MeasureUnit) = Quantity(amount * unit.factorToBase, unit.dimension)
        fun zero(dimension: Dimension) = Quantity(BigDecimal.ZERO, dimension)
    }
}

/** Umrechnungsdaten einer konkreten Zutat. */
data class ConversionInfo(
    /** Gramm je Milliliter, null = unbekannt. */
    val densityGPerMl: BigDecimal? = null,
    /** Gramm je Stück, null = unbekannt. */
    val pieceWeightG: BigDecimal? = null,
    /** Gramm je Packung (z. B. Vanillezucker 8 g), null = unbekannt. */
    val packageWeightG: BigDecimal? = null,
    /** Gramm je Dose (Abtropf- bzw. Füllgewicht), null = unbekannt. */
    val canWeightG: BigDecimal? = null,
) {
    /** Gramm je Einheit einer Zähl-Dimension; Masse und Volumen haben keine. */
    fun gramsPerUnit(dimension: Dimension): BigDecimal? = when (dimension) {
        Dimension.COUNT -> pieceWeightG
        Dimension.PACKAGE -> packageWeightG
        Dimension.CAN -> canWeightG
        Dimension.MASS, Dimension.VOLUME -> null
    }?.takeIf { it.signum() > 0 }
}

object UnitConverter {
    /**
     * Konvertiert eine Menge in eine Zieldimension. Masse/Volumen nur mit Dichte,
     * Stück/Masse nur mit Stückgewicht. Liefert null, wenn keine sichere Umrechnung existiert.
     */
    fun convert(q: Quantity, target: Dimension, info: ConversionInfo): Quantity? {
        if (q.dimension == target) return q
        val grams: BigDecimal = when (q.dimension) {
            Dimension.MASS -> q.baseAmount
            // Dichte 0 wie unbekannt: sonst ginge ml → g, aber nicht zurück (markCooked rechnet hin und her)
            Dimension.VOLUME -> info.densityGPerMl?.takeIf { it.signum() > 0 }?.let { q.baseAmount * it } ?: return null
            Dimension.COUNT, Dimension.PACKAGE, Dimension.CAN ->
                info.gramsPerUnit(q.dimension)?.let { q.baseAmount * it } ?: return null
        }
        val result: BigDecimal = when (target) {
            Dimension.MASS -> grams
            Dimension.VOLUME -> info.densityGPerMl?.takeIf { it.signum() > 0 }
                ?.let { grams.divide(it, MATH_SCALE, RoundingMode.HALF_UP) } ?: return null
            Dimension.COUNT, Dimension.PACKAGE, Dimension.CAN ->
                info.gramsPerUnit(target)?.let { grams.divide(it, MATH_SCALE, RoundingMode.HALF_UP) } ?: return null
        }
        return Quantity(result, target)
    }
}

object QuantityFormatter {
    /** Wählt eine gut lesbare Anzeigeeinheit (z. B. 1700 g → 1,7 kg). */
    fun displayUnit(q: Quantity): MeasureUnit = when (q.dimension) {
        Dimension.MASS -> if (q.baseAmount.abs() >= BigDecimal("1000")) MeasureUnit.KILOGRAM else MeasureUnit.GRAM
        Dimension.VOLUME -> if (q.baseAmount.abs() >= BigDecimal("1000")) MeasureUnit.LITER else MeasureUnit.MILLILITER
        Dimension.COUNT -> MeasureUnit.PIECE
        Dimension.PACKAGE -> MeasureUnit.PACKAGE
        Dimension.CAN -> MeasureUnit.CAN
    }

    fun format(q: Quantity, unit: MeasureUnit = displayUnit(q)): String {
        val amount = q.amountIn(unit).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
        val text = amount.toPlainString().replace('.', ',')
        return "$text ${unit.symbol}"
    }
}

package de.foody.core.data.db

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Typwandler wie in Schema 9: [BigDecimal] als Text (verlustfrei, `toPlainString`), [LocalDate] als Epoch-Tag.
 * Aufzählungen speichert Room selbst als Namen (Text).
 */
class Converters {
    @TypeConverter fun ausBigDecimal(wert: BigDecimal?): String? = wert?.toPlainString()

    @TypeConverter fun zuBigDecimal(wert: String?): BigDecimal? = wert?.let(::BigDecimal)

    @TypeConverter fun ausDatum(wert: LocalDate?): Long? = wert?.toEpochDay()

    @TypeConverter fun zuDatum(wert: Long?): LocalDate? = wert?.let(LocalDate::ofEpochDay)
}

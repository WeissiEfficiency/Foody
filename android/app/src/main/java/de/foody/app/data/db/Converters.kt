package de.foody.app.data.db

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.time.LocalDate

/** BigDecimal als String (verlustfrei), LocalDate als Epoch-Tag. */
class Converters {
    @TypeConverter fun fromBigDecimal(v: BigDecimal?): String? = v?.toPlainString()
    @TypeConverter fun toBigDecimal(v: String?): BigDecimal? = v?.let(::BigDecimal)
    @TypeConverter fun fromDate(v: LocalDate?): Long? = v?.toEpochDay()
    @TypeConverter fun toDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)
}

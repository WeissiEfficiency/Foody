package de.foody.core.data

import java.time.LocalDate
import java.util.UUID

private val WOCHENTAGE = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

/** „Mi“ */
fun wochentagKurz(datum: LocalDate): String = WOCHENTAGE[datum.dayOfWeek.value - 1]

/** „Mi 28.10.“ */
fun datumKurz(datum: LocalDate): String = "${wochentagKurz(datum)} ${datum.dayOfMonth}.${datum.monthValue}."

/** Neue ID für einen Datensatz. */
internal fun neueId(): String = UUID.randomUUID().toString()

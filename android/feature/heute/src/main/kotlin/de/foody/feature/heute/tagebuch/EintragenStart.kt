package de.foody.feature.heute.tagebuch

import de.foody.domain.Mahlzeit
import java.time.LocalDate

/** Eintragen beginnt gleich mit der Kamera ([FOTO]) bzw. dem Strichcode-Scanner ([SCANNEN]). */
enum class SofortStart { FOTO, SCANNEN }

/** Wie Eintragen beginnt: [sofort] `null` = Erfassen, sonst gleich Kamera bzw. Scanner. */
data class EintragenStart(val datum: LocalDate, val mahlzeit: Mahlzeit? = null, val sofort: SofortStart? = null)

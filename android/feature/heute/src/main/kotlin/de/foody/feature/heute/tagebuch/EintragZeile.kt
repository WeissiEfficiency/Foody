package de.foody.feature.heute.tagebuch

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.foody.core.data.db.TagebuchEintragEntity
import de.foody.core.designsystem.FoodyTheme
import de.foody.core.designsystem.komponenten.kcalText
import de.foody.core.designsystem.token.FoodyAbstaende
import de.foody.domain.Mahlzeit
import de.foody.domain.Naehrwerte

/** Mahlzeit eines Eintrags; alte Freitext-Mahlzeiten („Sonstiges“) zählen als Snack. */
internal fun TagebuchEintragEntity.eintragMahlzeit(): Mahlzeit = Mahlzeit.ausText(mahlzeit) ?: Mahlzeit.SNACK

internal fun TagebuchEintragEntity.werte() = Naehrwerte(energieKj, eiweiss, kohlenhydrate, fett, vollstaendig)

/** Höchstlänge eines Namens (wie der Server). */
internal const val MAX_NAME = 200

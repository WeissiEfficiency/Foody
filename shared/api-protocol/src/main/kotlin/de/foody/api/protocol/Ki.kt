package de.foody.api.protocol

import kotlinx.serialization.Serializable

/** Ob der Server für die KI-Funktionen eingerichtet ist (Schlüssel gesetzt). */
@Serializable
data class KiStatus(val eingerichtet: Boolean)

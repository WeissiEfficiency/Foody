package de.foody.api.protocol

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String, val password: String, val deviceName: String)

@Serializable
data class PasswordChangeRequest(val oldPassword: String, val newPassword: String)

/** Antwort auf die Anmeldung: Geräte-Token und ID des Kontos. */
@Serializable
data class AuthResponse(val token: String, val kontoId: String)

package de.foody.sync.protocol

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String, val password: String, val deviceName: String)

@Serializable
data class RegisterRequest(val code: String, val username: String, val password: String, val deviceName: String)

@Serializable
data class PasswordChangeRequest(val oldPassword: String, val newPassword: String)

/** [householdId] ist `null`, solange das Gerät noch keinem Haushalt zugeordnet ist. */
@Serializable
data class AuthResponse(val token: String, val userId: String, val householdId: String? = null)

@Serializable
data class HouseholdDto(val id: String, val name: String, val role: String)

@Serializable
data class CreateHouseholdRequest(val name: String)

@Serializable
data class SelectHouseholdRequest(val householdId: String)

@Serializable
data class InviteDto(val code: String, val expiresAt: Long)

@Serializable
data class DeviceDto(val id: String, val name: String, val lastSeenAt: Long? = null, val current: Boolean)

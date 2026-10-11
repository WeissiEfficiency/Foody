package de.foody.sync.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Fehlercodes des Servers; auf dem Draht snake_case in Kleinbuchstaben. */
@Serializable
enum class ErrorCode {
    @SerialName("protocol_too_old") PROTOCOL_TOO_OLD,
    @SerialName("server_too_old") SERVER_TOO_OLD,
    @SerialName("invalid_credentials") INVALID_CREDENTIALS,
    @SerialName("throttled") THROTTLED,
    @SerialName("unauthorized") UNAUTHORIZED,
    @SerialName("forbidden") FORBIDDEN,
    @SerialName("not_found") NOT_FOUND,
    @SerialName("no_household") NO_HOUSEHOLD,
    @SerialName("username_taken") USERNAME_TAKEN,
    @SerialName("invalid_invite") INVALID_INVITE,
    @SerialName("invalid_input") INVALID_INPUT,
    @SerialName("invalid_payload") INVALID_PAYLOAD,
    @SerialName("missing_reference") MISSING_REFERENCE,
    @SerialName("too_large") TOO_LARGE,
    @SerialName("cursor_expired") CURSOR_EXPIRED,
}

@Serializable
data class ErrorDto(val code: ErrorCode)

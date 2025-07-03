package dev.rubentxu.hodei.security.infrastructure.api

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

object AuthDto {
    @Serializable
    data class LoginRequestDto(
        val username: String,
        val password: String
    )

    @Serializable
    data class LoginResponseDto(
        val token: String,
        val user: UserDto,
        val expiresAt: Instant
    )

    @Serializable
    data class RegisterRequestDto(
        val username: String,
        val email: String,
        val password: String,
        val roles: List<String>? = null
    )

    @Serializable
    data class RegisterResponseDto(
        val user: UserDto,
        val message: String
    )

    @Serializable
    data class LogoutResponseDto(
        val message: String
    )

    @Serializable
    data class UserDto(
        val id: String,
        val username: String,
        val email: String,
        val roles: List<String>,
        val isActive: Boolean,
        val lastLoginAt: Instant?
    )
}
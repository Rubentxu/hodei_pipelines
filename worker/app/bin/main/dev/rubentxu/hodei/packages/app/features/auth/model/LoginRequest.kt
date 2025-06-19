package dev.rubentxu.hodei.packages.app.features.auth.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val usernameOrEmail: String,
    val password: String
)